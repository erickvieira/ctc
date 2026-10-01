package dev.erickvieira.flashbooking.adapter.input.web

import dev.erickvieira.flashbooking.adapter.input.web.filter.UserIdFilter
import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.output.EventRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReservationApiIntegrationTest {
	companion object {
		@Container
		@ServiceConnection
		@JvmStatic
		val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
	}

	@Autowired
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var eventRepository: EventRepository

	private val userId = "11111111-1111-1111-1111-111111111111"
	private val otherUserId = "22222222-2222-2222-2222-222222222222"

	@Test
	fun `POST reserves and GET reads it back`() {
		val eventId = createEvent(capacity = 10)

		val location =
			mockMvc
				.perform(
					post("/events/$eventId/reservations")
						.header(UserIdFilter.USER_ID_HEADER, userId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"quantity":2}"""),
				)
				.andExpect(status().isCreated)
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.eventId").value(eventId.toString()))
				.andExpect(jsonPath("$.quantity").value(2))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn()
				.response
				.getHeader("Location")!!

		mockMvc
			.perform(get(location).header(UserIdFilter.USER_ID_HEADER, userId))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.eventId").value(eventId.toString()))
	}

	@Test
	fun `POST without availability is rejected as 409`() {
		val eventId = createEvent(capacity = 1)
		reserve(eventId, userId, "1")

		mockMvc
			.perform(
				post("/events/$eventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":1}"""),
			)
			.andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("EVENT_SOLD_OUT"))
	}

	@Test
	fun `POST quantity above the maximum is rejected as 400`() {
		val eventId = createEvent(capacity = 10)

		mockMvc
			.perform(
				post("/events/$eventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":11}"""),
			)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("INVALID_QUANTITY"))
	}

	@Test
	fun `GET a reservation from another user is 404`() {
		val eventId = createEvent(capacity = 10)
		val location = reserve(eventId, userId, "1")

		mockMvc
			.perform(get(location).header(UserIdFilter.USER_ID_HEADER, otherUserId))
			.andExpect(status().isNotFound)
			.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"))
	}

	private fun createEvent(capacity: Int): UUID =
		eventRepository.save(Event.fake(capacity = Capacity.of(capacity))).id

	private fun reserve(
		eventId: UUID,
		header: String,
		quantity: String,
	): String =
		mockMvc
			.perform(
				post("/events/$eventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, header)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":$quantity}"""),
			)
			.andExpect(status().isCreated)
			.andReturn()
			.response
			.getHeader("Location")!!
}
