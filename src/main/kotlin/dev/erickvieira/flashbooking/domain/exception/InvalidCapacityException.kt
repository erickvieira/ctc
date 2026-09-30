package dev.erickvieira.flashbooking.domain.exception

class InvalidCapacityException(val value: Int) :
	RuntimeException("capacity must be greater than or equal to 1, but was $value")
