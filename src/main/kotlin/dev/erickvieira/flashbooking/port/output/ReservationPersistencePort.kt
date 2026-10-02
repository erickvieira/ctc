package dev.erickvieira.flashbooking.port.output

import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationIdempotency
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion
import dev.erickvieira.flashbooking.domain.model.UserId
import java.time.OffsetDateTime
import java.util.UUID

interface ReservationPersistencePort {
	fun insertIdempotent(
		reservation: Reservation,
		idempotency: ReservationIdempotency?,
	): ReservationInsertion

	fun findByIdAndUserId(id: UUID, userId: UserId): Reservation?

	fun confirmIfPending(
		id: UUID,
		userId: UserId,
		now: OffsetDateTime,
	): Reservation?

	fun cancelIfCancellable(
		id: UUID,
		userId: UserId,
		now: OffsetDateTime,
	): Reservation?

	fun expirePending(
		now: OffsetDateTime,
		limit: Int,
	): List<Reservation>
}
