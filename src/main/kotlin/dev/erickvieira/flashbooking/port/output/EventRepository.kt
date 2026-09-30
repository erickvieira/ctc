package dev.erickvieira.flashbooking.port.output

import dev.erickvieira.flashbooking.domain.model.Event
import java.util.UUID

interface EventRepository {
	fun save(event: Event): Event

	fun findById(id: UUID): Event?
}
