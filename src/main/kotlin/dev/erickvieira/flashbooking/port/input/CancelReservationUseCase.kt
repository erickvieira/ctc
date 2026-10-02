package dev.erickvieira.flashbooking.port.input

import dev.erickvieira.flashbooking.domain.model.UserId
import java.util.UUID

interface CancelReservationUseCase {
	fun cancel(
		id: UUID,
		userId: UserId,
	)
}
