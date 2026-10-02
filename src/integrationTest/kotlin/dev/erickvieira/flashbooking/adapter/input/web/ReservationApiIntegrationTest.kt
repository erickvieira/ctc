package dev.erickvieira.flashbooking.adapter.input.web

import dev.erickvieira.flashbooking.adapter.input.web.filter.UserIdFilter
import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.output.EventRepository
import org.assertj.core.api.Assertions.assertThat
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

	@Test
	fun `POST with the same idempotency key replays the reservation as 200`() {
		val eventId = createEvent(capacity = 10)
		val key = UUID.randomUUID().toString()
		val body = """{"quantity":2}"""

		val firstId =
			mockMvc
				.perform(
					post("/events/$eventId/reservations")
						.header(UserIdFilter.USER_ID_HEADER, userId)
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body),
				)
				.andExpect(status().isCreated)
				.andReturn()
				.response
				.getHeader("Location")!!
				.substringAfterLast("/")

		mockMvc
			.perform(
				post("/events/$eventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body),
			)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.id").value(firstId))
	}

	@Test
	fun `POST reusing the idempotency key with a different payload is 409`() {
		val eventId = createEvent(capacity = 10)
		val key = UUID.randomUUID().toString()

		mockMvc
			.perform(
				post("/events/$eventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":2}"""),
			)
			.andExpect(status().isCreated)

		mockMvc
			.perform(
				post("/events/$eventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":3}"""),
			)
			.andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
	}

	@Test
	fun `the same idempotency key is scoped per user`() {
		val eventId = createEvent(capacity = 10)
		val key = UUID.randomUUID().toString()

		mockMvc
			.perform(
				post("/events/$eventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":2}"""),
			)
			.andExpect(status().isCreated)

		mockMvc
			.perform(
				post("/events/$eventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, otherUserId)
					.header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":2}"""),
			)
			.andExpect(status().isCreated)
	}

	@Test
	fun `POST without an idempotency key always creates a new reservation`() {
		val eventId = createEvent(capacity = 10)

		val first = reserve(eventId, userId, "1")
		val second = reserve(eventId, userId, "1")

		assertThat(first).isNotEqualTo(second)
	}

	@Test
	fun `reusing the same key for a different event is 409`() {
		val firstEventId = createEvent(capacity = 10)
		val secondEventId = createEvent(capacity = 10)
		val key = UUID.randomUUID().toString()

		mockMvc
			.perform(
				post("/events/$firstEventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":2}"""),
			)
			.andExpect(status().isCreated)

		mockMvc
			.perform(
				post("/events/$secondEventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":2}"""),
			)
			.andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
	}

	@Test
	fun `a sold-out attempt with an idempotency key is not replayed`() {
		val eventId = createEvent(capacity = 1)
		reserve(eventId, userId, "1")
		val key = UUID.randomUUID().toString()

		mockMvc
			.perform(
				post("/events/$eventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":1}"""),
			)
			.andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("EVENT_SOLD_OUT"))

		mockMvc
			.perform(
				post("/events/$eventId/reservations")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.header("Idempotency-Key", key)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"quantity":1}"""),
			)
			.andExpect(status().isConflict)
			.andExpect(jsonPath("$.code").value("EVENT_SOLD_OUT"))
	}

	private fun createEvent(capacity: Int): UUID =
		eventRepository.save(event = Event.fake(capacity = Capacity.of(value = capacity))).id

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
