# Legibilidade

- **Mapper sem aninhamento**: nunca passe uma chamada de mapper (`XMapperImpl.to*`) como argumento de outra chamada.
  Atribua o resultado a um `val` nomeado, ou use encadeamento `.let`/`.map`/`?.let` onde o mapper recebe `it`.
- **Named arguments**: instancie classes Kotlin (data class, value class, exceção) e chame funções/métodos Kotlin com
  named arguments — em produção **e** testes. Métodos Java (Spring/JPA/JDK), stdlib (AssertJ/MockK) e modelos gerados
  pelo OpenAPI ficam posicionais.
- **Sem FQN inline**: em código Kotlin (produção, testes e `build.gradle.kts`), importe o tipo e use o nome simples —
  nunca escreva `pacote.subpacote.Tipo` no corpo. Isentas as strings que exigem o nome qualificado por natureza:
  expressões do Konvert, globs do Konsist/PIT, `mainClass.set("...")` e valores de `application*.yml`.
- **KDoc, não `//`**: documente o *porquê* de classes, funções e propriedades com KDoc (`/** */` + `@param`/`@return`/
  `@throws`/`@see`). Nenhum `//` solto em código Kotlin: ou vira KDoc, ou o código vira autoexplicativo.

# Testes

- **JUnit Jupiter + AssertJ**: `assertThrows<T> { }` (reificado).
- **Asserção por objeto**: valide um resultado complexo com um único
  `assertThat(result).usingRecursiveComparison().isEqualTo(expected)`, concentrando o esperado num objeto (de
  preferência via as fixtures `fake(...)`), em vez de uma sequência de `assertThat` campo-a-campo. Mantenha asserts
  separados só para aspectos distintos (ex.: status HTTP + header `Location` + corpo) e para exceções
  (`usingRecursiveComparison` compara `stackTrace`/`cause`).
- **`@Nested` por método**: nos unit tests, cada método sob teste vira uma `inner class` com
  `@Nested @DisplayName("nomeDoMetodo")`.
- **MockK estrito**: `mockk<T>()` (sem relaxed); `confirmVerified(...)` + `checkUnnecessaryStub(...)` no `@AfterEach`.
  Em teste com mock de fixture (ex.: `HttpServletRequest`), aplique `checkUnnecessaryStub(...)` e `confirmVerified(...)`
  apenas onde todas as interações são verificadas.
- **Fixtures**: fakers em `src/test/.../fixtures`, 1 arquivo por classe (`<Class>Extension.kt`),
  `fun <Class>.Companion.fake(...)`.

# Arquitetura

- **Hexagonal formal** (`domain`/`port`/`application`/`adapter`), travada por Konsist (`HexagonalArchitectureTest`).
- **1 classe por arquivo** (exceção: extension functions), travada por `OneClassPerFileTest`.
- **Mappers são objetos estáticos Konvert** (`@Konverter`), chamados direto (sem bean/DI).
- **Value objects** como `@JvmInline value class`; comandos já carregam o VO.
