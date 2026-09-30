package dev.erickvieira.flashbooking.domain.command

import dev.erickvieira.flashbooking.domain.model.Capacity
import java.time.OffsetDateTime

data class CreateEventCommand(
	val name: String,
	val startsAt: OffsetDateTime,
	val capacity: Capacity,
) {
	companion object
}
