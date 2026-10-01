package dev.erickvieira.flashbooking.adapter.output.persistence.entity

import dev.erickvieira.flashbooking.domain.model.Capacity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.*

@Entity
@Table(name = "events")
class EventEntity(
	@Id val id: UUID,
	@Column(nullable = false) val name: String,
	@Column(nullable = false) val startsAt: OffsetDateTime,
	@Column(nullable = false) val capacity: Capacity,
	@Column(nullable = false) val available: Int,
	@Column(nullable = false) val createdAt: OffsetDateTime,
	@Column(nullable = false) val updatedAt: OffsetDateTime,
)
