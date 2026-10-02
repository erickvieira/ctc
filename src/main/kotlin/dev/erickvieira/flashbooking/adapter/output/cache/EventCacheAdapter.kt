package dev.erickvieira.flashbooking.adapter.output.cache

import dev.erickvieira.flashbooking.adapter.output.cache.dto.CachedEvent
import dev.erickvieira.flashbooking.adapter.output.cache.mapper.CachedEventMapperImpl
import dev.erickvieira.flashbooking.config.CacheProperties
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.port.output.EventCachePort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.util.*
import kotlin.random.Random

/**
 * Cache-aside do evento em `event:{id}` (JSON, TTL + jitter). Best-effort: Redis fora
 * do ar vira miss/ignorado — o DB segue como fonte de verdade.
 */
@Component
class EventCacheAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val properties: CacheProperties,
) : EventCachePort {
    override fun get(eventId: UUID): Event? = read(key(eventId))?.let { json ->
        runCatching {
            val cached = objectMapper.readValue<CachedEvent>(json)
            CachedEventMapperImpl.toDomain(cached = cached)
        }.getOrNull()
    }

    override fun put(eventId: UUID, event: Event) {
        val json = runCatching {
            val cached = CachedEventMapperImpl.toCached(event = event)
            objectMapper.writeValueAsString(cached)
        }.getOrNull()
        if (json != null) {
            write(key(eventId), json, ttlWithJitter())
        }
    }

    override fun evict(eventId: UUID) {
        runCatching { redisTemplate.delete(key(eventId)) }
    }

    private fun read(key: String): String? = runCatching { redisTemplate.opsForValue().get(key) }.getOrNull()

    private fun write(key: String, value: String, ttl: Duration) {
        runCatching { redisTemplate.opsForValue().set(key, value, ttl) }
    }

    private fun key(eventId: UUID): String = "event:$eventId"

    private fun ttlWithJitter(): Duration =
        properties.eventTtl.plusSeconds(Random.nextLong(properties.eventTtlJitter.seconds))
}
