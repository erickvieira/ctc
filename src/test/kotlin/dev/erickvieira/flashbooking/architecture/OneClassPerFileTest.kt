package dev.erickvieira.flashbooking.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoTypeAliasDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

class OneClassPerFileTest {
	@Test
	fun `each file declares at most one top-level type`() {
		val scopes = listOf(Konsist.scopeFromProduction(), Konsist.scopeFromTest())

		scopes.forEach { scope ->
			scope.files
				.filterNot { it.path.contains("/generated/") }
				.assertTrue { file ->
					val typeCount =
						file.declarations(includeNested = false, includeLocal = false).count { declaration ->
							declaration is KoClassDeclaration ||
								declaration is KoInterfaceDeclaration ||
								declaration is KoObjectDeclaration ||
								declaration is KoTypeAliasDeclaration
						}

					typeCount <= 1
				}
		}
	}
}
