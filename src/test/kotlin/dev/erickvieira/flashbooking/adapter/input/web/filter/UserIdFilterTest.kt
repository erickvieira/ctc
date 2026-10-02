package dev.erickvieira.flashbooking.adapter.input.web.filter

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.MDC

class UserIdFilterTest {
	private val filter = UserIdFilter()

	@Nested
	@DisplayName("doFilterInternal")
	inner class DoFilterInternal {
		@Test
		fun `puts the user id into the MDC when the header is present`() {
			val request = mockk<HttpServletRequest>()
			val response = mockk<HttpServletResponse>()
			val chain = mockk<FilterChain>()
			every { request.getHeader(UserIdFilter.USER_ID_HEADER) } returns "user-123"
			every { chain.doFilter(request, response) } answers {
				assertThat(MDC.get(UserIdFilter.MDC_USER_ID_KEY)).isEqualTo("user-123")
			}

			filter.doFilterInternal(request = request, response = response, filterChain = chain)

			verify(exactly = 1) { chain.doFilter(request, response) }
			assertThat(MDC.get(UserIdFilter.MDC_USER_ID_KEY)).isNull()
		}

		@Test
		fun `does not put the user id into the MDC when the header is absent`() {
			val request = mockk<HttpServletRequest>()
			val response = mockk<HttpServletResponse>()
			val chain = mockk<FilterChain>()
			every { request.getHeader(UserIdFilter.USER_ID_HEADER) } returns null
			every { chain.doFilter(request, response) } answers {
				assertThat(MDC.get(UserIdFilter.MDC_USER_ID_KEY)).isNull()
			}

			filter.doFilterInternal(request = request, response = response, filterChain = chain)

			verify(exactly = 1) { chain.doFilter(request, response) }
			assertThat(MDC.get(UserIdFilter.MDC_USER_ID_KEY)).isNull()
		}
	}
}
