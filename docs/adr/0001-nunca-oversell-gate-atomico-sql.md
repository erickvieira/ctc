# 0001. Nunca-oversell por gate atômico no SQL

- Status: Aceito
- Data: 2026-10-02

## Contexto

Flash sale: muitas requisições concorrentes para o mesmo evento. O requisito mais duro é **nunca vender
além da capacidade**, inclusive sob múltiplas instâncias da aplicação.

Checar e decrementar em memória (`if (available >= q) available -= q`) tem race condition clássica:
duas requisições leem o mesmo `available` e ambas decrementam. Qualquer solução precisa da autoridade
de escrita do banco.

## Decisão

O decremento e a validação são uma única instrução atômica:

```sql
UPDATE events
SET available  = available - :quantity,
    updated_at = :now
WHERE id = :id
  AND available >= :quantity
```

`affected = 0` significa que a última unidade foi disputada: a transação sofre **rollback total** e a API
responde **409 `EVENT_SOLD_OUT`**.

O invariante **não** vive no domínio: um método `Event.reserve(quantity)` só conseguiria validar contra o
`available` carregado, que já pode estar velho. O domínio modela o **pedido** de reserva; a **decisão** de
estoque é do banco.

## Consequências

- Correto sob concorrência sem retry e sem locks explícitos no aplicativo.
- O caminho de estoque depende do Postgres; a lógica vive numa query, não em Kotlin.
- Coberto por teste de concorrência real (100 tentativas / capacidade 50 → exatamente 50 sucessos).

## Alternativas rejeitadas

- **Lock otimista (`@Version`) + retry**: transfere o problema para o cliente/temporizador e gera retry storm.
- **Lock pessimista (`SELECT … FOR UPDATE` seguido de checagem)**: funciona, mas serializa leituras e é mais
  verboso que o próprio `UPDATE` condicional.
- **Contador em memória / Redis**: não é autoritativo entre instâncias e perde a garantia em restart.
- **Fila serializada por evento**: correta, porém overkill para o escopo e adiciona infraestrutura.
