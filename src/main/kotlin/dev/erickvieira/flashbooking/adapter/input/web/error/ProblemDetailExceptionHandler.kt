package dev.erickvieira.flashbooking.adapter.input.web.error

import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.exception.InvalidCapacityException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI
import java.time.Clock
import java.time.OffsetDateTime

@RestControllerAdvice
class ProblemDetailExceptionHandler(
	private val clock: Clock,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(InvalidCapacityException::class)
	fun handleInvalidCapacity(
		exception: InvalidCapacityException,
		request: HttpServletRequest,
	): ResponseEntity<ProblemDetail> =
		problem(HttpStatus.BAD_REQUEST, "INVALID_CAPACITY", exception.message.orEmpty(), request)

	@ExceptionHandler(EventNotFoundException::class)
	fun handleEventNotFound(
		exception: EventNotFoundException,
		request: HttpServletRequest,
	): ResponseEntity<ProblemDetail> =
		problem(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", exception.message.orEmpty(), request)

	@ExceptionHandler(MissingRequestHeaderException::class)
	fun handleMissingHeader(
		exception: MissingRequestHeaderException,
		request: HttpServletRequest,
	): ResponseEntity<ProblemDetail> =
		problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "missing required header '${exception.headerName}'", request)

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleBeanValidation(
		exception: MethodArgumentNotValidException,
		request: HttpServletRequest,
	): ResponseEntity<ProblemDetail> {
		val errors = exception.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
		return problem(
			HttpStatus.BAD_REQUEST,
			"VALIDATION_ERROR",
			errors.joinToString("; ").ifBlank { "invalid request" },
			request,
			errors,
		)
	}

	@ExceptionHandler(HttpMessageNotReadableException::class)
	fun handleUnreadableBody(
		exception: HttpMessageNotReadableException,
		request: HttpServletRequest,
	): ResponseEntity<ProblemDetail> =
		problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "malformed request body", request)

	@ExceptionHandler(MethodArgumentTypeMismatchException::class)
	fun handleTypeMismatch(
		exception: MethodArgumentTypeMismatchException,
		request: HttpServletRequest,
	): ResponseEntity<ProblemDetail> =
		problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "invalid value for '${exception.name}'", request)

	@ExceptionHandler(NoResourceFoundException::class)
	fun handleNoResource(
		exception: NoResourceFoundException,
		request: HttpServletRequest,
	): ResponseEntity<ProblemDetail> =
		problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "resource not found", request)

	@ExceptionHandler(DataIntegrityViolationException::class)
	fun handleDataIntegrity(
		exception: DataIntegrityViolationException,
		request: HttpServletRequest,
	): ResponseEntity<ProblemDetail> {
		// CHECK/FK violations are internal invariants that should never fire; log the constraint
		// name Postgres assigned (inside the message) and return a generic 500.
		log.error("data integrity violation: {}", exception.mostSpecificCause.message, exception)
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "unexpected consistency violation", request)
	}

	@ExceptionHandler(Exception::class)
	fun handleUnexpected(
		exception: Exception,
		request: HttpServletRequest,
	): ResponseEntity<ProblemDetail> {
		log.error("unhandled error", exception)
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "unexpected error", request)
	}

	private fun problem(
		status: HttpStatus,
		code: String,
		detail: String,
		request: HttpServletRequest,
		errors: List<String>? = null,
	): ResponseEntity<ProblemDetail> {
		val body = ProblemDetail.forStatusAndDetail(status, detail)
		body.type = URI.create("about:blank")
		body.title = status.reasonPhrase
		body.instance = URI.create(request.requestURI)
		body.setProperty("code", code)
		body.setProperty("timestamp", OffsetDateTime.now(clock))
		if (errors != null) {
			body.setProperty("errors", errors)
		}
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body)
	}
}
