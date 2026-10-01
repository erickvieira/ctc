package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.domain.command.CreateEventCommand
import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.port.input.CreateEventUseCase
import dev.erickvieira.flashbooking.port.input.GetEventUseCase
import dev.erickvieira.flashbooking.port.output.EventRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class EventService(
	private val eventRepository: EventRepository,
	private val clock: Clock,
) : CreateEventUseCase,
	GetEventUseCase {
	override fun create(command: CreateEventCommand): Event {
		val now = OffsetDateTime.now(clock)
		val event =
			Event(
				id = UUID.randomUUID(),
				name = command.name,
				startsAt = command.startsAt,
				capacity = command.capacity,
				available = command.capacity.value,
				createdAt = now,
				updatedAt = now,
			)
		return eventRepository.save(event = event)
	}

	override fun getById(id: UUID): Event = eventRepository.findById(id = id) ?: throw EventNotFoundException(id = id)
}
