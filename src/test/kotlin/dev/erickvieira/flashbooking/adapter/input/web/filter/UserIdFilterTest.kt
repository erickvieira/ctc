package dev.erickvieira.flashbooking.adapter.input.web.filter

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class UserIdFilterTest {
	private val filter = UserIdFilter()

	@AfterEach
	fun clearMdc() = MDC.clear()

	@Nested
	@DisplayName("doFilterInternal")
	inner class DoFilterInternal {
		@Test
		fun `populates the MDC from the request headers and a generated trace`() {
			val request = request(userId = "user-123", idempotencyKey = "key-456")
			val response = response(statusCode = 201)
			val chain = mockk<FilterChain>()
			every { chain.doFilter(request, response) } answers {
				assertThat(MDC.get(UserIdFilter.MDC_USER_ID_KEY)).isEqualTo("user-123")
				assertThat(MDC.get(UserIdFilter.MDC_IDEMPOTENCY_KEY)).isEqualTo("key-456")
				assertThat(MDC.get(UserIdFilter.MDC_TRACE_ID_KEY)).matches("[0-9a-f]{32}")
				assertThat(MDC.get(UserIdFilter.MDC_SPAN_ID_KEY)).matches("[0-9a-f]{16}")
				assertThat(MDC.get(UserIdFilter.MDC_HTTP_METHOD_KEY)).isEqualTo("POST")
				assertThat(MDC.get(UserIdFilter.MDC_PATH_KEY)).isEqualTo("/events/1/reservations")
			}

			filter.doFilterInternal(request = request, response = response, filterChain = chain)

			verify(exactly = 1) { chain.doFilter(request, response) }
			assertThat(MDC.get(UserIdFilter.MDC_USER_ID_KEY)).isNull()
			assertThat(MDC.get(UserIdFilter.MDC_TRACE_ID_KEY)).isNull()
		}

		@Test
		fun `leaves the optional keys unset when the headers are absent`() {
			val request = request(userId = null, idempotencyKey = null)
			val response = response(statusCode = 400)
			val chain = mockk<FilterChain>()
			every { chain.doFilter(request, response) } answers {
				assertThat(MDC.get(UserIdFilter.MDC_USER_ID_KEY)).isNull()
				assertThat(MDC.get(UserIdFilter.MDC_IDEMPOTENCY_KEY)).isNull()
				assertThat(MDC.get(UserIdFilter.MDC_TRACE_ID_KEY)).isNotBlank()
			}

			filter.doFilterInternal(request = request, response = response, filterChain = chain)

			verify(exactly = 1) { chain.doFilter(request, response) }
		}

		@Test
		fun `respects a trace context already present in the MDC and restores it afterwards`() {
			MDC.put(UserIdFilter.MDC_TRACE_ID_KEY, AGENT_TRACE_ID)
			MDC.put(UserIdFilter.MDC_SPAN_ID_KEY, AGENT_SPAN_ID)
			val request = request(userId = null, idempotencyKey = null)
			val response = response(statusCode = 200)
			val chain = mockk<FilterChain>()
			every { chain.doFilter(request, response) } answers {
				assertThat(MDC.get(UserIdFilter.MDC_TRACE_ID_KEY)).isEqualTo(AGENT_TRACE_ID)
				assertThat(MDC.get(UserIdFilter.MDC_SPAN_ID_KEY)).isEqualTo(AGENT_SPAN_ID)
			}

			filter.doFilterInternal(request = request, response = response, filterChain = chain)

			verify(exactly = 1) { chain.doFilter(request, response) }
			assertThat(MDC.get(UserIdFilter.MDC_TRACE_ID_KEY)).isEqualTo(AGENT_TRACE_ID)
			assertThat(MDC.get(UserIdFilter.MDC_SPAN_ID_KEY)).isEqualTo(AGENT_SPAN_ID)
		}

		private fun request(userId: String?, idempotencyKey: String?): HttpServletRequest {
			val request = mockk<HttpServletRequest>()
			every { request.getHeader(UserIdFilter.USER_ID_HEADER) } returns userId
			every { request.getHeader(UserIdFilter.IDEMPOTENCY_KEY_HEADER) } returns idempotencyKey
			every { request.method } returns "POST"
			every { request.requestURI } returns "/events/1/reservations"
			return request
		}

		private fun response(statusCode: Int): HttpServletResponse {
			val response = mockk<HttpServletResponse>()
			every { response.status } returns statusCode
			return response
		}
	}

	private companion object {
		private const val AGENT_TRACE_ID = "0123456789abcdef0123456789abcdef"
		private const val AGENT_SPAN_ID = "0123456789abcdef"
	}
}
