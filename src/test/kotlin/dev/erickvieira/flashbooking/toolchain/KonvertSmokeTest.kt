package dev.erickvieira.flashbooking.toolchain

import io.mcarle.konvert.api.Konverter
import kotlin.test.Test
import kotlin.test.assertEquals

data class SmokeSource(
	val name: String,
	val count: Int,
)

data class SmokeTarget(
	val name: String,
	val count: Int,
)

@Konverter
interface SmokeMapper {
	fun toTarget(source: SmokeSource): SmokeTarget
}

class KonvertSmokeTest {
	private val mapper: SmokeMapper = SmokeMapperImpl

	@Test
	fun `generated mapper copies matching properties`() {
		val source = SmokeSource(name = "flash", count = 3)

		assertEquals(SmokeTarget(name = "flash", count = 3), mapper.toTarget(source))
	}
}
