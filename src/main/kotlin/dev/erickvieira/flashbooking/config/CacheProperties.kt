package dev.erickvieira.flashbooking.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.cache")
data class CacheProperties(
	val eventTtl: Duration,
	val eventTtlJitter: Duration,
)
