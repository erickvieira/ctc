package dev.erickvieira.flashbooking.adapter.output.persistence.converter

import dev.erickvieira.flashbooking.domain.model.Capacity
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class CapacityConverter : AttributeConverter<Capacity, Int> {
	override fun convertToDatabaseColumn(attribute: Capacity): Int = attribute.value

	override fun convertToEntityAttribute(dbData: Int): Capacity = Capacity.of(dbData)
}
