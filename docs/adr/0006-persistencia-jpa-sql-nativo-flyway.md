# 0006. Persistência: JPA no CRUD, SQL nativo no crítico, Flyway dono do schema

- Status: Aceito
- Data: 2026-10-02

## Contexto

O domínio é puro (hexagonal) e não pode carregar anotações de persistência. Ao mesmo tempo, os caminhos de
concorrência (gate de estoque, upsert idempotente, cancelamento, confirmação, expiração) precisam de SQL
preciso que um ORM não expressa bem.

## Decisão

- **Entidades JPA** vivem só no adaptador de saída; os value objects (`Capacity`, `Quantity`, `UserId`) são
  mapeados com `@Converter(autoApply = true)`. Naming físico via `CamelCaseToUnderscoresNamingStrategy`, sem
  `@Column(name = …)`.
- **JPA/Hibernate** para o CRUD simples; `ddl-auto=validate` (o Hibernate **não** cria/derruba schema).
- **Flyway** é o dono do schema (`V1`, `V2`, `V3`), aplicado no boot.
- **SQL nativo** (`NamedParameterJdbcTemplate`) para os caminhos críticos: `UPDATE … WHERE available >= :q`,
  o upsert `ON CONFLICT … RETURNING (xmax = 0)`, `cancelIfCancellable`/`confirmIfPending`, `release` e o sweep
  `FOR UPDATE SKIP LOCKED`.
- **Mapeamento** entidade↔domínio com Konvert (`EventPersistenceMapper`, `ReservationPersistenceMapper`).

## Consequências

- Domínio sem dependência de framework (`HexagonalArchitectureTest` garante via Konsist).
- O SQL crítico é explícito e revisável; o CRUD continua ergonômico.
- JPA e SQL nativo compartilham a mesma transação (`JpaTransactionManager` + datasource comum).
- Migrações versionadas e reproduzíveis; `validate` pega divergência schema↔entidade no boot.

## Alternativas rejeitadas

- **JPA em tudo**: lock otimista/pessimista não substitui o `UPDATE` condicional com a clareza desejada.
- **JDBC em tudo**: perde o CRUD simples e o mapeamento automático de VOs.
- **`ddl-auto=update`**: schema não versionado, imprevisível em produção e sem revisão.
