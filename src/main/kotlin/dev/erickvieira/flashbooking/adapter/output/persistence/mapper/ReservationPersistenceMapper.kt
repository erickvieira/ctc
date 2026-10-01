package dev.erickvieira.flashbooking.adapter.output.persistence.mapper

import dev.erickvieira.flashbooking.adapter.output.persistence.entity.ReservationEntity
import dev.erickvieira.flashbooking.domain.model.Reservation
import io.mcarle.konvert.api.Konverter

@Konverter
interface ReservationPersistenceMapper {
	fun toEntity(reservation: Reservation): ReservationEntity

	fun toDomain(entity: ReservationEntity): Reservation
}
