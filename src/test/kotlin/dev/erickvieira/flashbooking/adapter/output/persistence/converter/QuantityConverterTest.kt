package dev.erickvieira.flashbooking.adapter.output.persistence.converter

import dev.erickvieira.flashbooking.domain.model.Quantity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class QuantityConverterTest {
	private val converter = QuantityConverter()

	@Nested
	@DisplayName("convertToDatabaseColumn")
	inner class ConvertToDatabaseColumn {
		@Test
		fun `converts to database column`() {
			assertThat(converter.convertToDatabaseColumn(attribute = Quantity.of(value = 3, max = 10))).isEqualTo(3)
		}
	}

	@Nested
	@DisplayName("convertToEntityAttribute")
	inner class ConvertToEntityAttribute {
		@Test
		fun `converts from database column`() {
			assertThat(converter.convertToEntityAttribute(dbData = 3)).isEqualTo(Quantity.fromStorage(value = 3))
		}
	}
}
