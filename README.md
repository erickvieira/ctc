# flash-booking

Núcleo de reserva de ingressos (flash sale). API REST + PostgreSQL + Redis. Contrato OpenAPI.

## Toolchain (workarounds)

- **openapi-generator**: plugin Gradle parou na 7.14.0 (sem `useSpringBoot4`/`useJackson3`). Geração = CLI 7.25.0 via
  `JavaExec` (`openapiGenerate`). Saída `build/generated/openapi/src/main/kotlin` no source set `main`; exclui
  `org/openapitools/**`.
- **PIT (mutação)**: plugin `info.solidsoft.pitest` quebra no Gradle 9 (`reporting.baseDir` removido). Roda
  `pitest-command-line` 1.20.2 + `pitest-junit5-plugin` 1.2.3 via `JavaExec` (`mutationTest`). Gate: kill ≥ 90%.
- **Spring Boot 4**: autoconfig modular. Sem `org.springframework.boot:spring-boot-flyway`, Flyway não roda
  (silencioso).
- **Testcontainers**: Boot 4 não gerencia versões dos módulos → importa `testcontainers-bom:1.21.4`.
- **SQL crítico**: upsert idempotente (`INSERT … ON CONFLICT … RETURNING`, inclui `(xmax = 0) AS inserted`) via
  `NamedParameterJdbcTemplate`; `@Modifying @Query` só devolve contagem, não a linha.
- **Gates**: `verify` = `test` + `integrationTest` + `mutationTest`. JaCoCo exclui `Main` + OpenAPI gerado; mutação
  exclui `*Test*`, fixtures e gerado.

## Rodar

```
docker compose up --build
```

- API/Health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Smoke: `./scripts/smoke.sh`
