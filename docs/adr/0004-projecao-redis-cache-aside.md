# 0004. Disponibilidade como projeção Redis (cache-aside, consistência eventual)

- Status: Aceito
- Data: 2026-10-02

## Contexto

`GET /events/{id}` é a leitura mais quente do sistema e carrega o `available`, que muda a cada reserva,
cancelamento ou expiração. Ler sempre do Postgres é correto mas desnecessário para um dado que tolera
pequena defasagem.

## Decisão

Projetar a **view completa do evento** em Redis, com **cache-aside**:

- chave `event:{id}` → JSON da view;
- **hit** devolve do cache; **miss** lê o DB, popula e devolve;
- nas mutações (reserva/cancelamento/expiração), **DEL após o commit**
  (`@TransactionalEventListener(phase = AFTER_COMMIT)`), nunca dentro da transação;
- TTL de 30s + **jitter** (evita expiração sincronizada de muitas chaves);
- **best-effort**: se o Redis cair, o cache vira miss/ignorado e o DB continua respondendo.

O Postgres é sempre a **fonte de verdade**; o Redis é uma projeção descartável.

## Consequências

- Leituras rápidas e proteção do banco em picos, com staleness máxima ≤ TTL.
- Evict após commit evita cachear um estado que a transação pode reverter (rollback de sold-out).
- `EventAvailabilityChanged` desacopla quem muta de quem invalida.

## Alternativas rejeitadas

- **Write-through**: acopla a escrita ao cache e propaga falha do Redis para a reserva.
- **Invalidar antes do commit**: pode popular o cache com estado não-commitado.
- **Sem cache**: correto, mas desperdiça o requisito de consistência eventual do desafio.
- **`pub/sub`/keyspace notifications**: mais partes móveis para o mesmo efeito de um DEL explícito.
