package dev.erickvieira.flashbooking.domain.exception

import java.util.UUID

class ReservationNotFoundException(val id: UUID) :
	RuntimeException("reservation $id was not found")
