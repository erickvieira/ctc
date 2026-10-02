package dev.erickvieira.flashbooking.adapter.output.cache

import dev.erickvieira.flashbooking.adapter.output.cache.listener.EventCacheEvictionListener
import dev.erickvieira.flashbooking.domain.model.EventAvailabilityChanged
import dev.erickvieira.flashbooking.port.output.EventCachePort
import io.mockk.Runs
import io.mockk.checkUnnecessaryStub
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

class EventCacheEvictionListenerTest {
	private val cache = mockk<EventCachePort>()
	private val listener = EventCacheEvictionListener(eventCache = cache)

	@AfterEach
	fun enforceStrictVerification() {
		confirmVerified(cache)
		checkUnnecessaryStub(cache)
	}

	@Test
	fun `evicts the cached event of the changed event`() {
		val eventId = UUID.randomUUID()
		every { cache.evict(eventId = eventId) } just Runs

		listener.onAvailabilityChanged(EventAvailabilityChanged(eventId = eventId))

		verify(exactly = 1) { cache.evict(eventId = eventId) }
	}
}
