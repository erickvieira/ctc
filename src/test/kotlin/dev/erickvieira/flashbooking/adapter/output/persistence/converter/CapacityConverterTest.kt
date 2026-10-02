package dev.erickvieira.flashbooking.adapter.output.persistence.converter

import dev.erickvieira.flashbooking.domain.model.Capacity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CapacityConverterTest {
	private val converter = CapacityConverter()

	@Nested
	@DisplayName("convertToDatabaseColumn")
	inner class ConvertToDatabaseColumn {
		@Test
		fun `converts to database column`() {
			assertThat(converter.convertToDatabaseColumn(attribute = Capacity.of(value = 5))).isEqualTo(5)
		}
	}

	@Nested
	@DisplayName("convertToEntityAttribute")
	inner class ConvertToEntityAttribute {
		@Test
		fun `converts from database column`() {
			assertThat(converter.convertToEntityAttribute(dbData = 5)).isEqualTo(Capacity.of(value = 5))
		}
	}
}
