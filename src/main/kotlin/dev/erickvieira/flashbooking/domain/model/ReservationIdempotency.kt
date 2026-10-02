package dev.erickvieira.flashbooking.domain.model

import java.util.UUID

data class ReservationIdempotency(
	val key: String,
	val fingerprint: UUID,
) {
	companion object
}
