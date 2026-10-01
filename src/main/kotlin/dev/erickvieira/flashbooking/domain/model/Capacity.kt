package dev.erickvieira.flashbooking.domain.model

import dev.erickvieira.flashbooking.domain.exception.InvalidCapacityException

@JvmInline
value class Capacity private constructor(val value: Int) {
	companion object {
		fun of(value: Int): Capacity {
			if (value < 1) {
				throw InvalidCapacityException(value = value)
			}
			return Capacity(value = value)
		}
	}
}
