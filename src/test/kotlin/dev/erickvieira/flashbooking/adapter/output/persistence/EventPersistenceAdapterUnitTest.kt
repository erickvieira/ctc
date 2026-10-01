package dev.erickvieira.flashbooking.adapter.output.persistence

import dev.erickvieira.flashbooking.adapter.output.persistence.mapper.EventPersistenceMapperImpl
import dev.erickvieira.flashbooking.adapter.output.persistence.repository.EventJpaRepository
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.fixtures.fake
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

class EventPersistenceAdapterUnitTest {
	private val jpaRepository = mockk<EventJpaRepository>()
	private val adapter = EventPersistenceAdapter(jpaRepository)

	@Test
	fun `save round-trips through the mapper`() {
		val event = Event.fake()
		every { jpaRepository.save(any()) } answers { firstArg() }

		assertThat(adapter.save(event)).usingRecursiveComparison().isEqualTo(event)
		verify(exactly = 1) { jpaRepository.save(any()) }
	}

	@Test
	fun `findById returns the mapped event when present`() {
		val event = Event.fake()
		val entity = EventPersistenceMapperImpl.toEntity(event)
		every { jpaRepository.findById(event.id) } returns Optional.of(entity)

		assertThat(adapter.findById(event.id)).usingRecursiveComparison().isEqualTo(event)
	}

	@Test
	fun `findById returns null when absent`() {
		val id = UUID.randomUUID()
		every { jpaRepository.findById(id) } returns Optional.empty()

		assertThat(adapter.findById(id)).isNull()
	}

	@Test
	fun `tryReserve is true when the update affects rows`() {
		val eventId = UUID.randomUUID()
		val quantity = Quantity.fake(value = 2)
		val now = OffsetDateTime.parse("2026-01-01T12:00:00Z")
		every { jpaRepository.tryReserve(eventId, 2, now) } returns 1

		assertThat(adapter.tryReserve(eventId, quantity, now)).isTrue()
	}

	@Test
	fun `tryReserve is false when the update affects no rows`() {
		val eventId = UUID.randomUUID()
		val quantity = Quantity.fake(value = 2)
		val now = OffsetDateTime.parse("2026-01-01T12:00:00Z")
		every { jpaRepository.tryReserve(eventId, 2, now) } returns 0

		assertThat(adapter.tryReserve(eventId, quantity, now)).isFalse()
	}
}
