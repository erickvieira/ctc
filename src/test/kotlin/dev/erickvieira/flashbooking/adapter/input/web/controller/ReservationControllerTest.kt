package dev.erickvieira.flashbooking.adapter.input.web.controller

import dev.erickvieira.flashbooking.adapter.input.web.mapper.ReservationApiMapperImpl
import dev.erickvieira.flashbooking.adapter.input.web.model.CreateReservationRequest
import dev.erickvieira.flashbooking.config.ReservationProperties
import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.Reservation
import dev.erickvieira.flashbooking.domain.model.ReservationInsertion
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.fixtures.fake
import dev.erickvieira.flashbooking.port.input.CreateReservationUseCase
import dev.erickvieira.flashbooking.port.input.GetReservationUseCase
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
import org.springframework.http.HttpStatus
import java.time.Duration
import java.util.UUID

class ReservationControllerTest {
	private val createReservationUseCase = mockk<CreateReservationUseCase>()
	private val getReservationUseCase = mockk<GetReservationUseCase>()
	private val properties = ReservationProperties(ttl = Duration.ofMinutes(10), maxPerReservation = 10)
	private val controller = ReservationController(createReservationUseCase = createReservationUseCase, getReservationUseCase = getReservationUseCase, properties = properties)

	@AfterEach
	fun enforceStrictVerification() {
		confirmVerified(createReservationUseCase, getReservationUseCase)
		checkUnnecessaryStub(createReservationUseCase, getReservationUseCase)
	}

	@Nested
	@DisplayName("createReservation")
	inner class CreateReservation {
		@Test
		fun `maps the request into a command and returns 201 with Location when created`() {
			val eventId = UUID.randomUUID()
			val userId = UserId.fake()
			val reservation = Reservation.fake(eventId = eventId, userId = userId, quantity = Quantity.of(value = 2, max = 10))
			every { createReservationUseCase.create(any()) } returns ReservationInsertion.Created(reservation = reservation)

			val response =
				controller.createReservation(
					xUserId = userId.value,
					id = eventId,
					createReservationRequest = CreateReservationRequest(2),
					idempotencyKey = "key-1",
				)

			assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
			assertThat(response.headers.location.toString()).isEqualTo("/reservations/${reservation.id}")
			assertThat(response.body).usingRecursiveComparison().isEqualTo(ReservationApiMapperImpl.toResponse(reservation = reservation))
			verify(exactly = 1) {
				createReservationUseCase.create(
					command = match { it.eventId == eventId && it.quantity == Quantity.of(value = 2, max = 10) && it.idempotencyKey == "key-1" },
				)
			}
		}

		@Test
		fun `returns 200 without Location when the reservation is replayed`() {
			val eventId = UUID.randomUUID()
			val userId = UserId.fake()
			val reservation = Reservation.fake(eventId = eventId, userId = userId)
			every { createReservationUseCase.create(any()) } returns ReservationInsertion.Replayed(reservation = reservation)

			val response =
				controller.createReservation(
					xUserId = userId.value,
					id = eventId,
					createReservationRequest = CreateReservationRequest(2),
					idempotencyKey = "key-1",
				)

			assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
			assertThat(response.headers.location).isNull()
			assertThat(response.body).usingRecursiveComparison().isEqualTo(ReservationApiMapperImpl.toResponse(reservation = reservation))
			verify(exactly = 1) { createReservationUseCase.create(command = any()) }
		}
	}

	@Nested
	@DisplayName("getReservation")
	inner class GetReservation {
		@Test
		fun `returns 200 with the mapped reservation`() {
			val userId = UserId.fake()
			val reservation = Reservation.fake(userId = userId)
			every { getReservationUseCase.getByIdAndUserId(id = reservation.id, userId = userId) } returns reservation

			val response = controller.getReservation(xUserId = userId.value, id = reservation.id)

			assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
			assertThat(response.body).usingRecursiveComparison().isEqualTo(ReservationApiMapperImpl.toResponse(reservation = reservation))
			verify(exactly = 1) { getReservationUseCase.getByIdAndUserId(id = reservation.id, userId = userId) }
		}
	}
}
