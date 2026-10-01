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
    override fun save(reservation: Reservation): Reservation =
        jpaRepository.save(ReservationPersistenceMapperImpl.toEntity(reservation))
            .let { ReservationPersistenceMapperImpl.toDomain(it) }

    override fun findByIdAndUserId(id: UUID, userId: UserId): Reservation? =
        jpaRepository.findFirstByIdAndUserId(id, userId.value)
            ?.let { ReservationPersistenceMapperImpl.toDomain(it) }
}
