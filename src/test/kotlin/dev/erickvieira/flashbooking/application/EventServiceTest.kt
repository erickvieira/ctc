package dev.erickvieira.flashbooking.application

import dev.erickvieira.flashbooking.domain.command.CreateEventCommand
import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.model.Capacity
import dev.erickvieira.flashbooking.domain.model.Event
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.output.EventPersistencePort
import dev.erickvieira.flashbooking.port.output.EventCachePort
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
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class EventServiceTest {
	private val fixedInstant = Instant.parse("2026-01-01T12:00:00Z")
	private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
	private val repository = mockk<EventPersistencePort>()
	private val cache = mockk<EventCachePort>()
	private val service = EventService(eventPersistencePort = repository, eventCache = cache, clock = clock)

	@AfterEach
	fun enforceStrictVerification() {
		confirmVerified(repository, cache)
		checkUnnecessaryStub(repository, cache)
	}

	@Nested
	@DisplayName("create")
	inner class Create {
		@Test
		fun `persists the event with full availability and fixed timestamps`() {
			every { repository.save(event = any()) } answers { firstArg() }
			val command = CreateEventCommand.fake(name = "Show da Banda X", capacity = Capacity.of(value = 100))

			val created = service.create(command = command)

			val expected = Event.fake(name = "Show da Banda X", capacity = Capacity.of(value = 100))

			assertThat(created).usingRecursiveComparison().ignoringFields("id").isEqualTo(expected)
			assertThat(created.id).isNotNull()
			verify(exactly = 1) {
				repository.save(
					event = match { it.name == "Show da Banda X" && it.available == 100 && it.capacity == Capacity.of(value = 100) },
				)
			}
		}
	}

	@Nested
	@DisplayName("getById")
	inner class GetById {
		@Test
		fun `returns the cached event on a hit`() {
			val event = Event.fake()
			every { cache.get(eventId = event.id) } returns event

			assertThat(service.getById(id = event.id)).usingRecursiveComparison().isEqualTo(event)

			verify(exactly = 1) { cache.get(eventId = event.id) }
			verify(exactly = 0) { repository.findById(id = any()) }
			verify(exactly = 0) { cache.put(eventId = any(), event = any()) }
		}

		@Test
		fun `loads from the repository and caches on a miss`() {
			val event = Event.fake()
			every { cache.get(eventId = event.id) } returns null
			every { repository.findById(id = event.id) } returns event
			every { cache.put(eventId = event.id, event = event) } just Runs

			assertThat(service.getById(id = event.id)).usingRecursiveComparison().isEqualTo(event)

			verify(exactly = 1) { cache.get(eventId = event.id) }
			verify(exactly = 1) { repository.findById(id = event.id) }
			verify(exactly = 1) { cache.put(eventId = event.id, event = event) }
		}

		@Test
		fun `throws when the event does not exist`() {
			val id = UUID.randomUUID()
			every { cache.get(eventId = id) } returns null
			every { repository.findById(id = id) } returns null

			assertThrows<EventNotFoundException> { service.getById(id = id) }

			verify(exactly = 1) { cache.get(eventId = id) }
			verify(exactly = 1) { repository.findById(id = id) }
			verify(exactly = 0) { cache.put(eventId = any(), event = any()) }
		}
	}
}
