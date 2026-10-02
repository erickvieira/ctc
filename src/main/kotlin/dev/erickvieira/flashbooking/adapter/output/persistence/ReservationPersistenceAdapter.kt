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
import dev.erickvieira.flashbooking.port.output.ReservationPersistencePort
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class ReservationPersistenceAdapter(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    private val jpaRepository: ReservationJpaRepository,
) : ReservationPersistencePort {
    /**
     * Upsert idempotente. 0 linhas só com chave não-nula (NULL nunca conflita no índice único) ->
     * conflito de payload.
     *
     * @param reservation reserva a inserir.
     * @param idempotency chave + fingerprint; null = sem idempotência.
     * @return `Created` no insert novo; `Replayed` quando a chave repete o payload.
     * @throws IdempotencyConflictException chave reusada com payload diferente.
     */
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

    override fun confirmIfPending(
        id: UUID,
        userId: UserId,
        now: OffsetDateTime,
    ): Reservation? =
        namedParameterJdbcTemplate
            .query(
                CONFIRM_SQL,
                MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("userId", userId.value)
                    .addValue("now", now),
                RESERVATION_ROW_MAPPER,
            ).firstOrNull()

    override fun cancelIfCancellable(
        id: UUID,
        userId: UserId,
        now: OffsetDateTime,
    ): Reservation? =
        namedParameterJdbcTemplate
            .query(
                CANCEL_SQL,
                MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("userId", userId.value)
                    .addValue("now", now),
                RESERVATION_ROW_MAPPER,
            ).firstOrNull()

    override fun expirePending(
        now: OffsetDateTime,
        limit: Int,
    ): List<Reservation> =
        namedParameterJdbcTemplate
            .query(
                EXPIRE_SQL,
                MapSqlParameterSource()
                    .addValue("now", now)
                    .addValue("limit", limit),
                RESERVATION_ROW_MAPPER,
            )

    internal data class InsertRow(val reservation: Reservation, val inserted: Boolean)

    companion object {
        private const val RESERVATION_COLUMNS =
            "id, event_id, user_id, quantity, status, expires_at, created_at, updated_at"

        /**
         * Upsert idempotente. Chave única por usuário `(user_id, idempotency_key)`; fingerprint inclui
         * `eventId` + quantidade -> payload diferente não casa o `WHERE` -> 0 linhas -> 409.
         *
         * `DO UPDATE SET updated_at = reservations.updated_at` (no-op) é obrigatório: `DO NOTHING`
         * não devolveria a linha no `RETURNING`. Custo: lock de linha + versão nova por retry.
         */
        private val INSERT_SQL =
            """
            INSERT INTO reservations (id, event_id, user_id, quantity, status, idempotency_key, fingerprint, expires_at, created_at, updated_at)
            VALUES (:id, :eventId, :userId, :quantity, :status, :idempotencyKey, :fingerprint, :expiresAt, :createdAt, :updatedAt)
            ON CONFLICT (user_id, idempotency_key) DO UPDATE
               SET updated_at = reservations.updated_at
             WHERE reservations.fingerprint = EXCLUDED.fingerprint
            RETURNING $RESERVATION_COLUMNS, (xmax = 0) AS inserted
            """.trimIndent()

        /**
         * Transição condicional `PENDING -> CONFIRMED`. Devolve <=1 linha; já Confirmada não transiciona.
         */
        private val CONFIRM_SQL =
            """
            UPDATE reservations
               SET status = 'CONFIRMED', updated_at = :now
             WHERE id = :id AND user_id = :userId AND status = 'PENDING'
            RETURNING $RESERVATION_COLUMNS
            """.trimIndent()

        /**
         * Transição condicional `{PENDING, CONFIRMED} -> CANCELLED`. Devolve <=1 linha -> DELETEs
         * repetidos devolvem ingressos só uma vez.
         */
        private val CANCEL_SQL =
            """
            UPDATE reservations
               SET status = 'CANCELLED', updated_at = :now
             WHERE id = :id AND user_id = :userId AND status IN ('PENDING', 'CONFIRMED')
            RETURNING $RESERVATION_COLUMNS
            """.trimIndent()

        /**
         * Transição em lote `PENDING -> EXPIRED` com `FOR UPDATE SKIP LOCKED` (seguro entre
         * instâncias) -> devolve as transicionadas.
         */
        private val EXPIRE_SQL =
            """
            UPDATE reservations
               SET status = 'EXPIRED', updated_at = :now
             WHERE id IN (
                 SELECT id FROM reservations
                  WHERE status = 'PENDING' AND expires_at <= :now
                  ORDER BY expires_at
                  FOR UPDATE SKIP LOCKED
                  LIMIT :limit
             )
            RETURNING $RESERVATION_COLUMNS
            """.trimIndent()

        internal val RESERVATION_ROW_MAPPER = RowMapper { rs, _ -> toReservation(rs) }

        internal val INSERT_ROW_MAPPER = RowMapper { rs, _ ->
            InsertRow(reservation = toReservation(rs), inserted = rs.getBoolean("inserted"))
        }

        private fun toReservation(rs: ResultSet): Reservation =
            Reservation(
                id = rs.getObject("id", UUID::class.java),
                eventId = rs.getObject("event_id", UUID::class.java),
                userId = UserId(value = rs.getObject("user_id", UUID::class.java)),
                quantity = Quantity.fromStorage(value = rs.getInt("quantity")),
                status = ReservationStatus.valueOf(rs.getString("status")),
                expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
                updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java),
            )
    }
}
