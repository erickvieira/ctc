# Flash Booking

Núcleo de reserva de ingressos para eventos de capacidade limitada em modelo flash sale. Uma reserva segura ingressos de um evento temporariamente; o estoque é devolvido por cancelamento ou por expiração.

## Language

**Evento**:
Acontecimento com data de início e uma capacidade total de ingressos.
_Avoid_: Show, sessão, lote

**Capacidade**:
Total de ingressos que um evento pode vender. Fixa desde a criação.
_Avoid_: Lotação, limite, total

**Disponível**:
Ingressos ainda não segurados por reservas pendentes. Capacidade menos a soma das quantidades seguradas.
_Avoid_: Estoque, saldo, vagas

**Reserva**:
Pedido temporário que segura uma quantidade de ingressos de um evento até ser cancelado ou expirar.
_Avoid_: Pedido, compra, inscrição, booking

**Quantidade**:
Número de ingressos que uma reserva segura de um evento. Inteiro de 1 até o máximo por reserva (configurável, padrão 10).
_Avoid_: Total, tamanho, contagem

**Pendente**:
Estado inicial de uma reserva que está segurando ingressos e ainda pode ser cancelada.
_Avoid_: Ativa, aberta

**Expirada**:
Estado terminal de uma reserva cujo tempo de vida terminou; seus ingressos já voltaram ao evento.
_Avoid_: Vencida, timeout

**Cancelada**:
Estado terminal de uma reserva encerrada por ação explícita; seus ingressos voltaram ao evento.
_Avoid_: Removida, deletada

**Expiração**:
Passagem automática de Pendente para Expirada ao fim do tempo de vida.
_Avoid_: Timeout, vencimento

**Cancelamento**:
Passagem de Pendente para Cancelada por requisição explícita.
_Avoid_: Deleção, remoção

**Tempo de vida**:
Duração entre a criação de uma reserva e sua expiração. Configurável, padrão de 10 minutos.
_Avoid_: Timeout, validade

**Oversell**:
Venda de mais ingressos do que a capacidade do evento. Nunca deve ocorrer.
