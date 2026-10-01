# Legibilidade

- **Mapper sem aninhamento**: nunca passe uma chamada de mapper (`XMapperImpl.to*`) como argumento de outra chamada. Atribua o resultado a um `val` nomeado, ou use encadeamento `.let`/`.map`/`?.let` onde o mapper recebe `it`.
- **Named arguments**: instancie classes Kotlin (data class, value class, exceção) e chame funções/métodos Kotlin sempre com named arguments.
