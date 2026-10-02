package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.config.ExpirationProperties
import dev.erickvieira.flashbooking.port.output.EventRepository
import dev.erickvieira.flashbooking.port.output.ReservationRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

@Service
class ReservationExpirationService(
    private val reservationRepository: ReservationRepository,
    private val eventRepository: EventRepository,
    private val properties: ExpirationProperties,
    private val clock: Clock,
) {
    @Scheduled(fixedDelayString = "\${app.expiration.sweep-interval}")
    @Transactional
    fun sweep() {
        val now = OffsetDateTime.now(clock)
        val expired = reservationRepository.expirePending(now = now, limit = properties.batchSize)
        expired
            .groupBy { it.eventId }
            .forEach { (eventId, batch) ->
                val amount = batch.sumOf { it.quantity.value }
                eventRepository.release(eventId = eventId, amount = amount, now = now)
            }
    }
}
