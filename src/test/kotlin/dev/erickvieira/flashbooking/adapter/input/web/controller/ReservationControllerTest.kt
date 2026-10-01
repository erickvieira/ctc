package dev.erickvieira.flashbooking.adapter.input.web.controller

import dev.erickvieira.flashbooking.adapter.input.web.mapper.ReservationApiMapperImpl
import dev.erickvieira.flashbooking.adapter.input.web.model.CreateReservationRequest
import dev.erickvieira.flashbooking.config.ReservationProperties
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.input.CreateReservationUseCase
import dev.erickvieira.flashbooking.port.input.GetReservationUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Duration
import java.util.UUID

class ReservationControllerTest {
	private val createReservationUseCase = mockk<CreateReservationUseCase>()
	private val getReservationUseCase = mockk<GetReservationUseCase>()
	private val properties = ReservationProperties(ttl = Duration.ofMinutes(10), maxPerReservation = 10)
	private val controller = ReservationController(createReservationUseCase, getReservationUseCase, properties)

	@Test
	fun `createReservation maps the request into a command and returns 201 with Location`() {
		val eventId = UUID.randomUUID()
		val userId = UserId.fake()
		val reservation = Reservation.fake(eventId = eventId, userId = userId, quantity = Quantity.of(2, 10))
		every { createReservationUseCase.create(any()) } returns reservation

		val response = controller.createReservation(userId.value, eventId, CreateReservationRequest(2))

		assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
		assertThat(response.headers.location.toString()).isEqualTo("/reservations/${reservation.id}")
		assertThat(response.body).usingRecursiveComparison().isEqualTo(ReservationApiMapperImpl.toResponse(reservation))
		verify(exactly = 1) {
			createReservationUseCase.create(match { it.eventId == eventId && it.quantity == Quantity.of(2, 10) })
		}
	}

	@Test
	fun `getReservation returns 200 with the mapped reservation`() {
		val userId = UserId.fake()
		val reservation = Reservation.fake(userId = userId)
		every { getReservationUseCase.getByIdAndUserId(reservation.id, userId) } returns reservation

		val response = controller.getReservation(userId.value, reservation.id)

		assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
		assertThat(response.body).usingRecursiveComparison().isEqualTo(ReservationApiMapperImpl.toResponse(reservation))
	}
}
