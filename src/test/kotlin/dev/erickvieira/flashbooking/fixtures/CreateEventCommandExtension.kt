package dev.erickvieira.flashbooking.fixtures

import dev.erickvieira.flashbooking.domain.command.CreateEventCommand
import dev.erickvieira.flashbooking.domain.model.Capacity
import java.time.OffsetDateTime

fun CreateEventCommand.Companion.fake(
	name: String = "Event",
	startsAt: OffsetDateTime = defaultOffsetDateTime,
	capacity: Capacity = Capacity.of(value = 10),
): CreateEventCommand =
	CreateEventCommand(
		name = name,
		startsAt = startsAt,
		capacity = capacity,
	)
