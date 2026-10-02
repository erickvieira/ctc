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
    GetReservationUseCase {
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
        // O insert via NamedParameterJdbcTemplate participa da transação JPA porque usa a mesma
        // DataSource; se o tryReserve falhar abaixo (sold out), o rollback desfaz o insert idempotente
        // (ver ReservationApiIntegrationTest: "a sold-out attempt with an idempotency key is not replayed").
        // Como a chave não fica persistida num sold-out, um retry com a mesma chave — depois que a
        // capacidade for liberada — cria uma reserva nova (a tentativa que falhou não "queima" a chave).
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
}
