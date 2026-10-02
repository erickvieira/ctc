package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.domain.command.CreateEventCommand
import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.port.input.CreateEventUseCase
import dev.erickvieira.flashbooking.port.input.GetEventUseCase
import dev.erickvieira.flashbooking.port.output.EventPersistencePort
import dev.erickvieira.flashbooking.port.output.EventCachePort
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class EventService(
	private val eventPersistencePort: EventPersistencePort,
	private val eventCache: EventCachePort,
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
		return eventPersistencePort.save(event = event)
	}

	/**
	 * Cache-aside: cache hit -> devolve; miss -> lê o DB, popula o cache e devolve.
	 *
	 * @param id id do evento.
	 * @return o evento (possivelmente eventualmente consistente).
	 * @throws EventNotFoundException evento inexistente.
	 */
	override fun getById(id: UUID): Event = eventCache.get(eventId = id) ?: loadAndCache(id = id)

	private fun loadAndCache(id: UUID): Event {
		val event = eventPersistencePort.findById(id = id) ?: throw EventNotFoundException(id = id)
		eventCache.put(eventId = id, event = event)
		return event
	}
}
