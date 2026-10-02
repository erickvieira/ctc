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
import dev.erickvieira.flashbooking.fixtures.fake
import io.mockk.checkUnnecessaryStub
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.SqlParameterSource
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

class ReservationPersistenceAdapterUnitTest {
    private val fixedNow = OffsetDateTime.parse("2026-01-01T12:00:00Z")
    private val namedParameterJdbcTemplate = mockk<NamedParameterJdbcTemplate>()
    private val jpaRepository = mockk<ReservationJpaRepository>()
    private val adapter =
        ReservationPersistenceAdapter(
            namedParameterJdbcTemplate = namedParameterJdbcTemplate,
            jpaRepository = jpaRepository,
        )

    @AfterEach
    fun enforceStrictVerification() {
        confirmVerified(namedParameterJdbcTemplate, jpaRepository)
        checkUnnecessaryStub(namedParameterJdbcTemplate, jpaRepository)
    }

    @Nested
    @DisplayName("insertIdempotent")
    inner class InsertIdempotent {
        @Test
        fun `returns Created when the row was inserted`() {
            val reservation = Reservation.fake()
            val idempotency = ReservationIdempotency(key = "key-1", fingerprint = UUID.randomUUID())
            val row = ReservationPersistenceAdapter.InsertRow(reservation = reservation, inserted = true)
            every {
                namedParameterJdbcTemplate.query(
                    any(),
                    any<SqlParameterSource>(),
                    any<RowMapper<ReservationPersistenceAdapter.InsertRow>>(),
                )
            } returns listOf(row)

            val result = adapter.insertIdempotent(reservation = reservation, idempotency = idempotency)

            assertThat(result).isEqualTo(ReservationInsertion.Created(reservation = reservation))
            verify(exactly = 1) {
                namedParameterJdbcTemplate.query(
                    any(),
                    match<SqlParameterSource> { it.getValue("idempotencyKey") == "key-1" && it.getValue("fingerprint") == idempotency.fingerprint },
                    any<RowMapper<ReservationPersistenceAdapter.InsertRow>>(),
                )
            }
        }

        @Test
        fun `returns Replayed when the row already existed`() {
            val reservation = Reservation.fake()
            val idempotency = ReservationIdempotency(key = "key-1", fingerprint = UUID.randomUUID())
            val row = ReservationPersistenceAdapter.InsertRow(reservation = reservation, inserted = false)
            every {
                namedParameterJdbcTemplate.query(
                    any(),
                    any<SqlParameterSource>(),
                    any<RowMapper<ReservationPersistenceAdapter.InsertRow>>(),
                )
            } returns listOf(row)

            val result = adapter.insertIdempotent(reservation = reservation, idempotency = idempotency)

            assertThat(result).isEqualTo(ReservationInsertion.Replayed(reservation = reservation))
            verify(exactly = 1) {
                namedParameterJdbcTemplate.query(
                    any(),
                    any<SqlParameterSource>(),
                    any<RowMapper<ReservationPersistenceAdapter.InsertRow>>(),
                )
            }
        }

        @Test
        fun `throws when the key was reused with a different payload`() {
            val reservation = Reservation.fake()
            val idempotency = ReservationIdempotency(key = "key-1", fingerprint = UUID.randomUUID())
            every {
                namedParameterJdbcTemplate.query(
                    any(),
                    any<SqlParameterSource>(),
                    any<RowMapper<ReservationPersistenceAdapter.InsertRow>>(),
                )
            } returns emptyList()

            val exception =
                assertThrows<IdempotencyConflictException> {
                    adapter.insertIdempotent(reservation = reservation, idempotency = idempotency)
                }

            assertThat(exception.idempotencyKey).isEqualTo("key-1")
            verify(exactly = 1) {
                namedParameterJdbcTemplate.query(
                    any(),
                    any<SqlParameterSource>(),
                    any<RowMapper<ReservationPersistenceAdapter.InsertRow>>(),
                )
            }
        }

        @Test
        fun `inserts without a key when idempotency is null`() {
            val reservation = Reservation.fake()
            val row = ReservationPersistenceAdapter.InsertRow(reservation = reservation, inserted = true)
            every {
                namedParameterJdbcTemplate.query(
                    any(),
                    any<SqlParameterSource>(),
                    any<RowMapper<ReservationPersistenceAdapter.InsertRow>>(),
                )
            } returns listOf(row)

            val result = adapter.insertIdempotent(reservation = reservation, idempotency = null)

            assertThat(result).isEqualTo(ReservationInsertion.Created(reservation = reservation))
            verify(exactly = 1) {
                namedParameterJdbcTemplate.query(
                    any(),
                    match<SqlParameterSource> { it.getValue("idempotencyKey") == null && it.getValue("fingerprint") == null },
                    any<RowMapper<ReservationPersistenceAdapter.InsertRow>>(),
                )
            }
        }
    }

    @Nested
    @DisplayName("cancelIfPending")
    inner class CancelIfPending {
        @Test
        fun `returns the cancelled reservation when the transition happens`() {
            val reservation = Reservation.fake(status = ReservationStatus.CANCELLED)
            every {
                namedParameterJdbcTemplate.query(any(), any<SqlParameterSource>(), any<RowMapper<Reservation>>())
            } returns listOf(reservation)

            val result = adapter.cancelIfPending(id = reservation.id, userId = reservation.userId, now = fixedNow)

            assertThat(result).usingRecursiveComparison().isEqualTo(reservation)
            verify(exactly = 1) {
                namedParameterJdbcTemplate.query(any(), any<SqlParameterSource>(), any<RowMapper<Reservation>>())
            }
        }

        @Test
        fun `returns null when no row transitions`() {
            every {
                namedParameterJdbcTemplate.query(any(), any<SqlParameterSource>(), any<RowMapper<Reservation>>())
            } returns emptyList()

            assertThat(adapter.cancelIfPending(id = UUID.randomUUID(), userId = UserId.fake(), now = fixedNow)).isNull()
            verify(exactly = 1) {
                namedParameterJdbcTemplate.query(any(), any<SqlParameterSource>(), any<RowMapper<Reservation>>())
            }
        }
    }

    @Nested
    @DisplayName("expirePending")
    inner class ExpirePending {
        @Test
        fun `returns the expired reservations`() {
            val first = Reservation.fake(status = ReservationStatus.EXPIRED)
            val second = Reservation.fake(status = ReservationStatus.EXPIRED)
            every {
                namedParameterJdbcTemplate.query(any(), any<SqlParameterSource>(), any<RowMapper<Reservation>>())
            } returns listOf(first, second)

            val result = adapter.expirePending(now = fixedNow, limit = 100)

            assertThat(result).usingRecursiveComparison().isEqualTo(listOf(first, second))
            verify(exactly = 1) {
                namedParameterJdbcTemplate.query(any(), any<SqlParameterSource>(), any<RowMapper<Reservation>>())
            }
        }
    }

    @Nested
    @DisplayName("findByIdAndUserId")
    inner class FindByIdAndUserId {
        @Test
        fun `returns the mapped reservation when present`() {
            val reservation = Reservation.fake()
            val entity = ReservationPersistenceMapperImpl.toEntity(reservation = reservation)
            every { jpaRepository.findFirstByIdAndUserId(id = reservation.id, userId = reservation.userId.value) } returns entity

            assertThat(adapter.findByIdAndUserId(id = reservation.id, userId = reservation.userId))
                .usingRecursiveComparison()
                .isEqualTo(reservation)
            verify(exactly = 1) { jpaRepository.findFirstByIdAndUserId(id = reservation.id, userId = reservation.userId.value) }
        }

        @Test
        fun `returns null when absent`() {
            val id = UUID.randomUUID()
            val userId = UserId.fake()
            every { jpaRepository.findFirstByIdAndUserId(id = id, userId = userId.value) } returns null

            assertThat(adapter.findByIdAndUserId(id = id, userId = userId)).isNull()
            verify(exactly = 1) { jpaRepository.findFirstByIdAndUserId(id = id, userId = userId.value) }
        }
    }

    @Nested
    @DisplayName("rowMapper")
    inner class RowMapperTest {
        @Test
        fun `INSERT_ROW_MAPPER maps a result set into a reservation and the inserted flag`() {
            val id = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val rs = mockk<ResultSet>()
            every { rs.getObject("id", UUID::class.java) } returns id
            every { rs.getObject("event_id", UUID::class.java) } returns eventId
            every { rs.getObject("user_id", UUID::class.java) } returns userId
            every { rs.getInt("quantity") } returns 3
            every { rs.getString("status") } returns "PENDING"
            every { rs.getObject("expires_at", OffsetDateTime::class.java) } returns fixedNow
            every { rs.getObject("created_at", OffsetDateTime::class.java) } returns fixedNow
            every { rs.getObject("updated_at", OffsetDateTime::class.java) } returns fixedNow
            every { rs.getBoolean("inserted") } returns true

            val row = ReservationPersistenceAdapter.INSERT_ROW_MAPPER.mapRow(rs, 0)

            val expected =
                ReservationPersistenceAdapter.InsertRow(
                    reservation =
                        Reservation.fake(
                            id = id,
                            eventId = eventId,
                            userId = UserId(value = userId),
                            quantity = Quantity.fromStorage(value = 3),
                            status = ReservationStatus.PENDING,
                            expiresAt = fixedNow,
                            createdAt = fixedNow,
                            updatedAt = fixedNow,
                        ),
                    inserted = true,
                )
            assertThat(row).usingRecursiveComparison().isEqualTo(expected)
        }

        @Test
        fun `RESERVATION_ROW_MAPPER maps a result set into a reservation`() {
            val id = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val rs = mockk<ResultSet>()
            every { rs.getObject("id", UUID::class.java) } returns id
            every { rs.getObject("event_id", UUID::class.java) } returns eventId
            every { rs.getObject("user_id", UUID::class.java) } returns userId
            every { rs.getInt("quantity") } returns 3
            every { rs.getString("status") } returns "EXPIRED"
            every { rs.getObject("expires_at", OffsetDateTime::class.java) } returns fixedNow
            every { rs.getObject("created_at", OffsetDateTime::class.java) } returns fixedNow
            every { rs.getObject("updated_at", OffsetDateTime::class.java) } returns fixedNow

            val row = ReservationPersistenceAdapter.RESERVATION_ROW_MAPPER.mapRow(rs, 0)

            val expected =
                Reservation.fake(
                    id = id,
                    eventId = eventId,
                    userId = UserId(value = userId),
                    quantity = Quantity.fromStorage(value = 3),
                    status = ReservationStatus.EXPIRED,
                    expiresAt = fixedNow,
                    createdAt = fixedNow,
                    updatedAt = fixedNow,
                )
            assertThat(row).usingRecursiveComparison().isEqualTo(expected)
        }
    }
}
