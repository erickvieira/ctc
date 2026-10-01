package dev.erickvieira.flashbooking.adapter.output.persistence

import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.output.EventRepository
import dev.erickvieira.flashbooking.port.output.ReservationRepository
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
class ReservationPersistenceAdapterTest {
	companion object {
		@Container
		@ServiceConnection
		@JvmStatic
		val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
	}

	@Autowired
	private lateinit var eventRepository: EventRepository

	@Autowired
	private lateinit var reservationRepository: ReservationRepository

	@Test
	fun `save and findById round-trip the reservation`() {
		val event = eventRepository.save(Event.fake(capacity = Capacity.of(10)))
		val reservation = Reservation.fake(eventId = event.id, quantity = Quantity.of(value = 2, max = 10))

		val saved = reservationRepository.save(reservation)

		assertThat(saved).usingRecursiveComparison().isEqualTo(reservation)
		assertThat(reservationRepository.findByIdAndUserId(reservation.id, reservation.userId))
			.usingRecursiveComparison()
			.isEqualTo(reservation)
	}
}
