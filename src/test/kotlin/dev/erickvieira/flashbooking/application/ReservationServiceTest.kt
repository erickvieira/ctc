package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.config.ReservationProperties
import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.exception.EventSoldOutException
import dev.erickvieira.flashbooking.domain.exception.ReservationNotFoundException
import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.domain.model.EventAvailabilityChanged
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion
import dev.erickvieira.flashbooking.domain.model.ReservationStatus
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.output.EventPersistencePort
import dev.erickvieira.flashbooking.port.output.ReservationPersistencePort
import io.mockk.Runs
import io.mockk.checkUnnecessaryStub
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
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
	private val eventPersistencePort = mockk<EventPersistencePort>()
	private val reservationPersistencePort = mockk<ReservationPersistencePort>()
	private val publisher = mockk<ApplicationEventPublisher>()
	private val service =
		ReservationService(
			eventPersistencePort = eventPersistencePort,
			reservationPersistencePort = reservationPersistencePort,
			publisher = publisher,
			properties = properties,
			clock = clock,
		)

	@AfterEach
	fun enforceStrictVerification() {
		confirmVerified(eventPersistencePort, reservationPersistencePort, publisher)
		checkUnnecessaryStub(eventPersistencePort, reservationPersistencePort, publisher)
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
			every { eventPersistencePort.findById(id = event.id) } returns event
			every {
				reservationPersistencePort.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			} answers { ReservationInsertion.Created(reservation = firstArg()) }
			every { eventPersistencePort.tryReserve(eventId = event.id, quantity = command.quantity, now = fixedNow) } returns true
			every { publisher.publishEvent(any<EventAvailabilityChanged>()) } just Runs

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
			verify(exactly = 1) { eventPersistencePort.findById(id = event.id) }
			verify(exactly = 1) {
				reservationPersistencePort.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			}
			verify(exactly = 1) { eventPersistencePort.tryReserve(eventId = event.id, quantity = command.quantity, now = fixedNow) }
			verify(exactly = 1) { publisher.publishEvent(EventAvailabilityChanged(eventId = event.id)) }
		}

		@Test
		fun `replays the existing reservation without holding seats when the idempotency key is reused`() {
			val event = Event.fake()
			val command = CreateReservationCommand.fake(eventId = event.id, idempotencyKey = "key-1")
			val existing = Reservation.fake(eventId = event.id, userId = command.userId)
			every { eventPersistencePort.findById(id = event.id) } returns event
			every {
				reservationPersistencePort.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			} returns ReservationInsertion.Replayed(reservation = existing)

			val insertion = service.create(command = command)

			assertThat(insertion).usingRecursiveComparison().isEqualTo(ReservationInsertion.Replayed(reservation = existing))
			verify(exactly = 1) { eventPersistencePort.findById(id = event.id) }
			verify(exactly = 1) {
				reservationPersistencePort.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			}
			verify(exactly = 0) { eventPersistencePort.tryReserve(eventId = any(), quantity = any(), now = any()) }
			verify(exactly = 0) { publisher.publishEvent(any<EventAvailabilityChanged>()) }
		}

		@Test
		fun `fails when the event does not exist`() {
			val eventId = UUID.randomUUID()
			every { eventPersistencePort.findById(id = eventId) } returns null

			assertThrows<EventNotFoundException> { service.create(command = CreateReservationCommand.fake(eventId = eventId)) }

			verify(exactly = 1) { eventPersistencePort.findById(id = eventId) }
		}

		@Test
		fun `fails when there is no availability`() {
			val event = Event.fake(capacity = Capacity.of(value = 1))
			val command = CreateReservationCommand.fake(eventId = event.id)
			every { eventPersistencePort.findById(id = event.id) } returns event
			every {
				reservationPersistencePort.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			} returns ReservationInsertion.Created(reservation = Reservation.fake())
			every { eventPersistencePort.tryReserve(eventId = event.id, quantity = command.quantity, now = fixedNow) } returns false

			assertThrows<EventSoldOutException> { service.create(command = command) }

			verify(exactly = 1) { eventPersistencePort.findById(id = event.id) }
			verify(exactly = 1) {
				reservationPersistencePort.insertIdempotent(reservation = any(), idempotency = command.idempotency)
			}
			verify(exactly = 1) { eventPersistencePort.tryReserve(eventId = event.id, quantity = command.quantity, now = fixedNow) }
			verify(exactly = 0) { publisher.publishEvent(any<EventAvailabilityChanged>()) }
		}
	}

	@Nested
	@DisplayName("getById")
	inner class GetById {
		@Test
		fun `returns the reservation when it belongs to the user`() {
			val reservation = Reservation.fake()
			every { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId) } returns reservation

			assertThat(service.getByIdAndUserId(id = reservation.id, userId = reservation.userId))
				.usingRecursiveComparison()
				.isEqualTo(reservation)
			verify(exactly = 1) { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId) }
		}

		@Test
		fun `throws when the reservation belongs to another user`() {
			val reservation = Reservation.fake()
			val otherUserId = UserId.fake()
			every { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = otherUserId) } returns null

			assertThrows<ReservationNotFoundException> { service.getByIdAndUserId(id = reservation.id, userId = otherUserId) }

			verify(exactly = 1) { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = otherUserId) }
		}

		@Test
		fun `throws when the reservation does not exist`() {
			val id = UUID.randomUUID()
			val userId = UserId.fake()
			every { reservationPersistencePort.findByIdAndUserId(id = id, userId = userId) } returns null

			assertThrows<ReservationNotFoundException> { service.getByIdAndUserId(id = id, userId = userId) }

			verify(exactly = 1) { reservationPersistencePort.findByIdAndUserId(id = id, userId = userId) }
		}
	}

	@Nested
	@DisplayName("cancel")
	inner class Cancel {
		@Test
		fun `cancels a pending reservation and releases the seats`() {
			val reservation = Reservation.fake(status = ReservationStatus.PENDING)
			val cancelled = reservation.copy(status = ReservationStatus.CANCELLED)
			every { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId) } returns reservation
			every {
				reservationPersistencePort.cancelIfCancellable(id = reservation.id, userId = reservation.userId, now = fixedNow)
			} returns cancelled
			every { eventPersistencePort.release(eventId = cancelled.eventId, amount = cancelled.quantity.value, now = fixedNow) } just Runs
			every { publisher.publishEvent(any<EventAvailabilityChanged>()) } just Runs

			service.cancel(id = reservation.id, userId = reservation.userId)

			verify(exactly = 1) { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId) }
			verify(exactly = 1) { reservationPersistencePort.cancelIfCancellable(id = reservation.id, userId = reservation.userId, now = fixedNow) }
			verify(exactly = 1) { eventPersistencePort.release(eventId = cancelled.eventId, amount = cancelled.quantity.value, now = fixedNow) }
			verify(exactly = 1) { publisher.publishEvent(EventAvailabilityChanged(eventId = cancelled.eventId)) }
		}

		@Test
		fun `does not release seats again when the reservation is already terminal`() {
			val reservation = Reservation.fake(status = ReservationStatus.EXPIRED)
			every { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId) } returns reservation
			every {
				reservationPersistencePort.cancelIfCancellable(id = reservation.id, userId = reservation.userId, now = fixedNow)
			} returns null

			service.cancel(id = reservation.id, userId = reservation.userId)

			verify(exactly = 1) { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId) }
			verify(exactly = 1) { reservationPersistencePort.cancelIfCancellable(id = reservation.id, userId = reservation.userId, now = fixedNow) }
			verify(exactly = 0) { eventPersistencePort.release(eventId = any(), amount = any(), now = any()) }
			verify(exactly = 0) { publisher.publishEvent(any<EventAvailabilityChanged>()) }
		}

		@Test
		fun `throws when the reservation does not exist or belongs to another user`() {
			val id = UUID.randomUUID()
			val userId = UserId.fake()
			every { reservationPersistencePort.findByIdAndUserId(id = id, userId = userId) } returns null

			assertThrows<ReservationNotFoundException> { service.cancel(id = id, userId = userId) }

			verify(exactly = 1) { reservationPersistencePort.findByIdAndUserId(id = id, userId = userId) }
		}
	}

	@Nested
	@DisplayName("confirm")
	inner class Confirm {
		@Test
		fun `confirms a pending reservation`() {
			val reservation = Reservation.fake(status = ReservationStatus.PENDING)
			val confirmed = reservation.copy(status = ReservationStatus.CONFIRMED)
			every { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId) } returns reservation
			every {
				reservationPersistencePort.confirmIfPending(id = reservation.id, userId = reservation.userId, now = fixedNow)
			} returns confirmed

			service.confirm(id = reservation.id, userId = reservation.userId)

			verify(exactly = 1) { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId) }
			verify(exactly = 1) { reservationPersistencePort.confirmIfPending(id = reservation.id, userId = reservation.userId, now = fixedNow) }
			verify(exactly = 0) { publisher.publishEvent(any<EventAvailabilityChanged>()) }
		}

		@Test
		fun `does not transition again when the reservation is already confirmed`() {
			val reservation = Reservation.fake(status = ReservationStatus.CONFIRMED)
			every { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId) } returns reservation
			every {
				reservationPersistencePort.confirmIfPending(id = reservation.id, userId = reservation.userId, now = fixedNow)
			} returns null

			service.confirm(id = reservation.id, userId = reservation.userId)

			verify(exactly = 1) { reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId) }
			verify(exactly = 1) { reservationPersistencePort.confirmIfPending(id = reservation.id, userId = reservation.userId, now = fixedNow) }
		}

		@Test
		fun `throws when the reservation does not exist or belongs to another user`() {
			val id = UUID.randomUUID()
			val userId = UserId.fake()
			every { reservationPersistencePort.findByIdAndUserId(id = id, userId = userId) } returns null

			assertThrows<ReservationNotFoundException> { service.confirm(id = id, userId = userId) }

			verify(exactly = 1) { reservationPersistencePort.findByIdAndUserId(id = id, userId = userId) }
		}
	}
}
