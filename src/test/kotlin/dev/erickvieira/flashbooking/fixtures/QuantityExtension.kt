package dev.erickvieira.flashbooking.fixtures

import dev.erickvieira.flashbooking.domain.model.Quantity

fun Quantity.Companion.fake(
	value: Int = 1,
	max: Int = 10,
): Quantity = Quantity.of(value = value, max = max)
