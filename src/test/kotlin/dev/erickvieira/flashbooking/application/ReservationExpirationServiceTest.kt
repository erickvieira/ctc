package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.config.ExpirationProperties
import dev.erickvieira.flashbooking.domain.model.EventAvailabilityChanged
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationStatus
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
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
	private val reservationPersistencePort = mockk<ReservationPersistencePort>()
	private val eventPersistencePort = mockk<EventPersistencePort>()
	private val publisher = mockk<ApplicationEventPublisher>()
	private val service =
		ReservationExpirationService(
			reservationPersistencePort = reservationPersistencePort,
			eventPersistencePort = eventPersistencePort,
			publisher = publisher,
			properties = properties,
			clock = clock,
		)

	@AfterEach
	fun enforceStrictVerification() {
		confirmVerified(reservationPersistencePort, eventPersistencePort, publisher)
		checkUnnecessaryStub(reservationPersistencePort, eventPersistencePort, publisher)
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
			every { reservationPersistencePort.expirePending(now = fixedNow, limit = 3) } returns listOf(first, second, other)
			every { eventPersistencePort.release(eventId = eventId, amount = 5, now = fixedNow) } just Runs
			every { eventPersistencePort.release(eventId = other.eventId, amount = 1, now = fixedNow) } just Runs
			every { publisher.publishEvent(any<EventAvailabilityChanged>()) } just Runs

			service.sweep()

			verify(exactly = 1) { reservationPersistencePort.expirePending(now = fixedNow, limit = 3) }
			verify(exactly = 1) { eventPersistencePort.release(eventId = eventId, amount = 5, now = fixedNow) }
			verify(exactly = 1) { eventPersistencePort.release(eventId = other.eventId, amount = 1, now = fixedNow) }
			verify(exactly = 1) { publisher.publishEvent(EventAvailabilityChanged(eventId = eventId)) }
			verify(exactly = 1) { publisher.publishEvent(EventAvailabilityChanged(eventId = other.eventId)) }
		}

		@Test
		fun `does nothing when there is nothing to expire`() {
			every { reservationPersistencePort.expirePending(now = fixedNow, limit = 3) } returns emptyList()

			service.sweep()

			verify(exactly = 1) { reservationPersistencePort.expirePending(now = fixedNow, limit = 3) }
			verify(exactly = 0) { eventPersistencePort.release(eventId = any(), amount = any(), now = any()) }
			verify(exactly = 0) { publisher.publishEvent(any<EventAvailabilityChanged>()) }
		}
	}
}
