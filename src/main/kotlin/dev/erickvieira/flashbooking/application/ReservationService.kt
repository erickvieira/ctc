package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.config.ReservationProperties
import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.exception.EventSoldOutException
import dev.erickvieira.flashbooking.domain.exception.ReservationNotFoundException
import dev.erickvieira.flashbooking.domain.model.EventAvailabilityChanged
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion
import dev.erickvieira.flashbooking.domain.model.ReservationStatus
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.port.input.CancelReservationUseCase
import dev.erickvieira.flashbooking.port.input.ConfirmReservationUseCase
import dev.erickvieira.flashbooking.port.input.CreateReservationUseCase
import dev.erickvieira.flashbooking.port.input.GetReservationUseCase
import dev.erickvieira.flashbooking.port.output.EventPersistencePort
import dev.erickvieira.flashbooking.port.output.ReservationPersistencePort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ReservationService(
    private val eventPersistencePort: EventPersistencePort,
    private val reservationPersistencePort: ReservationPersistencePort,
    private val publisher: ApplicationEventPublisher,
    private val properties: ReservationProperties,
    private val clock: Clock,
) : CreateReservationUseCase,
    GetReservationUseCase,
    CancelReservationUseCase,
    ConfirmReservationUseCase {
    /**
     * Cria (ou replay) reserva idempotente. Insert via `NamedParameterJdbcTemplate` = mesma transação
     * JPA; `tryReserve` sem estoque -> rollback desfaz o insert -> chave não persiste (retry com
     * estoque liberado cria reserva nova). Publica [EventAvailabilityChanged] para invalidar o cache.
     *
     * @param command evento, usuário, quantidade e chave de idempotência opcional.
     * @return `Created` no primeiro insert; `Replayed` quando a chave repete o mesmo payload.
     * @throws EventNotFoundException evento inexistente.
     * @throws IdempotencyConflictException chave reusada com payload diferente.
     * @throws EventSoldOutException sem disponibilidade (rollback total).
     */
    @Transactional
    override fun create(command: CreateReservationCommand): ReservationInsertion {
        eventPersistencePort.findById(id = command.eventId) ?: throw EventNotFoundException(id = command.eventId)
        val now = OffsetDateTime.now(clock)
        val reservation =
            Reservation(
                id = UUID.randomUUID(),
                eventId = command.eventId,
                userId = command.userId,
                quantity = command.quantity,
                status = ReservationStatus.PENDING,
                expiresAt = now.plus(properties.ttl),
                createdAt = now,
                updatedAt = now,
            )
        return when (
            val insertion =
                reservationPersistencePort.insertIdempotent(
                    reservation = reservation,
                    idempotency = command.idempotency,
                )
        ) {
            is ReservationInsertion.Created -> {
                if (!eventPersistencePort.tryReserve(
                        eventId = command.eventId,
                        quantity = command.quantity,
                        now = now
                    )
                ) {
                    throw EventSoldOutException(eventId = command.eventId)
                }
                publisher.publishEvent(EventAvailabilityChanged(eventId = command.eventId))
                insertion
            }

            is ReservationInsertion.Replayed -> insertion
        }
    }

    override fun getByIdAndUserId(id: UUID, userId: UserId): Reservation =
        reservationPersistencePort.findByIdAndUserId(id = id, userId = userId)
            ?: throw ReservationNotFoundException(id = id)

    /**
     * Cancela reserva do usuário e devolve ingressos. Idempotente: reserva terminal (ou 2ª chamada)
     * não transiciona nem devolve de novo. Na transição, publica [EventAvailabilityChanged].
     *
     * @param id id da reserva.
     * @param userId dono da reserva.
     * @throws ReservationNotFoundException reserva inexistente ou de outro usuário.
     */
    @Transactional
    override fun cancel(id: UUID, userId: UserId) {
        reservationPersistencePort.findByIdAndUserId(id = id, userId = userId)
            ?: throw ReservationNotFoundException(id = id)
        val now = OffsetDateTime.now(clock)
        val cancelled = reservationPersistencePort.cancelIfCancellable(id = id, userId = userId, now = now)
        if (cancelled != null) {
            eventPersistencePort.release(eventId = cancelled.eventId, amount = cancelled.quantity.value, now = now)
            publisher.publishEvent(EventAvailabilityChanged(eventId = cancelled.eventId))
        }
    }

    /**
     * Confirma uma reserva do usuário, impedindo a expiração. Idempotente: confirmar uma reserva já
     * Confirmada (ou terminal) não transiciona e devolve 204. Não muda `available` (sem evict).
     *
     * @param id id da reserva.
     * @param userId dono da reserva.
     * @throws ReservationNotFoundException reserva inexistente ou de outro usuário.
     */
    @Transactional
    override fun confirm(id: UUID, userId: UserId) {
        reservationPersistencePort.findByIdAndUserId(id = id, userId = userId)
            ?: throw ReservationNotFoundException(id = id)
        reservationPersistencePort.confirmIfPending(id = id, userId = userId, now = OffsetDateTime.now(clock))
    }
}
