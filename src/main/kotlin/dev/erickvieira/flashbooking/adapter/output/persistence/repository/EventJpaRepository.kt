package dev.erickvieira.flashbooking.adapter.output.persistence.repository

import dev.erickvieira.flashbooking.adapter.output.persistence.entity.EventEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime
import java.util.UUID

interface EventJpaRepository : JpaRepository<EventEntity, UUID> {
	@Modifying
	@Query(
		value = """
			UPDATE 	events 
			SET 	available = available - :quantity, 
					updated_at = :now 
			WHERE 	id = :id 
			AND 	available >= :quantity
			""",
		nativeQuery = true,
	)
	fun tryReserve(
		@Param("id") id: UUID,
		@Param("quantity") quantity: Int,
		@Param("now") now: OffsetDateTime,
	): Int
}
