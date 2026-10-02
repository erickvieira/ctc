package dev.erickvieira.flashbooking.adapter.output.persistence

import dev.erickvieira.flashbooking.adapter.output.persistence.mapper.EventPersistenceMapperImpl
import dev.erickvieira.flashbooking.adapter.output.persistence.repository.EventJpaRepository
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.fixtures.fake
import io.mockk.checkUnnecessaryStub
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

class EventPersistenceAdapterUnitTest {
	private val jpaRepository = mockk<EventJpaRepository>()
	private val adapter = EventPersistenceAdapter(jpaRepository = jpaRepository)

	@AfterEach
	fun enforceStrictVerification() {
		confirmVerified(jpaRepository)
		checkUnnecessaryStub(jpaRepository)
	}

	@Nested
	@DisplayName("save")
	inner class Save {
		@Test
		fun `round-trips through the mapper`() {
			val event = Event.fake()
			every { jpaRepository.save(any()) } answers { firstArg() }

			assertThat(adapter.save(event = event)).usingRecursiveComparison().isEqualTo(event)
			verify(exactly = 1) { jpaRepository.save(any()) }
		}
	}

	@Nested
	@DisplayName("findById")
	inner class FindById {
		@Test
		fun `returns the mapped event when present`() {
			val event = Event.fake()
			val entity = EventPersistenceMapperImpl.toEntity(event = event)
			every { jpaRepository.findById(event.id) } returns Optional.of(entity)

			assertThat(adapter.findById(id = event.id)).usingRecursiveComparison().isEqualTo(event)
			verify(exactly = 1) { jpaRepository.findById(event.id) }
		}

		@Test
		fun `returns null when absent`() {
			val id = UUID.randomUUID()
			every { jpaRepository.findById(id) } returns Optional.empty()

			assertThat(adapter.findById(id = id)).isNull()
			verify(exactly = 1) { jpaRepository.findById(id) }
		}
	}

	@Nested
	@DisplayName("tryReserve")
	inner class TryReserve {
		@Test
		fun `is true when the update affects rows`() {
			val eventId = UUID.randomUUID()
			val quantity = Quantity.fake(value = 2)
			val now = OffsetDateTime.parse("2026-01-01T12:00:00Z")
			every { jpaRepository.tryReserve(id = eventId, quantity = 2, now = now) } returns 1

			assertThat(adapter.tryReserve(eventId = eventId, quantity = quantity, now = now)).isTrue()
			verify(exactly = 1) { jpaRepository.tryReserve(id = eventId, quantity = 2, now = now) }
		}

		@Test
		fun `is false when the update affects no rows`() {
			val eventId = UUID.randomUUID()
			val quantity = Quantity.fake(value = 2)
			val now = OffsetDateTime.parse("2026-01-01T12:00:00Z")
			every { jpaRepository.tryReserve(id = eventId, quantity = 2, now = now) } returns 0

			assertThat(adapter.tryReserve(eventId = eventId, quantity = quantity, now = now)).isFalse()
			verify(exactly = 1) { jpaRepository.tryReserve(id = eventId, quantity = 2, now = now) }
		}
	}
}
