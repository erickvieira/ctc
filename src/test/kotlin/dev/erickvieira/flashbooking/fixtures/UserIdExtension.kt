package dev.erickvieira.flashbooking.fixtures

import dev.erickvieira.flashbooking.domain.model.UserId
import java.util.UUID

fun UserId.Companion.fake(value: UUID = UUID.randomUUID()): UserId = UserId(value = value)
