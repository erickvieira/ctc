package dev.erickvieira.flashbooking.adapter.output.cache.listener

import dev.erickvieira.flashbooking.domain.model.EventAvailabilityChanged
import dev.erickvieira.flashbooking.port.output.EventCachePort
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class EventCacheEvictionListener(
	private val eventCache: EventCachePort,
) {
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	fun onAvailabilityChanged(event: EventAvailabilityChanged) {
		eventCache.evict(eventId = event.eventId)
	}
}
