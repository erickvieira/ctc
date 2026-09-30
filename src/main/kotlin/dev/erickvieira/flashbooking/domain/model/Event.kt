package dev.erickvieira.flashbooking.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class Event(
	val id: UUID,
	val name: String,
	val startsAt: OffsetDateTime,
	val capacity: Capacity,
	val available: Int,
	val createdAt: OffsetDateTime,
	val updatedAt: OffsetDateTime,
) {
	companion object
}
