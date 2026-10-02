package dev.erickvieira.flashbooking.adapter.input.web.mapper

import dev.erickvieira.flashbooking.adapter.input.web.model.CreateEventRequest
import dev.erickvieira.flashbooking.adapter.input.web.model.Event as EventResponse
import dev.erickvieira.flashbooking.domain.command.CreateEventCommand
import dev.erickvieira.flashbooking.domain.model.Event
import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping

@Konverter
interface EventApiMapper {
	@Konvert(mappings = [Mapping(target = "capacity", expression = "it.capacity.value")])
	fun toResponse(event: Event): EventResponse

	@Konvert(
		mappings = [
			Mapping(
				target = "capacity",
				expression = "dev.erickvieira.flashbooking.domain.model.Capacity.of(value = it.capacity)",
			),
		],
	)
	fun toCommand(request: CreateEventRequest): CreateEventCommand
}
