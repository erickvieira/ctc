# flash-booking

Núcleo de reserva de ingressos para eventos de capacidade limitada em modelo **flash sale**: reserva
temporária de ingressos com **nunca-oversell**, idempotência, expiração automática e leitura com
consistência eventual.

[![Unit tests](https://github.com/erickvieira/ctc/actions/workflows/unit-tests.yml/badge.svg?branch=main)](https://github.com/erickvieira/ctc/actions/workflows/unit-tests.yml)
[![Integration tests](https://github.com/erickvieira/ctc/actions/workflows/integration-tests.yml/badge.svg?branch=main)](https://github.com/erickvieira/ctc/actions/workflows/integration-tests.yml)
[![Mutation tests](https://github.com/erickvieira/ctc/actions/workflows/mutation-tests.yml/badge.svg?branch=main)](https://github.com/erickvieira/ctc/actions/workflows/mutation-tests.yml)
[![Build](https://github.com/erickvieira/ctc/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/erickvieira/ctc/actions/workflows/build.yml)

![Coverage](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/erickvieira/0e45facbc6702751746313e68a827e8a/raw/coverage.json)
![Mutation](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/erickvieira/0e45facbc6702751746313e68a827e8a/raw/mutation.json)
![Unit tests](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/erickvieira/0e45facbc6702751746313e68a827e8a/raw/tests-unit.json)
![Integration tests](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/erickvieira/0e45facbc6702751746313e68a827e8a/raw/tests-integration.json)

## Stack

Kotlin 2.3.21 · Spring Boot 4.1.0 · Java 21 · Jackson 3 (`tools.jackson`) · PostgreSQL 16 · Redis 7 ·
Flyway · Gradle 9.5.1 (Kotlin DSL) · openapi-generator 7.25.0 · Konvert 4.5.1 · Docker/Compose.

## Arquitetura

Hexagonal formal, travada por testes Konsist (`HexagonalArchitectureTest`) e "1 classe por arquivo"
(`OneClassPerFileTest`):

- `domain` — modelos, value objects (invariantes), comandos e exceções. Puro, sem framework.
- `port/input` (`*UseCase`) e `port/output` (`*PersistencePort`, `*CachePort`) — interfaces.
- `application` — `@Service` que implementa os use cases e orquestra a transação.
- `adapter/input/web` — controllers que implementam as interfaces OpenAPI geradas;
  `adapter/output/{persistence,cache}`.

Mapeamentos (domínio ↔ DTO/entidade/JSON) são objetos gerados por **Konvert** (`@Konverter`), sem bean/DI.
As decisões estão detalhadas em [`docs/adr/`](docs/adr/).

## Endpoints

Todas as rotas exigem o header `X-User-Id` (UUID); reservas de outro usuário respondem **404**.

| Método | Rota                              | Sucesso          | Notas                                           |
|--------|-----------------------------------|------------------|-------------------------------------------------|
| POST   | `/events`                         | 201 + `Location` | cria evento                                     |
| GET    | `/events/{id}`                    | 200              | view com `available` (cache-aside no Redis)     |
| POST   | `/events/{id}/reservations`       | 201 / 200        | header opcional `Idempotency-Key`; 200 = replay |
| GET    | `/reservations/{id}`              | 200              |                                                 |
| POST   | `/reservations/{id}/confirmation` | 204              | idempotente; `PENDING` → `CONFIRMED`            |
| DELETE | `/reservations/{id}`              | 204              | idempotente; devolve os ingressos               |

Erros: `application/problem+json` com `code` estável — `VALIDATION_ERROR`/`INVALID_QUANTITY`/`INVALID_CAPACITY`
(400), `*_NOT_FOUND` (404), `EVENT_SOLD_OUT`/`IDEMPOTENCY_CONFLICT` (409), `INTERNAL_ERROR` (500). Sem 422.

## Rodar

```
docker compose up --build
```

- Health: <http://localhost:8080/actuator/health>
- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- Smoke (6 endpoints): `./scripts/smoke.sh`
- Requisições manuais: `http/requests.http`

O `compose.yaml` sobe `api` (Dockerfile multi-stage), `postgres:16` e `redis:7` com healthchecks.

## Testes

```
./gradlew verify --parallel --max-workers=8
```

`verify` = `test` (unit + Konsist, sem Docker) + gate de cobertura JaCoCo (≥ 90%) + `integrationTest`
(Testcontainers: Postgres/Redis reais — repositório, concorrência, idempotência, expiração, cache e web) +
`mutationTest` (PIT, gate de kill ≥ 90%). As classes rodam em paralelo (`maxParallelForks`, padrão 8;
sobrescrevível com `-PmaxParallelForks=`).

Cada etapa roda isolada também: `./gradlew check`, `./gradlew integrationTest`, `./gradlew mutationTest`.

### CI (GitHub Actions)

`.github/workflows/` — quatro pipelines independentes, cada uma publicando artefatos:

| Workflow          | Comando                                          | Artefato            |
|-------------------|--------------------------------------------------|---------------------|
| Unit tests        | `./gradlew check`                                | relatório JaCoCo    |
| Integration tests | `./gradlew integrationTest -PmaxParallelForks=2` | relatório de testes |
| Mutation tests    | `./gradlew mutationTest`                         | relatório PIT       |
| Build             | `./gradlew bootJar` + `docker build`             | jar                 |

Os badges numéricos do topo (`coverage`, `mutation`, `unit tests`, `integration tests`) são publicados **apenas
em push para a `main`**, via `schneegans/dynamic-badges-action` num gist servido pelo shields.io, a partir de
`scripts/quality-metrics.py` (lê os XMLs de JaCoCo/PIT/JUnit). Requer os secrets do repositório `GIST_SECRET`
(PAT com escopo `gist`) e `GIST_ID`.

## Decisões e trade-offs

Resumo; o detalhamento está em `docs/adr/`.

- **Nunca-oversell** por `UPDATE … WHERE available >= :q` (0 linhas → 409). Correto sob concorrência, sem
  retry; o invariante fica no SQL, não no domínio (o `available` em memória já pode estar velho).
- **Idempotência** com `UNIQUE (user_id, idempotency_key)` + fingerprint `UUID v3` + upsert gateado: 201 novo /
  200 replay / 409 conflito. Sem TTL; um sold-out sofre rollback, então a chave não fica persistida.
- **Expiração** por sweep `FOR UPDATE SKIP LOCKED` (multi-instância, transição única) com `Clock` injetável;
  consistência com tolerância de ~`sweep-interval`, sem escrita no caminho de leitura.
- **Redis** como projeção cache-aside (`event:{id}`, TTL 30s + jitter, DEL após commit), best-effort: o
  banco é a fonte de verdade.
- **API-first** (OpenAPI modular) + **Konvert** no mapeamento; código gerado isolado no adaptador.
- **Persistência**: JPA no CRUD, SQL nativo no crítico, Flyway dono do schema, entidades no adaptador.

## Uso de IA

A implementação foi conduzida com apoio de um agente de código (opencode), em ciclos por milestone: cada
fatia fechava com `verify` verde e era **revisada e commitada manualmente** — o agente nunca commitava. A IA
escreveu código e testes, propôs trade-offs e operou a toolchain; as decisões de arquitetura foram revisadas
e validadas a cada etapa, com gates de Konsist/JaCoCo/PIT e testes de concorrência contra infraestrutura real.

## Evoluções futuras

- Observabilidade (perfil `observability`: OpenTelemetry → Prometheus/Loki/Tempo → Grafana, dashboard RED).
- Autenticação/autorização reais (hoje `X-User-Id` dependeria de um suposto gateway de autenticação para ser injetado).
- Expurgo/limite de chaves de idempotência e índices parciais.
- Listagem/paginação de eventos (cortado do escopo devido ao tempo; poderia ser feito usando cursor-based pagination).
- Testes de carga e ajuste de pool/paralelismo.

## Configuração

| Chave                                 | Default | Descrição                              |
|---------------------------------------|---------|----------------------------------------|
| `app.reservation.ttl`                 | `10m`   | tempo de vida de uma reserva Pendente  |
| `app.reservation.max-per-reservation` | `10`    | máximo de ingressos por reserva        |
| `app.expiration.sweep-interval`       | `2s`    | intervalo do job de expiração          |
| `app.expiration.batch-size`           | `100`   | reservas processadas por lote do sweep |
| `app.cache.event-ttl`                 | `30s`   | TTL da view do evento no Redis         |
| `app.cache.event-ttl-jitter`          | `5s`    | jitter somado ao TTL                   |

Tudo sobrescrevível por ENV (Spring standard / `APP_*`).

## Toolchain (workarounds)

- **openapi-generator**: o plugin Gradle parou na 7.14.0 (sem `useSpringBoot4`/`useJackson3`); a geração usa o
  CLI 7.25.0 via `JavaExec` (`openapiGenerate`), saída em `build/generated/openapi/...` com `org/openapitools/**`
  excluído.
- **PIT (mutação)**: o plugin `info.solidsoft.pitest` quebra no Gradle 9 (`reporting.baseDir` removido); roda
  `pitest-command-line` 1.20.2 + `pitest-junit5-plugin` 1.2.3 via `JavaExec` (`mutationTest`), gate de kill ≥ 90%.
- **Spring Boot 4**: autoconfig modular — sem `org.springframework.boot:spring-boot-flyway`, o Flyway não roda
  (silenciosamente).
- **Testcontainers**: o Boot 4 não gerencia as versões dos módulos → importa `testcontainers-bom:1.21.4`.
- **SQL crítico**: upsert idempotente (`INSERT … ON CONFLICT … RETURNING`, incluindo `(xmax = 0) AS inserted`)
  via `NamedParameterJdbcTemplate`; `@Modifying @Query` só devolve contagem, não a linha.
- **Kotlin**: `-Xno-param-assertions -Xno-call-assertions` remove os `Intrinsics.checkNotNull*` (mutantes
  equivalentes).
