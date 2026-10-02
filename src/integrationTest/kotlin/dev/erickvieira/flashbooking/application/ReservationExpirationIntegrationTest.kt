package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationStatus
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.output.EventPersistencePort
import dev.erickvieira.flashbooking.port.output.ReservationPersistencePort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Testcontainers
class ReservationExpirationIntegrationTest {
	companion object {
		@Container
		@ServiceConnection
		@JvmStatic
		val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
	}

	@Autowired
	private lateinit var eventPersistencePort: EventPersistencePort

	@Autowired
	private lateinit var reservationPersistencePort: ReservationPersistencePort

	@Autowired
	private lateinit var expirationService: ReservationExpirationService

	@Autowired
	private lateinit var transactionTemplate: TransactionTemplate

	@Test
	fun `sweep expires the reservation and returns the seats`() {
		val event = eventPersistencePort.save(event = Event.fake(capacity = Capacity.of(value = 5)))
		val reservation = reserveExpired(eventId = event.id, quantity = Quantity.of(value = 2, max = 10))

		expirationService.sweep()

		val reloaded = reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId)
		assertThat(reloaded?.status).isEqualTo(ReservationStatus.EXPIRED)
		assertThat(eventPersistencePort.findById(id = event.id)?.available).isEqualTo(5)
	}

	@Test
	fun `two concurrent sweeps expire the reservation only once`() {
		val event = eventPersistencePort.save(event = Event.fake(capacity = Capacity.of(value = 5)))
		val reservation = reserveExpired(eventId = event.id, quantity = Quantity.of(value = 2, max = 10))

		val start = CountDownLatch(1)
		val executor = Executors.newFixedThreadPool(2)
		try {
			val first = CompletableFuture.runAsync({ start.await(); expirationService.sweep() }, executor)
			val second = CompletableFuture.runAsync({ start.await(); expirationService.sweep() }, executor)
			start.countDown()
			first.get(10, TimeUnit.SECONDS)
			second.get(10, TimeUnit.SECONDS)
		} finally {
			executor.shutdownNow()
		}

		val reloaded = reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId)
		assertThat(reloaded?.status).isEqualTo(ReservationStatus.EXPIRED)
		assertThat(eventPersistencePort.findById(id = event.id)?.available).isEqualTo(5)
	}

	@Test
	fun `sweep ignores a confirmed reservation even if its expiry has passed`() {
		val event = eventPersistencePort.save(event = Event.fake(capacity = Capacity.of(value = 5)))
		val reservation = reserveExpired(eventId = event.id, quantity = Quantity.of(value = 2, max = 10))
		transactionTemplate.executeWithoutResult {
			reservationPersistencePort.confirmIfPending(id = reservation.id, userId = reservation.userId, now = OffsetDateTime.now())
		}

		expirationService.sweep()

		val reloaded = reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId)
		assertThat(reloaded?.status).isEqualTo(ReservationStatus.CONFIRMED)
		assertThat(eventPersistencePort.findById(id = event.id)?.available).isEqualTo(3)
	}

	private fun reserveExpired(
		eventId: UUID,
		quantity: Quantity,
	): Reservation {
		val past = OffsetDateTime.now().minusMinutes(1)
		val reservation =
			Reservation.fake(
				eventId = eventId,
				quantity = quantity,
				status = ReservationStatus.PENDING,
				expiresAt = past,
			)
		transactionTemplate.executeWithoutResult {
			check(eventPersistencePort.tryReserve(eventId = eventId, quantity = quantity, now = past)) { "reserve failed" }
			reservationPersistencePort.insertIdempotent(reservation = reservation, idempotency = null)
		}
		return reservation
	}
}
