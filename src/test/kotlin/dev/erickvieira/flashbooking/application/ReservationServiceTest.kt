package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.config.ReservationProperties
import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.exception.EventSoldOutException
import dev.erickvieira.flashbooking.domain.exception.ReservationNotFoundException
import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion
import dev.erickvieira.flashbooking.domain.model.ReservationStatus
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.output.EventRepository
import dev.erickvieira.flashbooking.port.output.ReservationRepository
import io.mockk.checkUnnecessaryStub
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ReservationServiceTest {
	private val fixedInstant = Instant.parse("2026-01-01T12:00:00Z")
	private val fixedNow = OffsetDateTime.ofInstant(fixedInstant, ZoneOffset.UTC)
	private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
	private val properties = ReservationProperties(ttl = Duration.ofMinutes(10), maxPerReservation = 10)
	private val eventRepository = mockk<EventRepository>()
	private val reservationRepository = mockk<ReservationRepository>()
	private val service = ReservationService(eventRepository = eventRepository, reservationRepository = reservationRepository, properties = properties, clock = clock)

	@AfterEach
	fun enforceStrictVerification() {
		confirmVerified(eventRepository, reservationRepository)
		checkUnnecessaryStub(eventRepository, reservationRepository)
	}

	@Nested
	@DisplayName("create")
	inner class Create {
		@Test
		fun `holds the seats and persists a pending reservation with the configured ttl`() {
			val event = Event.fake(capacity = Capacity.of(value = 100))
			val command =
				CreateReservationCommand.fake(
					eventId = event.id,
					quantity = Quantity.of(value = 2, max = 10),
				)
			every { eventRepository.findById(id = event.id) } returns event
			every {
				reservationRepository.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			} answers { ReservationInsertion.Created(reservation = firstArg()) }
			every { eventRepository.tryReserve(eventId = event.id, quantity = command.quantity, now = fixedNow) } returns true

			val insertion = service.create(command = command)

			val expected =
				Reservation.fake(
					eventId = event.id,
					userId = command.userId,
					quantity = command.quantity,
					status = ReservationStatus.PENDING,
					expiresAt = fixedNow.plus(Duration.ofMinutes(10)),
					createdAt = fixedNow,
					updatedAt = fixedNow,
				)
			assertThat(insertion).usingRecursiveComparison().ignoringFields("reservation.id").isEqualTo(ReservationInsertion.Created(reservation = expected))
			assertThat(insertion.reservation.id).isNotNull()
			verify(exactly = 1) { eventRepository.findById(id = event.id) }
			verify(exactly = 1) {
				reservationRepository.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			}
			verify(exactly = 1) { eventRepository.tryReserve(eventId = event.id, quantity = command.quantity, now = fixedNow) }
		}

		@Test
		fun `replays the existing reservation without holding seats when the idempotency key is reused`() {
			val event = Event.fake()
			val command = CreateReservationCommand.fake(eventId = event.id, idempotencyKey = "key-1")
			val existing = Reservation.fake(eventId = event.id, userId = command.userId)
			every { eventRepository.findById(id = event.id) } returns event
			every {
				reservationRepository.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			} returns ReservationInsertion.Replayed(reservation = existing)

			val insertion = service.create(command = command)

			assertThat(insertion).usingRecursiveComparison().isEqualTo(ReservationInsertion.Replayed(reservation = existing))
			verify(exactly = 1) { eventRepository.findById(id = event.id) }
			verify(exactly = 1) {
				reservationRepository.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			}
			verify(exactly = 0) { eventRepository.tryReserve(eventId = any(), quantity = any(), now = any()) }
		}

		@Test
		fun `fails when the event does not exist`() {
			val eventId = UUID.randomUUID()
			every { eventRepository.findById(id = eventId) } returns null

			assertThrows<EventNotFoundException> { service.create(command = CreateReservationCommand.fake(eventId = eventId)) }

			verify(exactly = 1) { eventRepository.findById(id = eventId) }
		}

		@Test
		fun `fails when there is no availability`() {
			val event = Event.fake(capacity = Capacity.of(value = 1))
			val command = CreateReservationCommand.fake(eventId = event.id)
			every { eventRepository.findById(id = event.id) } returns event
			every {
				reservationRepository.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			} returns ReservationInsertion.Created(reservation = Reservation.fake())
			every { eventRepository.tryReserve(eventId = event.id, quantity = command.quantity, now = fixedNow) } returns false

			assertThrows<EventSoldOutException> { service.create(command = command) }

			verify(exactly = 1) { eventRepository.findById(id = event.id) }
			verify(exactly = 1) {
				reservationRepository.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			}
			verify(exactly = 1) { eventRepository.tryReserve(eventId = event.id, quantity = command.quantity, now = fixedNow) }
		}
	}

	@Nested
	@DisplayName("getById")
	inner class GetById {
		@Test
		fun `returns the reservation when it belongs to the user`() {
			val reservation = Reservation.fake()
			every { reservationRepository.findByIdAndUserId(id = reservation.id, userId = reservation.userId) } returns reservation

			assertThat(service.getByIdAndUserId(id = reservation.id, userId = reservation.userId))
				.usingRecursiveComparison()
				.isEqualTo(reservation)
			verify(exactly = 1) { reservationRepository.findByIdAndUserId(id = reservation.id, userId = reservation.userId) }
		}

		@Test
		fun `throws when the reservation belongs to another user`() {
			val reservation = Reservation.fake()
			val otherUserId = UserId.fake()
			every { reservationRepository.findByIdAndUserId(id = reservation.id, userId = otherUserId) } returns null

			assertThrows<ReservationNotFoundException> { service.getByIdAndUserId(id = reservation.id, userId = otherUserId) }

			verify(exactly = 1) { reservationRepository.findByIdAndUserId(id = reservation.id, userId = otherUserId) }
		}

		@Test
		fun `throws when the reservation does not exist`() {
			val id = UUID.randomUUID()
			val userId = UserId.fake()
			every { reservationRepository.findByIdAndUserId(id = id, userId = userId) } returns null

			assertThrows<ReservationNotFoundException> { service.getByIdAndUserId(id = id, userId = userId) }

			verify(exactly = 1) { reservationRepository.findByIdAndUserId(id = id, userId = userId) }
		}
	}
}
