package dev.erickvieira.flashbooking.adapter.output.persistence.mapper

import dev.erickvieira.flashbooking.adapter.output.persistence.entity.EventEntity
import dev.erickvieira.flashbooking.domain.model.Event
import io.mcarle.konvert.api.Konverter

@Konverter
interface EventPersistenceMapper {
	fun toEntity(event: Event): EventEntity

	fun toDomain(entity: EventEntity): Event
}
