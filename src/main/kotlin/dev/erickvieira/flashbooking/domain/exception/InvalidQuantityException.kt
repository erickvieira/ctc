package dev.erickvieira.flashbooking.domain.exception

class InvalidQuantityException(
	val value: Int,
	val max: Int,
) : RuntimeException("quantity must be between 1 and $max, but was $value")
