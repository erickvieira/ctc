package dev.erickvieira.flashbooking.adapter.input.web.error

import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.exception.EventSoldOutException
import dev.erickvieira.flashbooking.domain.exception.InvalidCapacityException
import dev.erickvieira.flashbooking.domain.exception.InvalidQuantityException
import dev.erickvieira.flashbooking.domain.exception.ReservationNotFoundException
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProblemDetailExceptionHandlerTest {
	private val clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
	private val handler = ProblemDetailExceptionHandler(clock)
	private val request = mockk<HttpServletRequest>()
	private val methodParameter = MethodParameter(String::class.java.getMethod("concat", String::class.java), 0)

	init {
		every { request.requestURI } returns "/test"
	}

	@Test
	fun `maps invalid capacity to 400`() {
		assertProblem(handler.handleInvalidCapacity(InvalidCapacityException(0), request), HttpStatus.BAD_REQUEST, "INVALID_CAPACITY")
	}

	@Test
	fun `maps event not found to 404`() {
		assertProblem(handler.handleEventNotFound(EventNotFoundException(UUID.randomUUID()), request), HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND")
	}

	@Test
	fun `maps reservation not found to 404`() {
		assertProblem(handler.handleReservationNotFound(ReservationNotFoundException(UUID.randomUUID()), request), HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND")
	}

	@Test
	fun `maps invalid quantity to 400`() {
		assertProblem(handler.handleInvalidQuantity(InvalidQuantityException(0, 10), request), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY")
	}

	@Test
	fun `maps event sold out to 409`() {
		assertProblem(handler.handleEventSoldOut(EventSoldOutException(UUID.randomUUID()), request), HttpStatus.CONFLICT, "EVENT_SOLD_OUT")
	}

	@Test
	fun `maps missing header to 400`() {
		assertProblem(handler.handleMissingHeader(MissingRequestHeaderException("X-User-Id", methodParameter), request), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR")
	}

	@Test
	fun `maps bean validation to 400 with field errors`() {
		val bindingResult = BeanPropertyBindingResult("", "target")
		bindingResult.addError(FieldError("target", "name", "must not be null"))
		val exception = MethodArgumentNotValidException(methodParameter, bindingResult)

		val response = handler.handleBeanValidation(exception, request)

		assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
		assertThat(response.body?.detail).isEqualTo("name: must not be null")
		assertThat(response.body?.properties?.get("code")).isEqualTo("VALIDATION_ERROR")
		assertThat(response.body?.properties?.get("errors")).isEqualTo(listOf("name: must not be null"))
	}

	@Test
	fun `maps unreadable body to 400`() {
		assertProblem(
			handler.handleUnreadableBody(HttpMessageNotReadableException("broken", mockk<HttpInputMessage>()), request),
			HttpStatus.BAD_REQUEST,
			"VALIDATION_ERROR",
		)
	}

	@Test
	fun `maps type mismatch to 400`() {
		val exception = MethodArgumentTypeMismatchException("x", String::class.java, "id", methodParameter, null)

		assertProblem(handler.handleTypeMismatch(exception, request), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR")
	}

	@Test
	fun `maps missing resource to 404`() {
		assertProblem(handler.handleNoResource(NoResourceFoundException(HttpMethod.GET, "/x", "not found"), request), HttpStatus.NOT_FOUND, "NOT_FOUND")
	}

	@Test
	fun `maps data integrity violation to 500`() {
		assertProblem(handler.handleDataIntegrity(DataIntegrityViolationException("constraint"), request), HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR")
	}

	@Test
	fun `maps unexpected error to 500`() {
		assertProblem(handler.handleUnexpected(RuntimeException("boom"), request), HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR")
	}

	private fun assertProblem(
		response: org.springframework.http.ResponseEntity<ProblemDetail>,
		status: HttpStatus,
		code: String,
	) {
		assertThat(response.statusCode).isEqualTo(status)
		assertThat(response.body?.title).isEqualTo(code)
		assertThat(response.body?.detail).isNotBlank()
		assertThat(response.body?.properties?.get("code")).isEqualTo(code)
		assertThat(response.body?.properties?.get("timestamp")).isNotNull()
		assertThat(response.body?.type?.toString()).isEqualTo("about:blank")
		assertThat(response.body?.instance?.toString()).isEqualTo("/test")
	}
}
