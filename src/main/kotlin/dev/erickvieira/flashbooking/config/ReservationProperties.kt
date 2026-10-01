package dev.erickvieira.flashbooking.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.reservation")
data class ReservationProperties(
	val ttl: Duration,
	val maxPerReservation: Int,
)
