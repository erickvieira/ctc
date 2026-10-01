package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.config.ReservationProperties
import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.exception.EventSoldOutException
import dev.erickvieira.flashbooking.domain.exception.ReservationNotFoundException
import dev.erickvieira.flashbooking.domain.model.Reservation
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
    override fun create(command: CreateReservationCommand): Reservation {
        val event = eventRepository.findById(command.eventId) ?: throw EventNotFoundException(command.eventId)
        val now = OffsetDateTime.now(clock)
        if (!eventRepository.tryReserve(command.eventId, command.quantity, now)) {
            throw EventSoldOutException(command.eventId)
        }
        val reservation =
            Reservation(
                id = UUID.randomUUID(),
                eventId = event.id,
                userId = command.userId,
                quantity = command.quantity,
                status = ReservationStatus.PENDING,
                expiresAt = now.plus(properties.ttl),
                createdAt = now,
                updatedAt = now,
            )
        return reservationRepository.save(reservation)
    }

    override fun getByIdAndUserId(id: UUID, userId: UserId): Reservation =
        reservationRepository.findByIdAndUserId(id = id, userId = userId) ?: throw ReservationNotFoundException(id)
}
