package dev.erickvieira.flashbooking.domain.exception

import java.util.UUID

class EventSoldOutException(val eventId: UUID) :
	RuntimeException("event $eventId has no availability for the requested quantity")
