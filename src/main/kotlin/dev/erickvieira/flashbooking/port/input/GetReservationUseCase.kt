package dev.erickvieira.flashbooking.port.input

import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.UserId
import java.util.UUID

interface GetReservationUseCase {
	fun getByIdAndUserId(
		id: UUID,
		userId: UserId,
	): Reservation
}
