package dev.erickvieira.flashbooking.adapter.input.web.mapper

import dev.erickvieira.flashbooking.adapter.input.web.model.CreateReservationRequest
import dev.erickvieira.flashbooking.domain.command.CreateReservationCommand
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.adapter.input.web.model.Reservation as ReservationResponse
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.UserId
import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import java.util.UUID

@Konverter
interface ReservationApiMapper {
    fun toCommand(
        userId: UUID,
        id: UUID,
        request: CreateReservationRequest,
        maxPerReservation: Int,
    ): CreateReservationCommand =
        CreateReservationCommand(
            eventId = id,
            userId = UserId(value = userId),
            quantity = Quantity.of(value = request.quantity, max = maxPerReservation),
        )

    @Konvert(
        mappings = [
            Mapping(target = "quantity", expression = "it.quantity.value"),
            Mapping(
                target = "status",
                expression = "dev.erickvieira.flashbooking.adapter.input.web.model.ReservationStatus.valueOf(it.status.name)",
            ),
        ],
    )
    fun toResponse(reservation: Reservation): ReservationResponse
}
