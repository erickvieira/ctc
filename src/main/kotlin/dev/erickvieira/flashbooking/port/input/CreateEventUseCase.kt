package dev.erickvieira.flashbooking.port.input

import dev.erickvieira.flashbooking.domain.command.CreateEventCommand
import dev.erickvieira.flashbooking.domain.model.Event

interface CreateEventUseCase {
	fun create(command: CreateEventCommand): Event
}
