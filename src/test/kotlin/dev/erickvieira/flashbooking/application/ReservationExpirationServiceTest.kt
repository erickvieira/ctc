package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.config.ExpirationProperties
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationStatus
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.output.EventRepository
import dev.erickvieira.flashbooking.port.output.ReservationRepository
import io.mockk.Runs
import io.mockk.checkUnnecessaryStub
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ReservationExpirationServiceTest {
	private val fixedInstant = Instant.parse("2026-01-01T12:00:00Z")
	private val fixedNow = OffsetDateTime.ofInstant(fixedInstant, ZoneOffset.UTC)
	private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
	private val properties = ExpirationProperties(batchSize = 3)
	private val reservationRepository = mockk<ReservationRepository>()
	private val eventRepository = mockk<EventRepository>()
	private val service =
		ReservationExpirationService(
			reservationRepository = reservationRepository,
			eventRepository = eventRepository,
			properties = properties,
			clock = clock,
		)

	@AfterEach
	fun enforceStrictVerification() {
		confirmVerified(reservationRepository, eventRepository)
		checkUnnecessaryStub(reservationRepository, eventRepository)
	}

	@Nested
	@DisplayName("sweep")
	inner class Sweep {
		@Test
		fun `expires the batch and releases seats aggregated per event`() {
			val eventId = UUID.randomUUID()
			val first = Reservation.fake(eventId = eventId, quantity = Quantity.of(value = 2, max = 10), status = ReservationStatus.EXPIRED)
			val second = Reservation.fake(eventId = eventId, quantity = Quantity.of(value = 3, max = 10), status = ReservationStatus.EXPIRED)
			val other = Reservation.fake(eventId = UUID.randomUUID(), quantity = Quantity.of(value = 1, max = 10), status = ReservationStatus.EXPIRED)
			every { reservationRepository.expirePending(now = fixedNow, limit = 3) } returns listOf(first, second, other)
			every { eventRepository.release(eventId = eventId, amount = 5, now = fixedNow) } just Runs
			every { eventRepository.release(eventId = other.eventId, amount = 1, now = fixedNow) } just Runs

			service.sweep()

			verify(exactly = 1) { reservationRepository.expirePending(now = fixedNow, limit = 3) }
			verify(exactly = 1) { eventRepository.release(eventId = eventId, amount = 5, now = fixedNow) }
			verify(exactly = 1) { eventRepository.release(eventId = other.eventId, amount = 1, now = fixedNow) }
		}

		@Test
		fun `does nothing when there is nothing to expire`() {
			every { reservationRepository.expirePending(now = fixedNow, limit = 3) } returns emptyList()

			service.sweep()

			verify(exactly = 1) { reservationRepository.expirePending(now = fixedNow, limit = 3) }
			verify(exactly = 0) { eventRepository.release(eventId = any(), amount = any(), now = any()) }
		}
	}
}
