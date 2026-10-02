# 0003. Expiração por sweep transicional com `FOR UPDATE SKIP LOCKED`

- Status: Aceito
- Data: 2026-10-02

## Contexto

Uma reserva `PENDING` segura ingressos por um **tempo de vida** configurável e, ao fim dele, precisa virar
`EXPIRED` e devolver os ingressos ao evento. A devolução precisa acontecer **exatamente uma vez**, mesmo com
múltiplas instâncias rodando o job ao mesmo tempo.

## Decisão

Um `@Scheduled(fixedDelayString = "${app.expiration.sweep-interval}")` processa lotes em **uma transação**:

1. `SELECT id FROM reservations WHERE status = 'PENDING' AND expires_at <= :now ORDER BY expires_at FOR UPDATE SKIP LOCKED LIMIT :batchSize`
2. `UPDATE reservations SET status = 'EXPIRED', updated_at = :now WHERE id IN (...) RETURNING id, event_id, quantity`
3. agrega por `event_id` e `UPDATE events SET available = available + :delta WHERE id = :id`

`:now` vem de um **`Clock` injetável** (testável com clock falso). `SKIP LOCKED` faz instâncias concorrentes
dividirem o lote sem esperar umas pelas outras; o predicado `status = 'PENDING'` garante a transição única.

## Consequências

- Seguro multi-instância sem coordenação externa.
- `available` converge com tolerância de até ~`sweep-interval` (padrão 2s); documentado como consistência
  eventual, não como bug.
- O GET **não** escreve: expiração é sempre lazy-free (sem escrita em caminho de leitura).

## Alternativas rejeitadas

- **Broker/fila com TTL de mensagem**: mais infraestrutura e um segundo sistema de verdade.
- **ShedLock**: resolve lock entre instâncias, mas serializa o sweep e não melhora a transição.
- **Expiração lazy no GET**: introduz escrita em leitura, respostas não-idempotentes e corrida com o gate de estoque.
- **`FOR UPDATE` simples**: bloqueia a instância vizinha em vez de ela pegar outro lote.
