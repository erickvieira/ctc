package dev.erickvieira.flashbooking.fixtures

import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationStatus
import dev.erickvieira.flashbooking.domain.model.UserId
import java.time.OffsetDateTime
import java.util.UUID

fun Reservation.Companion.fake(
	id: UUID = UUID.randomUUID(),
	eventId: UUID = UUID.randomUUID(),
	userId: UserId = UserId.fake(),
	quantity: Quantity = Quantity.fake(),
	status: ReservationStatus = ReservationStatus.PENDING,
	expiresAt: OffsetDateTime = defaultOffsetDateTime,
	createdAt: OffsetDateTime = defaultOffsetDateTime,
	updatedAt: OffsetDateTime = defaultOffsetDateTime,
): Reservation =
	Reservation(
		id = id,
		eventId = eventId,
		userId = userId,
		quantity = quantity,
		status = status,
		expiresAt = expiresAt,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)
