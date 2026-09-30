package dev.erickvieira.flashbooking.domain.exception

import java.util.UUID

class EventNotFoundException(val id: UUID) :
	RuntimeException("event $id was not found")
