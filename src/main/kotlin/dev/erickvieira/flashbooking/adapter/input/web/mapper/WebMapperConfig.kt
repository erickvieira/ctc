package dev.erickvieira.flashbooking.adapter.input.web.mapper

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WebMapperConfig {
	@Bean
	fun eventApiMapper(): EventApiMapper = EventApiMapperImpl
}
