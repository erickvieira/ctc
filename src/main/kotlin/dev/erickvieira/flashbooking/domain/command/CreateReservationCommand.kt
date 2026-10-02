package dev.erickvieira.flashbooking.domain.command

import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.ReservationIdempotency
import dev.erickvieira.flashbooking.domain.model.UserId
import java.util.UUID

data class CreateReservationCommand(
	val eventId: UUID,
	val userId: UserId,
	val quantity: Quantity,
	val idempotencyKey: String?,
) {
	val idempotency: ReservationIdempotency? =
		if (idempotencyKey == null) {
			null
		} else {
			ReservationIdempotency(
				key = idempotencyKey,
				fingerprint = UUID.nameUUIDFromBytes("flashbooking|${userId.value}|$eventId|${quantity.value}".encodeToByteArray()),
			)
		}

	companion object
}
