package dev.erickvieira.flashbooking.domain.model

import java.util.UUID

data class EventAvailabilityChanged(val eventId: UUID) {
	companion object
}
