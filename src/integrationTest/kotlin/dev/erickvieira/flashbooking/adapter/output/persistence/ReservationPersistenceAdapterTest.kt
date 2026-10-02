package dev.erickvieira.flashbooking.adapter.output.persistence

import dev.erickvieira.flashbooking.domain.exception.IdempotencyConflictException
import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationIdempotency
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.output.EventPersistencePort
import dev.erickvieira.flashbooking.port.output.ReservationPersistencePort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Testcontainers
class ReservationPersistenceAdapterTest {
    companion object {
        const val LOCK_CONTENTION_WAIT_MILLIS = 300L

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16"))
    }

    @Autowired
    private lateinit var eventPersistencePort: EventPersistencePort

    @Autowired
    private lateinit var reservationPersistencePort: ReservationPersistencePort

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun `insertIdempotent without a key and findById round-trip the reservation`() {
        val event = eventPersistencePort.save(event = Event.fake(capacity = Capacity.of(value = 10)))
        val reservation = Reservation.fake(eventId = event.id, quantity = Quantity.of(value = 2, max = 10))

        val insertion = reservationPersistencePort.insertIdempotent(reservation = reservation, idempotency = null)

        assertThat(insertion).isEqualTo(ReservationInsertion.Created(reservation = reservation))
        assertThat(reservationPersistencePort.findByIdAndUserId(id = reservation.id, userId = reservation.userId))
            .usingRecursiveComparison()
            .isEqualTo(reservation)
    }

    @Test
    fun `insertIdempotent replays when the same key and fingerprint are reused`() {
        val event = eventPersistencePort.save(event = Event.fake(capacity = Capacity.of(value = 10)))
        val idempotency = ReservationIdempotency(key = "key-1", fingerprint = UUID.randomUUID())
        val reservation = Reservation.fake(eventId = event.id, quantity = Quantity.of(value = 2, max = 10))

        val first = reservationPersistencePort.insertIdempotent(reservation = reservation, idempotency = idempotency)
        val second = reservationPersistencePort.insertIdempotent(
            reservation = Reservation.fake(
                eventId = event.id,
                userId = reservation.userId,
                quantity = Quantity.of(value = 2, max = 10)
            ),
            idempotency = idempotency,
        )

        assertThat(first).isEqualTo(ReservationInsertion.Created(reservation = reservation))
        assertThat(second).isEqualTo(ReservationInsertion.Replayed(reservation = reservation))
    }

    @Test
    fun `insertIdempotent conflicts when the key is reused with a different fingerprint`() {
        val event = eventPersistencePort.save(event = Event.fake(capacity = Capacity.of(value = 10)))
        val key = "key-1"
        val reservation = Reservation.fake(eventId = event.id, quantity = Quantity.of(value = 2, max = 10))
        reservationPersistencePort.insertIdempotent(
            reservation = reservation,
            idempotency = ReservationIdempotency(key = key, fingerprint = UUID.randomUUID()),
        )

        assertThrows<IdempotencyConflictException> {
            reservationPersistencePort.insertIdempotent(
                reservation = Reservation.fake(
                    eventId = event.id,
                    userId = reservation.userId,
                    quantity = Quantity.of(value = 2, max = 10)
                ),
                idempotency = ReservationIdempotency(key = key, fingerprint = UUID.randomUUID()),
            )
        }
    }

    /**
     * T1 insere a chave (sem commit) e segura; T2 (mesma chave) bloqueia no lock. T1 aborta -> T2
     * precisa ser `Created` (não `Replayed`) e não pular o `tryReserve`.
     */
    @Test
    fun `an aborted concurrent insert still yields Created for a second attempt of the same key`() {
        val event = eventPersistencePort.save(event = Event.fake(capacity = Capacity.of(value = 10)))
        val idempotency = ReservationIdempotency(key = "key-1", fingerprint = UUID.randomUUID())
        val userId = UserId.fake()
        val quantity = Quantity.of(value = 2, max = 10)
        val first = Reservation.fake(eventId = event.id, userId = userId, quantity = quantity)
        val second = Reservation.fake(eventId = event.id, userId = userId, quantity = quantity)

        val firstInserted = CountDownLatch(1)
        val abort = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val firstTx = CompletableFuture.runAsync(
                {
                    transactionTemplate.executeWithoutResult {
                        reservationPersistencePort.insertIdempotent(reservation = first, idempotency = idempotency)
                        firstInserted.countDown()
                        abort.await()
                        throw IllegalStateException("forced rollback")
                    }
                },
                executor,
            )

            assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue()

            val secondTx = CompletableFuture.supplyAsync(
                {
                    reservationPersistencePort.insertIdempotent(reservation = second, idempotency = idempotency)
                },
                executor,
            )

            awaitLockContention()
            abort.countDown()

            val insertion = secondTx.get(10, TimeUnit.SECONDS)
            assertThatThrownBy { firstTx.get(10, TimeUnit.SECONDS) }
                .hasCauseInstanceOf(IllegalStateException::class.java)

            assertThat(insertion).isInstanceOf(ReservationInsertion.Created::class.java)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun awaitLockContention() {
        Thread.sleep(LOCK_CONTENTION_WAIT_MILLIS)
    }
}
