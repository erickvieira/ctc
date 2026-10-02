package dev.erickvieira.flashbooking.domain.model

import dev.erickvieira.flashbooking.domain.exception.InvalidQuantityException

@JvmInline
value class Quantity private constructor(val value: Int) {
	companion object {
		fun of(value: Int, max: Int): Quantity {
			if (value !in 1..max) {
				throw InvalidQuantityException(value = value, max = max)
			}
			return Quantity(value = value)
		}

		fun fromStorage(value: Int): Quantity = Quantity(value = value)
	}
}
