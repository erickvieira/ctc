# 0002. Idempotência: chave única por usuário + fingerprint UUID v3 + upsert gateado

- Status: Aceito
- Data: 2026-10-02

## Contexto

O cliente pode retentar a criação de reserva após um timeout de rede sem saber se o primeiro pedido foi
processado. Sem proteção, o retry cria reservas duplicadas e consome estoque indevido. A resposta do retry
precisa distinguir **criação nova** (201) de **replay** (200) e sinalizar **conflito** (409) quando a mesma
chave é reutilizada com um payload diferente.

## Decisão

Header opcional `Idempotency-Key`, com unicidade **por usuário**: `UNIQUE (user_id, idempotency_key)`.

A chave é acompanhada de um *fingerprint* `UUID v3` de `flashbooking|userId|eventId|quantity`. O insert vira
um upsert gateado:

```sql
INSERT INTO reservations (...)
VALUES (...)
ON CONFLICT (user_id, idempotency_key) DO UPDATE
   SET updated_at = reservations.updated_at
 WHERE reservations.fingerprint = EXCLUDED.fingerprint
RETURNING ..., (xmax = 0) AS inserted;
```

- `inserted = true` → segue para o gate de estoque (201).
- uma linha com `inserted = false` → replay idêntico (200).
- zero linhas → chave reutilizada com payload diferente (409 `IDEMPOTENCY_CONFLICT`).

O `DO UPDATE` é um no-op (reescreve o próprio `updated_at`, preservando o original); sua função é fazer o
`WHERE` filtrar o conflito de fingerprint e o `RETURNING` informar `xmax = 0` (linha nova).

## Consequências

- Retry seguro: mesma chave + mesmo payload devolve a reserva original sem efeito colateral.
- O escopo por usuário evita que a chave de um cliente bloqueie a de outro.
- O fingerprint inclui `eventId`: a mesma chave em outro evento é conflito, não replay.
- Uma tentativa que falha por sold-out sofre rollback total, então a chave **não** fica persistida — o retry
  com estoque liberado cria uma reserva nova.
- Sem TTL/expurgo da chave (a reserva é o registro durável).

## Alternativas rejeitadas

- **Chave global (sem `user_id`)**: colisões entre clientes e vazamento de existência.
- **Fingerprint só com a chave**: não detecta reuso com payload diferente.
- **Tabela de idempotência separada**: mais uma escrita/transação para a mesma garantia que o índice único já dá.
- **`INSERT` + `SELECT` em passos separados**: janela de corrida entre checar e inserir.
