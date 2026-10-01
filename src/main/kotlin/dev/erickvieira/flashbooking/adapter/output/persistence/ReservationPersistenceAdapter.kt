package dev.erickvieira.flashbooking.adapter.output.persistence

import dev.erickvieira.flashbooking.adapter.output.persistence.mapper.ReservationPersistenceMapperImpl
import dev.erickvieira.flashbooking.adapter.output.persistence.repository.ReservationJpaRepository
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.port.output.ReservationRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ReservationPersistenceAdapter(
    private val jpaRepository: ReservationJpaRepository,
) : ReservationRepository {
    override fun save(reservation: Reservation): Reservation {
        val entity = ReservationPersistenceMapperImpl.toEntity(reservation = reservation)
        val saved = jpaRepository.save(entity)
        return ReservationPersistenceMapperImpl.toDomain(entity = saved)
    }

    override fun findByIdAndUserId(id: UUID, userId: UserId): Reservation? =
        jpaRepository.findFirstByIdAndUserId(id = id, userId = userId.value)
            ?.let { ReservationPersistenceMapperImpl.toDomain(entity = it) }
}
