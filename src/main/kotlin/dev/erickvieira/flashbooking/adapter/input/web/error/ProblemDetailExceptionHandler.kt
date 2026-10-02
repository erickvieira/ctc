package dev.erickvieira.flashbooking.adapter.input.web.error

import dev.erickvieira.flashbooking.domain.exception.EventNotFoundException
import dev.erickvieira.flashbooking.domain.exception.EventSoldOutException
import dev.erickvieira.flashbooking.domain.exception.IdempotencyConflictException
import dev.erickvieira.flashbooking.domain.exception.InvalidCapacityException
import dev.erickvieira.flashbooking.domain.exception.InvalidQuantityException
import dev.erickvieira.flashbooking.domain.exception.ReservationNotFoundException
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
        problem(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_CAPACITY",
            detail = exception.message.orEmpty(),
            request = request,
        )

    @ExceptionHandler(EventNotFoundException::class)
    fun handleEventNotFound(
        exception: EventNotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.NOT_FOUND,
            code = "EVENT_NOT_FOUND",
            detail = exception.message.orEmpty(),
            request = request,
        )

    @ExceptionHandler(ReservationNotFoundException::class)
    fun handleReservationNotFound(
        exception: ReservationNotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.NOT_FOUND,
            code = "RESERVATION_NOT_FOUND",
            detail = exception.message.orEmpty(),
            request = request,
        )

    @ExceptionHandler(InvalidQuantityException::class)
    fun handleInvalidQuantity(
        exception: InvalidQuantityException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_QUANTITY",
            detail = exception.message.orEmpty(),
            request = request,
        )

    @ExceptionHandler(EventSoldOutException::class)
    fun handleEventSoldOut(
        exception: EventSoldOutException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.CONFLICT,
            code = "EVENT_SOLD_OUT",
            detail = exception.message.orEmpty(),
            request = request,
        )

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(
        exception: IdempotencyConflictException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.CONFLICT,
            code = "IDEMPOTENCY_CONFLICT",
            detail = exception.message.orEmpty(),
            request = request,
        )

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(
        exception: MissingRequestHeaderException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            detail = "missing required header '${exception.headerName}'",
            request = request,
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val errors = exception.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return problem(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            detail = errors.joinToString("; ").ifBlank { "invalid request" },
            request = request,
            errors = errors,
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            detail = "malformed request body",
            request = request,
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        exception: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            detail = "invalid value for '${exception.name}'",
            request = request,
        )

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(
        exception: NoResourceFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> =
        problem(status = HttpStatus.NOT_FOUND, code = "NOT_FOUND", detail = "resource not found", request = request)

    /**
     * CHECK/FK = invariante interna, não deveria disparar. Loga o constraint do Postgres e responde
     * 500 genérico.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(
        exception: DataIntegrityViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.error("data integrity violation: {}", exception.mostSpecificCause.message, exception)
        return problem(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_ERROR",
            detail = "unexpected consistency violation",
            request = request,
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.error("unhandled error", exception)
        return problem(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_ERROR",
            detail = "unexpected error",
            request = request,
        )
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
        body.title = code
        body.instance = URI.create(request.requestURI)
        body.setProperty("code", code)
        body.setProperty("timestamp", OffsetDateTime.now(clock))
        if (errors != null) {
            body.setProperty("errors", errors)
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body)
    }
}
