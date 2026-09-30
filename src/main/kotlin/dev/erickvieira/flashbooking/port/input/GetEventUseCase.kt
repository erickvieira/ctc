package dev.erickvieira.flashbooking.port.input

import dev.erickvieira.flashbooking.domain.model.Event
import java.util.UUID

interface GetEventUseCase {
	fun getById(id: UUID): Event
}
