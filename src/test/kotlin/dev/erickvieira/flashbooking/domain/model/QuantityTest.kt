package dev.erickvieira.flashbooking.domain.model

import dev.erickvieira.flashbooking.domain.exception.InvalidQuantityException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class QuantityTest {
	@Nested
	@DisplayName("of")
	inner class Of {
		@Test
		fun `accepts the minimum value`() {
			assertThat(Quantity.of(value = 1, max = 10).value).isEqualTo(1)
		}

		@Test
		fun `accepts the maximum value`() {
			assertThat(Quantity.of(value = 10, max = 10).value).isEqualTo(10)
		}

		@Test
		fun `rejects zero`() {
			val exception = assertThrows<InvalidQuantityException> { Quantity.of(value = 0, max = 10) }

			assertThat(exception.value).isEqualTo(0)
			assertThat(exception.max).isEqualTo(10)
		}

		@Test
		fun `rejects values above the maximum`() {
			val exception = assertThrows<InvalidQuantityException> { Quantity.of(value = 11, max = 10) }

			assertThat(exception.value).isEqualTo(11)
			assertThat(exception.max).isEqualTo(10)
		}
	}

	@Nested
	@DisplayName("fromStorage")
	inner class FromStorage {
		@Test
		fun `restores the persisted value`() {
			assertThat(Quantity.fromStorage(value = 5).value).isEqualTo(5)
		}
	}

	@Test
	fun `exposes the wrapped value through a boxed reference`() {
		val boxed: List<Quantity> = listOf(Quantity.of(value = 7, max = 10))

		assertThat(boxed[0].value).isEqualTo(7)
	}
}
