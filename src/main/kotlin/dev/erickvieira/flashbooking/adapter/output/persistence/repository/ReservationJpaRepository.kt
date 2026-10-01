package dev.erickvieira.flashbooking.adapter.output.persistence.repository

import dev.erickvieira.flashbooking.adapter.output.persistence.entity.ReservationEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReservationJpaRepository : JpaRepository<ReservationEntity, UUID> {
    fun findFirstByIdAndUserId(id: UUID, userId: UUID): ReservationEntity?
}
