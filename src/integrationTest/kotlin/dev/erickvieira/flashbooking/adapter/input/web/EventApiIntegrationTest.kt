package dev.erickvieira.flashbooking.adapter.input.web

import dev.erickvieira.flashbooking.adapter.input.web.filter.UserIdFilter
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
class EventApiIntegrationTest {
	companion object {
		@Container
		@ServiceConnection
		@JvmStatic
		val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
	}

	@Autowired
	private lateinit var mockMvc: MockMvc

	private val userId = "11111111-1111-1111-1111-111111111111"

	@Test
	fun `POST creates an event and GET reads it back`() {
		val location =
			mockMvc
				.perform(
					post("/events")
						.header(UserIdFilter.USER_ID_HEADER, userId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"name":"Show da Banda X","startsAt":"2026-10-15T20:00:00Z","capacity":100}"""),
				)
				.andExpect(status().isCreated)
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.name").value("Show da Banda X"))
				.andExpect(jsonPath("$.capacity").value(100))
				.andExpect(jsonPath("$.available").value(100))
				.andReturn()
				.response
				.getHeader("Location")!!

		mockMvc
			.perform(get(location).header(UserIdFilter.USER_ID_HEADER, userId))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.name").value("Show da Banda X"))
			.andExpect(jsonPath("$.available").value(100))
	}

	@Test
	fun `POST with capacity below one is rejected as 400`() {
		mockMvc
			.perform(
				post("/events")
					.header(UserIdFilter.USER_ID_HEADER, userId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"name":"Show","startsAt":"2026-10-15T20:00:00Z","capacity":0}"""),
			)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("INVALID_CAPACITY"))
	}

	@Test
	fun `POST without the user header is rejected as 400`() {
		mockMvc
			.perform(
				post("/events")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"name":"Show","startsAt":"2026-10-15T20:00:00Z","capacity":10}"""),
			)
			.andExpect(status().isBadRequest)
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
	}

	@Test
	fun `GET an unknown event is 404`() {
		mockMvc
			.perform(get("/events/${UUID.randomUUID()}").header(UserIdFilter.USER_ID_HEADER, userId))
			.andExpect(status().isNotFound)
			.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
	}
}
