package dev.erickvieira.flashbooking.adapter.output.persistence

import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.output.EventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@Testcontainers
class EventPersistenceAdapterTest {
	companion object {
		@Container
		@ServiceConnection
		@JvmStatic
		val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
	}

	@Autowired
	private lateinit var repository: EventRepository

	@Test
	fun `save and findById round-trip the event`() {
		val event = Event.fake(name = "Show da Banda X", capacity = Capacity.of(100))

		val saved = repository.save(event)

		assertThat(saved).usingRecursiveComparison().isEqualTo(event)
		assertThat(repository.findById(event.id)).usingRecursiveComparison().isEqualTo(event)
	}
}
