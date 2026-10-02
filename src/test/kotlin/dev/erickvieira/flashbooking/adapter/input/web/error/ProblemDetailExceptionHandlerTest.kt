package dev.erickvieira.flashbooking.adapter.input.web.error

import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.exception.EventSoldOutException
import dev.erickvieira.flashbooking.domain.exception.IdempotencyConflictException
import dev.erickvieira.flashbooking.domain.exception.InvalidCapacityException
import dev.erickvieira.flashbooking.domain.exception.InvalidQuantityException
import dev.erickvieira.flashbooking.domain.exception.ReservationNotFoundException
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ProblemDetailExceptionHandlerTest {
	private val clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
	private val handler = ProblemDetailExceptionHandler(clock = clock)
	private val request = mockk<HttpServletRequest>()
	private val methodParameter = MethodParameter(String::class.java.getMethod("concat", String::class.java), 0)

	init {
		every { request.requestURI } returns "/test"
	}

	@Nested
	@DisplayName("handleInvalidCapacity")
	inner class HandleInvalidCapacity {
		@Test
		fun `maps to 400 INVALID_CAPACITY`() {
			val exception = InvalidCapacityException(value = 0)

			assertProblem(
				handler.handleInvalidCapacity(exception = exception, request = request),
				HttpStatus.BAD_REQUEST,
				"INVALID_CAPACITY",
				exception.message.orEmpty(),
			)
		}
	}

	@Nested
	@DisplayName("handleEventNotFound")
	inner class HandleEventNotFound {
		@Test
		fun `maps to 404 EVENT_NOT_FOUND`() {
			val exception = EventNotFoundException(id = UUID.randomUUID())

			assertProblem(
				handler.handleEventNotFound(exception = exception, request = request),
				HttpStatus.NOT_FOUND,
				"EVENT_NOT_FOUND",
				exception.message.orEmpty(),
			)
		}
	}

	@Nested
	@DisplayName("handleReservationNotFound")
	inner class HandleReservationNotFound {
		@Test
		fun `maps to 404 RESERVATION_NOT_FOUND`() {
			val exception = ReservationNotFoundException(id = UUID.randomUUID())

			assertProblem(
				handler.handleReservationNotFound(exception = exception, request = request),
				HttpStatus.NOT_FOUND,
				"RESERVATION_NOT_FOUND",
				exception.message.orEmpty(),
			)
		}
	}

	@Nested
	@DisplayName("handleInvalidQuantity")
	inner class HandleInvalidQuantity {
		@Test
		fun `maps to 400 INVALID_QUANTITY`() {
			val exception = InvalidQuantityException(value = 0, max = 10)

			assertProblem(
				handler.handleInvalidQuantity(exception = exception, request = request),
				HttpStatus.BAD_REQUEST,
				"INVALID_QUANTITY",
				exception.message.orEmpty(),
			)
		}
	}

	@Nested
	@DisplayName("handleEventSoldOut")
	inner class HandleEventSoldOut {
		@Test
		fun `maps to 409 EVENT_SOLD_OUT`() {
			val exception = EventSoldOutException(eventId = UUID.randomUUID())

			assertProblem(
				handler.handleEventSoldOut(exception = exception, request = request),
				HttpStatus.CONFLICT,
				"EVENT_SOLD_OUT",
				exception.message.orEmpty(),
			)
		}
	}

	@Nested
	@DisplayName("handleIdempotencyConflict")
	inner class HandleIdempotencyConflict {
		@Test
		fun `maps to 409 IDEMPOTENCY_CONFLICT`() {
			val exception = IdempotencyConflictException(idempotencyKey = "key-1")

			assertProblem(
				handler.handleIdempotencyConflict(exception = exception, request = request),
				HttpStatus.CONFLICT,
				"IDEMPOTENCY_CONFLICT",
				exception.message.orEmpty(),
			)
		}
	}

	@Nested
	@DisplayName("handleMissingHeader")
	inner class HandleMissingHeader {
		@Test
		fun `maps to 400 VALIDATION_ERROR`() {
			val exception = MissingRequestHeaderException("X-User-Id", methodParameter)

			assertProblem(
				handler.handleMissingHeader(exception = exception, request = request),
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"missing required header '${exception.headerName}'",
			)
		}
	}

	@Nested
	@DisplayName("handleBeanValidation")
	inner class HandleBeanValidation {
		@Test
		fun `maps to 400 with field errors`() {
			val bindingResult = BeanPropertyBindingResult("", "target")
			bindingResult.addError(FieldError("target", "name", "must not be null"))
			val exception = MethodArgumentNotValidException(methodParameter, bindingResult)

			val response = handler.handleBeanValidation(exception = exception, request = request)

			assertProblem(
				response,
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"name: must not be null",
				listOf("name: must not be null"),
			)
		}
	}

	@Nested
	@DisplayName("handleUnreadableBody")
	inner class HandleUnreadableBody {
		@Test
		fun `maps to 400 VALIDATION_ERROR`() {
			assertProblem(
				handler.handleUnreadableBody(exception = HttpMessageNotReadableException("broken", mockk<HttpInputMessage>()), request = request),
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"malformed request body",
			)
		}
	}

	@Nested
	@DisplayName("handleTypeMismatch")
	inner class HandleTypeMismatch {
		@Test
		fun `maps to 400 VALIDATION_ERROR`() {
			val exception = MethodArgumentTypeMismatchException("x", String::class.java, "id", methodParameter, null)

			assertProblem(
				handler.handleTypeMismatch(exception = exception, request = request),
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"invalid value for '${exception.name}'",
			)
		}
	}

	@Nested
	@DisplayName("handleNoResource")
	inner class HandleNoResource {
		@Test
		fun `maps to 404 NOT_FOUND`() {
			assertProblem(
				handler.handleNoResource(exception = NoResourceFoundException(HttpMethod.GET, "/x", "not found"), request = request),
				HttpStatus.NOT_FOUND,
				"NOT_FOUND",
				"resource not found",
			)
		}
	}

	@Nested
	@DisplayName("handleDataIntegrity")
	inner class HandleDataIntegrity {
		@Test
		fun `maps to 500 INTERNAL_ERROR`() {
			assertProblem(
				handler.handleDataIntegrity(exception = DataIntegrityViolationException("constraint"), request = request),
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_ERROR",
				"unexpected consistency violation",
			)
		}
	}

	@Nested
	@DisplayName("handleUnexpected")
	inner class HandleUnexpected {
		@Test
		fun `maps to 500 INTERNAL_ERROR`() {
			assertProblem(
				handler.handleUnexpected(exception = RuntimeException("boom"), request = request),
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_ERROR",
				"unexpected error",
			)
		}
	}

	private fun assertProblem(
		response: org.springframework.http.ResponseEntity<ProblemDetail>,
		status: HttpStatus,
		code: String,
		detail: String,
		errors: List<String>? = null,
	) {
		val expected =
			ProblemDetail.forStatusAndDetail(status, detail).apply {
				type = URI.create("about:blank")
				title = code
				instance = URI.create("/test")
				setProperty("code", code)
				setProperty("timestamp", OffsetDateTime.now(clock))
				if (errors != null) {
					setProperty("errors", errors)
				}
			}
		assertThat(response.statusCode).isEqualTo(status)
		assertThat(response.body).usingRecursiveComparison().isEqualTo(expected)
	}
}
