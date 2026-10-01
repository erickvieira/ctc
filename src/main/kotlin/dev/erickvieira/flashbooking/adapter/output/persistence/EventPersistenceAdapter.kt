package dev.erickvieira.flashbooking.adapter.output.persistence

import dev.erickvieira.flashbooking.adapter.output.persistence.mapper.EventPersistenceMapperImpl
import dev.erickvieira.flashbooking.adapter.output.persistence.repository.EventJpaRepository
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.port.output.EventRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class EventPersistenceAdapter(
	private val jpaRepository: EventJpaRepository,
) : EventRepository {
	override fun save(event: Event): Event =
		EventPersistenceMapperImpl.toDomain(jpaRepository.save(EventPersistenceMapperImpl.toEntity(event)))

	override fun findById(id: UUID): Event? =
		jpaRepository.findById(id).map { EventPersistenceMapperImpl.toDomain(it) }.orElse(null)

	override fun tryReserve(
		eventId: UUID,
		quantity: Quantity,
		now: OffsetDateTime,
	): Boolean = jpaRepository.tryReserve(id = eventId, quantity = quantity.value, now = now) > 0
}
