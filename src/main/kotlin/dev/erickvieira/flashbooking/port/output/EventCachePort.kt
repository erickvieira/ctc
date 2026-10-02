package dev.erickvieira.flashbooking.port.output

import dev.erickvieira.flashbooking.domain.model.Event
import java.util.UUID

interface EventCachePort {
	fun get(eventId: UUID): Event?

	fun put(
		eventId: UUID,
		event: Event,
	)

	fun evict(eventId: UUID)
}
