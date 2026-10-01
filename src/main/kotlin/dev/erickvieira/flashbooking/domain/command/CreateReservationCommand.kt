package dev.erickvieira.flashbooking.domain.command

import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.UserId
import java.util.UUID

data class CreateReservationCommand(
	val eventId: UUID,
	val userId: UserId,
	val quantity: Quantity,
) {
	companion object
}
