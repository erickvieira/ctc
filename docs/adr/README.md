# Architecture Decision Records

Decisões arquiteturais do flash-booking, no formato [MADR](https://adr.github.io/madr/) resumido:
contexto, decisão, consequências e alternativas rejeitadas.

| #                                                           | Decisão                                                                      |
|-------------------------------------------------------------|------------------------------------------------------------------------------|
| [0001](0001-nunca-oversell-gate-atomico-sql.md)             | Nunca-oversell por gate atômico no SQL                                       |
| [0002](0002-idempotencia-chave-unica-fingerprint-upsert.md) | Idempotência: chave única por usuário + fingerprint UUID v3 + upsert gateado |
| [0003](0003-expiracao-sweep-skip-locked.md)                 | Expiração por sweep transicional com `FOR UPDATE SKIP LOCKED`                |
| [0004](0004-projecao-redis-cache-aside.md)                  | Disponibilidade como projeção Redis (cache-aside, consistência eventual)     |
| [0005](0005-api-first-openapi-konvert.md)                   | API-first com openapi-generator (kotlin-spring) + mapeamento Konvert         |
| [0006](0006-persistencia-jpa-sql-nativo-flyway.md)          | Persistência: JPA no CRUD, SQL nativo no crítico, Flyway dono do schema      |
