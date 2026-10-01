package dev.erickvieira.flashbooking.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class Reservation(
	val id: UUID,
	val eventId: UUID,
	val userId: UserId,
	val quantity: Quantity,
	val status: ReservationStatus,
	val expiresAt: OffsetDateTime,
	val createdAt: OffsetDateTime,
	val updatedAt: OffsetDateTime,
) {
	companion object
}
