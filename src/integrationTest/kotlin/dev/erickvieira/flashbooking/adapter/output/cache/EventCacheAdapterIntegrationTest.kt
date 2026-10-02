package dev.erickvieira.flashbooking.adapter.output.cache

import dev.erickvieira.flashbooking.domain.command.CreateEventCommand
import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.input.CreateEventUseCase
import dev.erickvieira.flashbooking.port.input.CreateReservationUseCase
import dev.erickvieira.flashbooking.port.output.EventCachePort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@Testcontainers
class EventCacheAdapterIntegrationTest {
	companion object {
		@Container
		@ServiceConnection
		@JvmStatic
		val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16"))

		@Container
		@JvmStatic
		val redis = GenericContainer(DockerImageName.parse("redis:7")).withExposedPorts(6379)

		@JvmStatic
		@DynamicPropertySource
		fun redisProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.data.redis.host", redis::getHost)
			registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
		}
	}

	@Autowired
	private lateinit var eventCache: EventCachePort

	@Autowired
	private lateinit var createEventUseCase: CreateEventUseCase

	@Autowired
	private lateinit var createReservationUseCase: CreateReservationUseCase

	@Test
	fun `put then get round-trips the event through Redis`() {
		val event = createEventUseCase.create(command = CreateEventCommand.fake(capacity = Capacity.of(value = 10)))

		eventCache.put(eventId = event.id, event = event)

		assertThat(eventCache.get(eventId = event.id)).usingRecursiveComparison().isEqualTo(event)
	}

	@Test
	fun `evict removes the cached event`() {
		val event = createEventUseCase.create(command = CreateEventCommand.fake(capacity = Capacity.of(value = 10)))
		eventCache.put(eventId = event.id, event = event)

		eventCache.evict(eventId = event.id)

		assertThat(eventCache.get(eventId = event.id)).isNull()
	}

	@Test
	fun `a reservation evicts the cached event after commit`() {
		val event = createEventUseCase.create(command = CreateEventCommand.fake(capacity = Capacity.of(value = 10)))
		eventCache.put(eventId = event.id, event = event)
		assertThat(eventCache.get(eventId = event.id)).isNotNull()

		createReservationUseCase.create(
			command = CreateReservationCommand.fake(eventId = event.id, quantity = Quantity.of(value = 1, max = 10)),
		)

		assertThat(eventCache.get(eventId = event.id)).isNull()
	}
}
