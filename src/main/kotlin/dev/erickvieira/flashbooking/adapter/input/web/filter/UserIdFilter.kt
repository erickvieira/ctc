package dev.erickvieira.flashbooking.adapter.input.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Enriquece o MDC com o contexto da requisição (`traceId`, `spanId`, `userId`, `idempotencyKey`, método e
 * path) e emite uma linha de acesso por request. `traceId`/`spanId` só são gerados quando **ausentes** —
 * um agente externo (ou uma camada anterior) que já os tenha posto no MDC é respeitado. Ao fim, restaura
 * os valores anteriores (ou remove o que não existia).
 */
@Component
class UserIdFilter : OncePerRequestFilter() {
	public override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val previous = MANAGED_KEYS.associateWith { MDC.get(it) }
		val startedAt = Instant.now()
		try {
			request.getHeader(USER_ID_HEADER)?.let { MDC.put(MDC_USER_ID_KEY, it) }
			request.getHeader(IDEMPOTENCY_KEY_HEADER)?.let { MDC.put(MDC_IDEMPOTENCY_KEY, it) }
			val traceId = MDC.get(MDC_TRACE_ID_KEY) ?: UUID.randomUUID().toString().replace("-", "")
			MDC.put(MDC_TRACE_ID_KEY, traceId)
			MDC.put(MDC_SPAN_ID_KEY, MDC.get(MDC_SPAN_ID_KEY) ?: traceId.substring(0, SPAN_ID_LENGTH))
			MDC.put(MDC_HTTP_METHOD_KEY, request.method)
			MDC.put(MDC_PATH_KEY, request.requestURI)
			filterChain.doFilter(request, response)
		} finally {
			log.info(
				"{} {} -> {} ({} ms)",
				request.method,
				request.requestURI,
				response.status,
				Duration.between(startedAt, Instant.now()).toMillis(),
			)
			previous.forEach { (key, value) ->
				if (value != null) {
					MDC.put(key, value)
				} else {
					MDC.remove(key)
				}
			}
		}
	}

	companion object {
		private val log = LoggerFactory.getLogger(UserIdFilter::class.java)

		const val USER_ID_HEADER = "X-User-Id"
		const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

		const val MDC_USER_ID_KEY = "userId"
		const val MDC_IDEMPOTENCY_KEY = "idempotencyKey"
		const val MDC_TRACE_ID_KEY = "traceId"
		const val MDC_SPAN_ID_KEY = "spanId"
		const val MDC_HTTP_METHOD_KEY = "httpMethod"
		const val MDC_PATH_KEY = "path"
		const val MDC_EVENT_ID_KEY = "eventId"
		const val MDC_RESERVATION_ID_KEY = "reservationId"

		private const val SPAN_ID_LENGTH = 16

		private val MANAGED_KEYS =
			listOf(
				MDC_USER_ID_KEY,
				MDC_IDEMPOTENCY_KEY,
				MDC_TRACE_ID_KEY,
				MDC_SPAN_ID_KEY,
				MDC_HTTP_METHOD_KEY,
				MDC_PATH_KEY,
				MDC_EVENT_ID_KEY,
				MDC_RESERVATION_ID_KEY,
			)
	}
}
