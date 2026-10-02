package dev.erickvieira.flashbooking.adapter.output.cache.dto

import java.time.OffsetDateTime
import java.util.UUID

data class CachedEvent(
	val id: UUID,
	val name: String,
	val startsAt: OffsetDateTime,
	val capacity: Int,
	val available: Int,
	val createdAt: OffsetDateTime,
	val updatedAt: OffsetDateTime,
)
