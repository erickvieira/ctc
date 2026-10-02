package dev.erickvieira.flashbooking.adapter.output.persistence

import dev.erickvieira.flashbooking.adapter.output.persistence.mapper.ReservationPersistenceMapperImpl
import dev.erickvieira.flashbooking.adapter.output.persistence.repository.ReservationJpaRepository
import dev.erickvieira.flashbooking.domain.exception.IdempotencyConflictException
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationIdempotency
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion
import dev.erickvieira.flashbooking.domain.model.ReservationStatus
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.port.output.ReservationRepository
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class ReservationPersistenceAdapter(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    private val jpaRepository: ReservationJpaRepository,
) : ReservationRepository {
    override fun insertIdempotent(
        reservation: Reservation,
        idempotency: ReservationIdempotency?,
    ): ReservationInsertion {
        val row =
            namedParameterJdbcTemplate
                .query(
                    INSERT_SQL,
                    MapSqlParameterSource()
                        .addValue("id", reservation.id)
                        .addValue("eventId", reservation.eventId)
                        .addValue("userId", reservation.userId.value)
                        .addValue("quantity", reservation.quantity.value)
                        .addValue("status", reservation.status.name)
                        .addValue("idempotencyKey", idempotency?.key)
                        .addValue("fingerprint", idempotency?.fingerprint)
                        .addValue("expiresAt", reservation.expiresAt)
                        .addValue("createdAt", reservation.createdAt)
                        .addValue("updatedAt", reservation.updatedAt),
                    INSERT_ROW_MAPPER,
                ).firstOrNull()
                // 0 linhas só ocorre com chave não-nula: NULL nunca conflita no índice único, então
                // no caminho de conflito `idempotency` nunca é null.
                ?: throw IdempotencyConflictException(idempotencyKey = idempotency?.key)

        return if (row.inserted) {
            ReservationInsertion.Created(reservation = row.reservation)
        } else {
            ReservationInsertion.Replayed(reservation = row.reservation)
        }
    }

    override fun findByIdAndUserId(id: UUID, userId: UserId): Reservation? =
        jpaRepository.findFirstByIdAndUserId(id = id, userId = userId.value)
            ?.let { ReservationPersistenceMapperImpl.toDomain(entity = it) }

    internal data class InsertRow(val reservation: Reservation, val inserted: Boolean)

    companion object {
        // Escopo: a chave de idempotência é única por usuário (`(user_id, idempotency_key)`). O
        // fingerprint inclui o `eventId` e a quantidade, então reusar a mesma chave com qualquer
        // payload diferente (outro evento, outra quantidade) não casa o `WHERE` do `ON CONFLICT` e
        // devolve 0 linhas -> 409 IDEMPOTENCY_CONFLICT.
        //
        // O `DO UPDATE SET updated_at = reservations.updated_at` (no-op de valor) é obrigatório:
        // `DO NOTHING` não devolveria a linha existente no `RETURNING`. O custo é um lock de linha
        // e uma nova versão do tuple a cada retry (replay).
        private val INSERT_SQL =
            """
            INSERT INTO reservations (id, event_id, user_id, quantity, status, idempotency_key, fingerprint, expires_at, created_at, updated_at)
            VALUES (:id, :eventId, :userId, :quantity, :status, :idempotencyKey, :fingerprint, :expiresAt, :createdAt, :updatedAt)
            ON CONFLICT (user_id, idempotency_key) DO UPDATE
               SET updated_at = reservations.updated_at
             WHERE reservations.fingerprint = EXCLUDED.fingerprint
            RETURNING id, event_id, user_id, quantity, status, expires_at, created_at, updated_at, (xmax = 0) AS inserted
            """.trimIndent()

        internal val INSERT_ROW_MAPPER = RowMapper { rs, _ ->
            InsertRow(
                reservation =
                    Reservation(
                        id = rs.getObject("id", UUID::class.java),
                        eventId = rs.getObject("event_id", UUID::class.java),
                        userId = UserId(value = rs.getObject("user_id", UUID::class.java)),
                        quantity = Quantity.fromStorage(value = rs.getInt("quantity")),
                        status = ReservationStatus.valueOf(rs.getString("status")),
                        expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java),
                        createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                        updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
                    ),
                inserted = rs.getBoolean("inserted"),
            )
        }
    }
}
