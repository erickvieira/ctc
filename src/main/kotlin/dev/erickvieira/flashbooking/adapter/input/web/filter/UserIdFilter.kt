package dev.erickvieira.flashbooking.adapter.input.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class UserIdFilter : OncePerRequestFilter() {
	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val userId = request.getHeader(USER_ID_HEADER)
		if (userId != null) {
			MDC.put(MDC_USER_ID_KEY, userId)
		}
		try {
			filterChain.doFilter(request, response)
		} finally {
			MDC.remove(MDC_USER_ID_KEY)
		}
	}

	companion object {
		const val USER_ID_HEADER = "X-User-Id"
		const val MDC_USER_ID_KEY = "userId"
	}
}
