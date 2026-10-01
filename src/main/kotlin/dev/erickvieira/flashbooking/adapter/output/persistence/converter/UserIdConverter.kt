package dev.erickvieira.flashbooking.adapter.output.persistence.converter

import dev.erickvieira.flashbooking.domain.model.UserId
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.util.UUID

@Converter(autoApply = true)
class UserIdConverter : AttributeConverter<UserId, UUID> {
	override fun convertToDatabaseColumn(attribute: UserId): UUID = attribute.value

	override fun convertToEntityAttribute(dbData: UUID): UserId = UserId(value = dbData)
}
