package dev.erickvieira.flashbooking.adapter.input.web.controller

import dev.erickvieira.flashbooking.adapter.input.web.api.ReservationsApi
import dev.erickvieira.flashbooking.adapter.input.web.mapper.ReservationApiMapperImpl
import dev.erickvieira.flashbooking.adapter.input.web.model.CreateReservationRequest
import dev.erickvieira.flashbooking.adapter.input.web.model.Reservation as ReservationResponse
import dev.erickvieira.flashbooking.config.ReservationProperties
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.port.input.CancelReservationUseCase
import dev.erickvieira.flashbooking.port.input.CreateReservationUseCase
import dev.erickvieira.flashbooking.port.input.GetReservationUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
class ReservationController(
    private val createReservationUseCase: CreateReservationUseCase,
    private val getReservationUseCase: GetReservationUseCase,
    private val cancelReservationUseCase: CancelReservationUseCase,
    private val properties: ReservationProperties,
) : ReservationsApi {
    override fun createReservation(
        xUserId: UUID,
        id: UUID,
        createReservationRequest: CreateReservationRequest,
        idempotencyKey: String?,
    ): ResponseEntity<ReservationResponse> {
        val command =
            ReservationApiMapperImpl.toCommand(
                id = id,
                userId = xUserId,
                request = createReservationRequest,
                maxPerReservation = properties.maxPerReservation,
                idempotencyKey = idempotencyKey,
            )
        val insertion = createReservationUseCase.create(command = command)
        val response = ReservationApiMapperImpl.toResponse(reservation = insertion.reservation)
        val location = URI.create("/reservations/${insertion.reservation.id}")
        return when (insertion) {
            is ReservationInsertion.Created -> ResponseEntity.created(location).body(response)
            is ReservationInsertion.Replayed -> ResponseEntity.ok().body(response)
        }
    }

    override fun getReservation(
        xUserId: UUID,
        id: UUID,
    ): ResponseEntity<ReservationResponse> =
        getReservationUseCase.getByIdAndUserId(id = id, userId = UserId(value = xUserId))
            .let { ReservationApiMapperImpl.toResponse(reservation = it) }
            .let { ResponseEntity.ok(it) }

    override fun deleteReservation(
        xUserId: UUID,
        id: UUID,
    ): ResponseEntity<Unit> {
        cancelReservationUseCase.cancel(id = id, userId = UserId(value = xUserId))
        return ResponseEntity.noContent().build()
    }
}
