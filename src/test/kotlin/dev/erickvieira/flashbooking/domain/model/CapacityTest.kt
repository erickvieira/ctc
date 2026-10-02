package dev.erickvieira.flashbooking.domain.model

import dev.erickvieira.flashbooking.domain.exception.InvalidCapacityException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CapacityTest {
	@Nested
	@DisplayName("of")
	inner class Of {
		@Test
		fun `accepts the minimum valid capacity`() {
			assertThat(Capacity.of(value = 1).value).isEqualTo(1)
		}

		@Test
		fun `accepts positive capacities`() {
			assertThat(Capacity.of(value = 100).value).isEqualTo(100)
		}

		@Test
		fun `rejects zero`() {
			assertThrows<InvalidCapacityException> { Capacity.of(value = 0) }
		}

		@Test
		fun `rejects negative capacities carrying the offending value`() {
			val exception = assertThrows<InvalidCapacityException> { Capacity.of(value = -5) }

			assertThat(exception.value).isEqualTo(-5)
		}
	}

	@Test
	fun `exposes the wrapped value through a boxed reference`() {
		val boxed: List<Capacity> = listOf(Capacity.of(value = 7))

		assertThat(boxed[0].value).isEqualTo(7)
	}
}
