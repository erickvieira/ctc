package dev.erickvieira.flashbooking.port.input

import dev.erickvieira.flashbooking.domain.model.UserId
import java.util.UUID

interface ConfirmReservationUseCase {
	fun confirm(
		id: UUID,
		userId: UserId,
	)
}
