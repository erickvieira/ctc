package dev.erickvieira.flashbooking.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.expiration")
data class ExpirationProperties(
	val batchSize: Int,
)
