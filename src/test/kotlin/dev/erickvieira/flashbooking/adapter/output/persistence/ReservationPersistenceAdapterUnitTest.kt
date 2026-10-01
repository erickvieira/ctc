package dev.erickvieira.flashbooking.adapter.output.persistence

import dev.erickvieira.flashbooking.adapter.output.persistence.mapper.ReservationPersistenceMapperImpl
import dev.erickvieira.flashbooking.adapter.output.persistence.repository.ReservationJpaRepository
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.fixtures.fake
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class ReservationPersistenceAdapterUnitTest {
	private val jpaRepository = mockk<ReservationJpaRepository>()
	private val adapter = ReservationPersistenceAdapter(jpaRepository)

	@Test
	fun `save round-trips through the mapper`() {
		val reservation = Reservation.fake()
		every { jpaRepository.save(any()) } answers { firstArg() }

		assertThat(adapter.save(reservation)).usingRecursiveComparison().isEqualTo(reservation)
		verify(exactly = 1) { jpaRepository.save(any()) }
	}

	@Test
	fun `findByIdAndUserId returns the mapped reservation when present`() {
		val reservation = Reservation.fake()
		val entity = ReservationPersistenceMapperImpl.toEntity(reservation)
		every { jpaRepository.findFirstByIdAndUserId(reservation.id, reservation.userId.value) } returns entity

		assertThat(adapter.findByIdAndUserId(reservation.id, reservation.userId))
			.usingRecursiveComparison()
			.isEqualTo(reservation)
	}

	@Test
	fun `findByIdAndUserId returns null when absent`() {
		val id = UUID.randomUUID()
		val userId = UserId.fake()
		every { jpaRepository.findFirstByIdAndUserId(id, userId.value) } returns null

		assertThat(adapter.findByIdAndUserId(id, userId)).isNull()
	}
}
