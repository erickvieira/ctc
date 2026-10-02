package dev.erickvieira.flashbooking.adapter.output.cache.mapper

import dev.erickvieira.flashbooking.adapter.output.cache.dto.CachedEvent
import dev.erickvieira.flashbooking.domain.model.Event
import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping

@Konverter
interface CachedEventMapper {
	@Konvert(mappings = [Mapping(target = "capacity", expression = "it.capacity.value")])
	fun toCached(event: Event): CachedEvent

	@Konvert(
		mappings = [
			Mapping(
				target = "capacity",
				expression = "dev.erickvieira.flashbooking.domain.model.Capacity.of(value = it.capacity)",
			),
		],
	)
	fun toDomain(cached: CachedEvent): Event
}
