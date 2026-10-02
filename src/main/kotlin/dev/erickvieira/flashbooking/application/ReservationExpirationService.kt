package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.config.ExpirationProperties
import dev.erickvieira.flashbooking.domain.model.EventAvailabilityChanged
import dev.erickvieira.flashbooking.port.output.EventPersistencePort
import dev.erickvieira.flashbooking.port.output.ReservationPersistencePort
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

@Service
class ReservationExpirationService(
    private val reservationPersistencePort: ReservationPersistencePort,
    private val eventPersistencePort: EventPersistencePort,
    private val publisher: ApplicationEventPublisher,
    private val properties: ExpirationProperties,
    private val clock: Clock,
) {
    /**
     * Transiciona um lote de reservas Pendentes vencidas para Expirada e devolve os ingressos,
     * agregados por evento. Publica [EventAvailabilityChanged] por evento para invalidar o cache.
     */
    @Scheduled(fixedDelayString = "\${app.expiration.sweep-interval}")
    @Transactional
    fun sweep() {
        val now = OffsetDateTime.now(clock)
        val expired = reservationPersistencePort.expirePending(now = now, limit = properties.batchSize)
        expired
            .groupBy { it.eventId }
            .forEach { (eventId, batch) ->
                logger.info("reservations expired: eventId={} count={}", eventId, batch.size)
                val amount = batch.sumOf { it.quantity.value }
                eventPersistencePort.release(eventId = eventId, amount = amount, now = now)
                publisher.publishEvent(EventAvailabilityChanged(eventId = eventId))
            }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(ReservationExpirationService::class.java)
    }
}
