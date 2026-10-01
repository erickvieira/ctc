package dev.erickvieira.flashbooking.adapter.output.persistence.converter

import dev.erickvieira.flashbooking.domain.model.Capacity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CapacityConverterTest {
	private val converter = CapacityConverter()

	@Test
	fun `converts to database column`() {
		assertThat(converter.convertToDatabaseColumn(Capacity.of(5))).isEqualTo(5)
	}

	@Test
	fun `converts from database column`() {
		assertThat(converter.convertToEntityAttribute(5)).isEqualTo(Capacity.of(5))
	}
}
