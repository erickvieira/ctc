package dev.erickvieira.flashbooking.adapter.output.persistence.entity

import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.ReservationStatus
import dev.erickvieira.flashbooking.domain.model.UserId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "reservations")
class ReservationEntity(
    @Id val id: UUID,
    @Column(nullable = false) val eventId: UUID,
    @Column(nullable = false) val userId: UserId,
    @Column(nullable = false) val quantity: Quantity,
    @Column(nullable = false) @Enumerated(EnumType.STRING) val status: ReservationStatus,
    @Column(nullable = false) val expiresAt: OffsetDateTime,
    @Column(nullable = false) val createdAt: OffsetDateTime,
    @Column(nullable = false) val updatedAt: OffsetDateTime,
)
