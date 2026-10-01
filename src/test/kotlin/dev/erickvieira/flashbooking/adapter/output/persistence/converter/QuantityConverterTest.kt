package dev.erickvieira.flashbooking.adapter.output.persistence.converter

import dev.erickvieira.flashbooking.domain.model.Quantity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QuantityConverterTest {
	private val converter = QuantityConverter()

	@Test
	fun `converts to database column`() {
		assertThat(converter.convertToDatabaseColumn(Quantity.of(3, 10))).isEqualTo(3)
	}

	@Test
	fun `converts from database column`() {
		assertThat(converter.convertToEntityAttribute(3)).isEqualTo(Quantity.fromStorage(3))
	}
}
