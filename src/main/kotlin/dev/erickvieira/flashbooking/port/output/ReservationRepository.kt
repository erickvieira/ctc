package dev.erickvieira.flashbooking.port.output

import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationIdempotency
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion
import dev.erickvieira.flashbooking.domain.model.UserId
import java.time.OffsetDateTime
import java.util.UUID

interface ReservationRepository {
	fun insertIdempotent(
		reservation: Reservation,
		idempotency: ReservationIdempotency?,
	): ReservationInsertion

	fun findByIdAndUserId(id: UUID, userId: UserId): Reservation?

	fun cancelIfPending(
		id: UUID,
		userId: UserId,
		now: OffsetDateTime,
	): Reservation?

	fun expirePending(
		now: OffsetDateTime,
		limit: Int,
	): List<Reservation>
}
