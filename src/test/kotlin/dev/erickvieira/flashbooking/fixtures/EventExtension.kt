package dev.erickvieira.flashbooking.fixtures

import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import java.time.OffsetDateTime
import java.util.UUID

fun Event.Companion.fake(
	id: UUID = UUID.randomUUID(),
	name: String = "Event",
	startsAt: OffsetDateTime = defaultOffsetDateTime,
	capacity: Capacity = Capacity.of(value = 10),
	available: Int = capacity.value,
	createdAt: OffsetDateTime = defaultOffsetDateTime,
	updatedAt: OffsetDateTime = defaultOffsetDateTime,
): Event =
	Event(
		id = id,
		name = name,
		startsAt = startsAt,
		capacity = capacity,
		available = available,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)
