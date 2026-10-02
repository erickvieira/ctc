package dev.erickvieira.flashbooking.port.output

import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.domain.model.Quantity
import java.time.OffsetDateTime
import java.util.UUID

interface EventPersistencePort {
	fun save(event: Event): Event

	fun findById(id: UUID): Event?

	fun tryReserve(
		eventId: UUID,
		quantity: Quantity,
		now: OffsetDateTime,
	): Boolean

	fun release(
		eventId: UUID,
		amount: Int,
		now: OffsetDateTime,
	)
}
