package dev.erickvieira.flashbooking.adapter.output.persistence.mapper

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PersistenceMapperConfig {
	@Bean
	fun eventPersistenceMapper(): EventPersistenceMapper = EventPersistenceMapperImpl
}
