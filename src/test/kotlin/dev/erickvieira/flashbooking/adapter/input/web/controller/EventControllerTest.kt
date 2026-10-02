package dev.erickvieira.flashbooking.adapter.input.web.controller

import dev.erickvieira.flashbooking.adapter.input.web.mapper.EventApiMapperImpl
import dev.erickvieira.flashbooking.adapter.input.web.model.CreateEventRequest
import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.input.CreateEventUseCase
import dev.erickvieira.flashbooking.port.input.GetEventUseCase
import io.mockk.checkUnnecessaryStub
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.UUID

class EventControllerTest {
	private val createEventUseCase = mockk<CreateEventUseCase>()
	private val getEventUseCase = mockk<GetEventUseCase>()
	private val controller = EventController(createEventUseCase = createEventUseCase, getEventUseCase = getEventUseCase)

	@AfterEach
	fun enforceStrictVerification() {
		confirmVerified(createEventUseCase, getEventUseCase)
		checkUnnecessaryStub(createEventUseCase, getEventUseCase)
	}

	@Nested
	@DisplayName("createEvent")
	inner class CreateEvent {
		@Test
		fun `maps the request into a command and returns 201 with Location`() {
			val event = Event.fake(capacity = Capacity.of(value = 100))
			val startsAt = OffsetDateTime.parse("2026-10-15T20:00:00Z")
			every { createEventUseCase.create(command = any()) } returns event

			val response =
				controller.createEvent(
					xUserId = UUID.randomUUID(),
					createEventRequest = CreateEventRequest("Show", startsAt, 100),
				)

			assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
			assertThat(response.headers.location.toString()).isEqualTo("/events/${event.id}")
			assertThat(response.body).usingRecursiveComparison().isEqualTo(EventApiMapperImpl.toResponse(event = event))
			verify(exactly = 1) {
				createEventUseCase.create(
					command = match { it.name == "Show" && it.capacity == Capacity.of(value = 100) },
				)
			}
		}
	}

	@Nested
	@DisplayName("getEvent")
	inner class GetEvent {
		@Test
		fun `returns 200 with the mapped event`() {
			val event = Event.fake()
			every { getEventUseCase.getById(id = event.id) } returns event

			val response = controller.getEvent(xUserId = UUID.randomUUID(), id = event.id)

			assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
			assertThat(response.body).usingRecursiveComparison().isEqualTo(EventApiMapperImpl.toResponse(event = event))
			verify(exactly = 1) { getEventUseCase.getById(id = event.id) }
		}
	}
}
