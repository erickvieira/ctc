package dev.erickvieira.flashbooking.port.output

import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.UserId
import java.util.UUID

interface ReservationRepository {
	fun save(reservation: Reservation): Reservation

	fun findByIdAndUserId(id: UUID, userId: UserId): Reservation?
}
