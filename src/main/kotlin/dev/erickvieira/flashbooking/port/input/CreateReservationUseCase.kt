package dev.erickvieira.flashbooking.port.input

import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion

interface CreateReservationUseCase {
	fun create(command: CreateReservationCommand): ReservationInsertion
}
