package dev.erickvieira.flashbooking.adapter.input.web.controller

import dev.erickvieira.flashbooking.adapter.input.web.mapper.EventApiMapperImpl
import dev.erickvieira.flashbooking.adapter.input.web.model.CreateEventRequest
import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.input.CreateEventUseCase
import dev.erickvieira.flashbooking.port.input.GetEventUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.UUID

class EventControllerTest {
	private val createEventUseCase = mockk<CreateEventUseCase>()
	private val getEventUseCase = mockk<GetEventUseCase>()
	private val controller = EventController(createEventUseCase, getEventUseCase)

	@Test
	fun `createEvent maps the request into a command and returns 201 with Location`() {
		val event = Event.fake(capacity = Capacity.of(100))
		val startsAt = OffsetDateTime.parse("2026-10-15T20:00:00Z")
		every { createEventUseCase.create(any()) } returns event

		val response = controller.createEvent(UUID.randomUUID(), CreateEventRequest("Show", startsAt, 100))

		assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
		assertThat(response.headers.location.toString()).isEqualTo("/events/${event.id}")
		assertThat(response.body).usingRecursiveComparison().isEqualTo(EventApiMapperImpl.toResponse(event))
		verify(exactly = 1) {
			createEventUseCase.create(match { it.name == "Show" && it.capacity == Capacity.of(100) })
		}
	}

	@Test
	fun `getEvent returns 200 with the mapped event`() {
		val event = Event.fake()
		every { getEventUseCase.getById(event.id) } returns event

		val response = controller.getEvent(UUID.randomUUID(), event.id)

		assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
		assertThat(response.body).usingRecursiveComparison().isEqualTo(EventApiMapperImpl.toResponse(event))
	}
}
