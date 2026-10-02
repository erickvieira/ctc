package dev.erickvieira.flashbooking.domain.model

sealed interface ReservationInsertion {
	val reservation: Reservation

	data class Created(override val reservation: Reservation) : ReservationInsertion

	data class Replayed(override val reservation: Reservation) : ReservationInsertion
}
