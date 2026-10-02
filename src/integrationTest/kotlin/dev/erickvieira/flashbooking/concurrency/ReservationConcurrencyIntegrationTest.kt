package dev.erickvieira.flashbooking.concurrency

import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.exception.EventSoldOutException
import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.input.CreateReservationUseCase
import dev.erickvieira.flashbooking.port.output.EventPersistencePort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@Testcontainers
class ReservationConcurrencyIntegrationTest {
	companion object {
		@Container
		@ServiceConnection
		@JvmStatic
		val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
	}

	@Autowired
	private lateinit var eventPersistencePort: EventPersistencePort

	@Autowired
	private lateinit var createReservationUseCase: CreateReservationUseCase

	@Test
	fun `never oversells under concurrent reservations`() {
		val capacity = 50
		val attempts = 100
		val event = eventPersistencePort.save(event = Event.fake(capacity = Capacity.of(value = capacity)))

		val start = CountDownLatch(1)
		val successes = AtomicInteger()
		val soldOut = AtomicInteger()
		val executor = Executors.newFixedThreadPool(16)

		val futures =
			(1..attempts).map {
				executor.submit(
					Callable {
						start.await()
						try {
							createReservationUseCase.create(
								command =
									CreateReservationCommand.fake(
										eventId = event.id,
										quantity = Quantity.of(value = 1, max = 10),
									),
							)
							successes.incrementAndGet()
						} catch (_: EventSoldOutException) {
							soldOut.incrementAndGet()
						}
					},
				)
			}

		start.countDown()
		futures.forEach { it.get() }
		executor.shutdown()

		assertThat(successes.get()).isEqualTo(capacity)
		assertThat(soldOut.get()).isEqualTo(attempts - capacity)
		assertThat(eventPersistencePort.findById(id = event.id)!!.available).isZero()
	}
}
