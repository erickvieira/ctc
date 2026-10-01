package dev.erickvieira.flashbooking.adapter.input.web.controller

import dev.erickvieira.flashbooking.adapter.input.web.api.EventsApi
import dev.erickvieira.flashbooking.adapter.input.web.mapper.EventApiMapperImpl
import dev.erickvieira.flashbooking.adapter.input.web.model.CreateEventRequest
import dev.erickvieira.flashbooking.adapter.input.web.model.Event as EventResponse
import dev.erickvieira.flashbooking.port.input.CreateEventUseCase
import dev.erickvieira.flashbooking.port.input.GetEventUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class EventController(
    private val createEventUseCase: CreateEventUseCase,
    private val getEventUseCase: GetEventUseCase,
) : EventsApi {
    override fun createEvent(
        xUserId: UUID,
        createEventRequest: CreateEventRequest,
    ): ResponseEntity<EventResponse> {
        val created = createEventUseCase.create(command = EventApiMapperImpl.toCommand(createEventRequest))
        return ResponseEntity
            .created(URI.create("/events/${created.id}"))
            .body(EventApiMapperImpl.toResponse(created))
    }

    override fun getEvent(
        xUserId: UUID,
        id: UUID,
    ): ResponseEntity<EventResponse> =
        getEventUseCase.getById(id)
            .let { EventApiMapperImpl.toResponse(it) }
            .let { ResponseEntity.ok(it) }
}
