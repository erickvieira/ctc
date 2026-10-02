package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.config.ReservationProperties
import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.exception.EventSoldOutException
import dev.erickvieira.flashbooking.domain.exception.ReservationNotFoundException
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion
import dev.erickvieira.flashbooking.domain.model.ReservationStatus
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.port.input.CancelReservationUseCase
import dev.erickvieira.flashbooking.port.input.CreateReservationUseCase
import dev.erickvieira.flashbooking.port.input.GetReservationUseCase
import dev.erickvieira.flashbooking.port.output.EventRepository
import dev.erickvieira.flashbooking.port.output.ReservationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ReservationService(
    private val eventRepository: EventRepository,
    private val reservationRepository: ReservationRepository,
    private val properties: ReservationProperties,
    private val clock: Clock,
) : CreateReservationUseCase,
    GetReservationUseCase,
    CancelReservationUseCase {
    /**
     * Cria (ou replay) reserva idempotente. Insert via `NamedParameterJdbcTemplate` = mesma transação
     * JPA; `tryReserve` sem estoque -> rollback desfaz o insert -> chave não persiste (retry com
     * estoque liberado cria reserva nova).
     *
     * @param command evento, usuário, quantidade e chave de idempotência opcional.
     * @return `Created` no primeiro insert; `Replayed` quando a chave repete o mesmo payload.
     * @throws EventNotFoundException evento inexistente.
     * @throws IdempotencyConflictException chave reusada com payload diferente.
     * @throws EventSoldOutException sem disponibilidade (rollback total).
     */
    @Transactional
    override fun create(command: CreateReservationCommand): ReservationInsertion {
        eventRepository.findById(id = command.eventId) ?: throw EventNotFoundException(id = command.eventId)
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
                reservationRepository.insertIdempotent(
                    reservation = reservation,
                    idempotency = command.idempotency,
                )
        ) {
            is ReservationInsertion.Created -> {
                if (!eventRepository.tryReserve(eventId = command.eventId, quantity = command.quantity, now = now)) {
                    throw EventSoldOutException(eventId = command.eventId)
                }
                insertion
            }
            is ReservationInsertion.Replayed -> insertion
        }
    }

    override fun getByIdAndUserId(id: UUID, userId: UserId): Reservation =
        reservationRepository.findByIdAndUserId(id = id, userId = userId) ?: throw ReservationNotFoundException(id = id)

    /**
     * Cancela reserva do usuário e devolve ingressos. Idempotente: reserva terminal (ou 2ª chamada)
     * não transiciona nem devolve de novo.
     *
     * @param id id da reserva.
     * @param userId dono da reserva.
     * @throws ReservationNotFoundException reserva inexistente ou de outro usuário.
     */
    @Transactional
    override fun cancel(id: UUID, userId: UserId) {
        reservationRepository.findByIdAndUserId(id = id, userId = userId)
            ?: throw ReservationNotFoundException(id = id)
        val now = OffsetDateTime.now(clock)
        val cancelled = reservationRepository.cancelIfPending(id = id, userId = userId, now = now)
        if (cancelled != null) {
            eventRepository.release(eventId = cancelled.eventId, amount = cancelled.quantity.value, now = now)
        }
    }
}
