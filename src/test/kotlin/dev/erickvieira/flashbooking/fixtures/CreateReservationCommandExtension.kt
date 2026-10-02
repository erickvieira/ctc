package dev.erickvieira.flashbooking.fixtures

import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.UserId
import java.util.UUID

fun CreateReservationCommand.Companion.fake(
	eventId: UUID = UUID.randomUUID(),
	userId: UserId = UserId.fake(),
	quantity: Quantity = Quantity.fake(),
	idempotencyKey: String? = null,
): CreateReservationCommand =
	CreateReservationCommand(
		eventId = eventId,
		userId = userId,
		quantity = quantity,
		idempotencyKey = idempotencyKey,
	)
