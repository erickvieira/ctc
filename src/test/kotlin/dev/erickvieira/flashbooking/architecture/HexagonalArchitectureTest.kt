package dev.erickvieira.flashbooking.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureAssertion
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

class HexagonalArchitectureTest : KoArchitectureAssertion by KoArchitectureCreator {
	@Test
	fun `layers only depend inwards`() {
		Konsist.scopeFromProduction().assertArchitecture {
			val domain = Layer("Domain", "dev.erickvieira.flashbooking.domain..")
			val port = Layer("Port", "dev.erickvieira.flashbooking.port..")
			val application = Layer("Application", "dev.erickvieira.flashbooking.application..")
			val adapter = Layer("Adapter", "dev.erickvieira.flashbooking.adapter..")

			domain.dependsOnNothing()
			port.dependsOn(domain)
			application.dependsOn(domain, port)
			adapter.dependsOn(domain, port)
		}
	}

	@Test
	fun `domain does not depend on frameworks`() {
		Konsist.scopeFromProduction()
			.files
			.withPackage("dev.erickvieira.flashbooking.domain..")
			.assertFalse { file ->
				file.imports.any { import ->
					frameworkPrefixes.any { prefix -> import.name.startsWith(prefix) }
				}
			}
	}

	private companion object {
		val frameworkPrefixes = listOf("org.springframework", "jakarta.", "tools.jackson", "com.fasterxml.jackson")
	}
}
