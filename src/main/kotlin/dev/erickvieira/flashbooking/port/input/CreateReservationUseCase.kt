package dev.erickvieira.flashbooking.port.input

import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.model.Reservation

interface CreateReservationUseCase {
	fun create(command: CreateReservationCommand): Reservation
}
