package dev.erickvieira.flashbooking.adapter.output.persistence.converter

import dev.erickvieira.flashbooking.domain.model.Quantity
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class QuantityConverter : AttributeConverter<Quantity, Int> {
	override fun convertToDatabaseColumn(attribute: Quantity): Int = attribute.value

	override fun convertToEntityAttribute(dbData: Int): Quantity = Quantity.fromStorage(value = dbData)
}
