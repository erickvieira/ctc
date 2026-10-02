package dev.erickvieira.flashbooking.adapter.output.persistence.converter

import dev.erickvieira.flashbooking.domain.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class UserIdConverterTest {
	private val converter = UserIdConverter()

	@Nested
	@DisplayName("convertToDatabaseColumn")
	inner class ConvertToDatabaseColumn {
		@Test
		fun `converts to database column`() {
			val id = UUID.randomUUID()

			assertThat(converter.convertToDatabaseColumn(attribute = UserId(value = id))).isEqualTo(id)
		}
	}

	@Nested
	@DisplayName("convertToEntityAttribute")
	inner class ConvertToEntityAttribute {
		@Test
		fun `converts from database column`() {
			val id = UUID.randomUUID()

			assertThat(converter.convertToEntityAttribute(dbData = id)).isEqualTo(UserId(value = id))
		}
	}
}
