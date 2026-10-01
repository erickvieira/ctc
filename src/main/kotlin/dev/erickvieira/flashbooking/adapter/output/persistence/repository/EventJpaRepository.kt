package dev.erickvieira.flashbooking.adapter.output.persistence.repository

import dev.erickvieira.flashbooking.adapter.output.persistence.entity.EventEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EventJpaRepository : JpaRepository<EventEntity, UUID>
