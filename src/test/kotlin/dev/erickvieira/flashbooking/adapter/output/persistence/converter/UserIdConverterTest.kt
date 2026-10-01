package dev.erickvieira.flashbooking.adapter.output.persistence.converter

import dev.erickvieira.flashbooking.domain.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class UserIdConverterTest {
	private val converter = UserIdConverter()

	@Test
	fun `converts to database column`() {
		val id = UUID.randomUUID()

		assertThat(converter.convertToDatabaseColumn(UserId(id))).isEqualTo(id)
	}

	@Test
	fun `converts from database column`() {
		val id = UUID.randomUUID()

		assertThat(converter.convertToEntityAttribute(id)).isEqualTo(UserId(id))
	}
}
