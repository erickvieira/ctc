package dev.erickvieira.flashbooking.adapter.output.cache

import dev.erickvieira.flashbooking.adapter.output.cache.mapper.CachedEventMapperImpl
import dev.erickvieira.flashbooking.config.CacheProperties
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.fixtures.fake
import io.mockk.Runs
import io.mockk.checkUnnecessaryStub
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID

class EventCacheAdapterUnitTest {
	private val valueOps = mockk<ValueOperations<String, String>>()
	private val redisTemplate = mockk<StringRedisTemplate>()
	private val objectMapper: ObjectMapper = JsonMapper.builder().build()
	private val properties = CacheProperties(eventTtl = Duration.ofSeconds(30), eventTtlJitter = Duration.ofSeconds(5))
	private val cache =
		EventCacheAdapter(
			redisTemplate = redisTemplate,
			objectMapper = objectMapper,
			properties = properties,
		)

	@AfterEach
	fun enforceStrictVerification() {
		confirmVerified(redisTemplate, valueOps)
		checkUnnecessaryStub(redisTemplate, valueOps)
	}

	@Nested
	@DisplayName("get")
	inner class Get {
		@Test
		fun `returns the cached event on a hit`() {
			val event = Event.fake()
			val json = objectMapper.writeValueAsString(CachedEventMapperImpl.toCached(event = event))
			every { redisTemplate.opsForValue() } returns valueOps
			every { valueOps.get("event:${event.id}") } returns json

			assertThat(cache.get(eventId = event.id)).usingRecursiveComparison().isEqualTo(event)

			verify(exactly = 1) { redisTemplate.opsForValue() }
			verify(exactly = 1) { valueOps.get("event:${event.id}") }
		}

		@Test
		fun `returns null on a miss`() {
			val id = UUID.randomUUID()
			every { redisTemplate.opsForValue() } returns valueOps
			every { valueOps.get("event:$id") } returns null

			assertThat(cache.get(eventId = id)).isNull()

			verify(exactly = 1) { redisTemplate.opsForValue() }
			verify(exactly = 1) { valueOps.get("event:$id") }
		}

		@Test
		fun `returns null when Redis fails`() {
			val id = UUID.randomUUID()
			every { redisTemplate.opsForValue() } throws RedisConnectionFailureException("down")

			assertThat(cache.get(eventId = id)).isNull()

			verify(exactly = 1) { redisTemplate.opsForValue() }
		}

		@Test
		fun `returns null when the cached payload is malformed`() {
			val id = UUID.randomUUID()
			every { redisTemplate.opsForValue() } returns valueOps
			every { valueOps.get("event:$id") } returns "{not-json"

			assertThat(cache.get(eventId = id)).isNull()

			verify(exactly = 1) { redisTemplate.opsForValue() }
			verify(exactly = 1) { valueOps.get("event:$id") }
		}
	}

	@Nested
	@DisplayName("put")
	inner class Put {
		@Test
		fun `writes the event as json with ttl plus jitter`() {
			val event = Event.fake()
			val json = objectMapper.writeValueAsString(CachedEventMapperImpl.toCached(event = event))
			every { redisTemplate.opsForValue() } returns valueOps
			every {
				valueOps.set(
					"event:${event.id}",
					json,
					match<Duration> { it >= properties.eventTtl && it <= properties.eventTtl.plus(properties.eventTtlJitter) },
				)
			} just Runs

			cache.put(eventId = event.id, event = event)

			verify(exactly = 1) { redisTemplate.opsForValue() }
			verify(exactly = 1) {
				valueOps.set(
					"event:${event.id}",
					json,
					match<Duration> { it >= properties.eventTtl && it <= properties.eventTtl.plus(properties.eventTtlJitter) },
				)
			}
		}

		@Test
		fun `ignores Redis failures`() {
			val event = Event.fake()
			every { redisTemplate.opsForValue() } throws RedisConnectionFailureException("down")

			cache.put(eventId = event.id, event = event)

			verify(exactly = 1) { redisTemplate.opsForValue() }
		}
	}

	@Nested
	@DisplayName("evict")
	inner class Evict {
		@Test
		fun `deletes the cache key`() {
			val id = UUID.randomUUID()
			every { redisTemplate.delete("event:$id") } returns true

			cache.evict(eventId = id)

			verify(exactly = 1) { redisTemplate.delete("event:$id") }
		}

		@Test
		fun `ignores Redis failures`() {
			val id = UUID.randomUUID()
			every { redisTemplate.delete("event:$id") } throws RedisConnectionFailureException("down")

			cache.evict(eventId = id)

			verify(exactly = 1) { redisTemplate.delete("event:$id") }
		}
	}
}
