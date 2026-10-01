package dev.erickvieira.flashbooking.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class ClockConfigTest {
	@Test
	fun `provides the system UTC clock`() {
		assertThat(ClockConfig().clock().zone).isEqualTo(ZoneOffset.UTC)
	}
}
