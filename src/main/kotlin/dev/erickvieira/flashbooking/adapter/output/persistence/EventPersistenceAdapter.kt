package dev.erickvieira.flashbooking.adapter.output.persistence

import dev.erickvieira.flashbooking.adapter.output.persistence.mapper.EventPersistenceMapper
import dev.erickvieira.flashbooking.adapter.output.persistence.repository.EventJpaRepository
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.port.output.EventRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class EventPersistenceAdapter(
	private val jpaRepository: EventJpaRepository,
	private val mapper: EventPersistenceMapper,
) : EventRepository {
	override fun save(event: Event): Event = mapper.toDomain(jpaRepository.save(mapper.toEntity(event)))

	override fun findById(id: UUID): Event? = jpaRepository.findById(id).map { mapper.toDomain(it) }.orElse(null)
}
