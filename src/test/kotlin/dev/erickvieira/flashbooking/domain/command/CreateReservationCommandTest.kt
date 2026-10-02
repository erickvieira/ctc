package dev.erickvieira.flashbooking.domain.command

import dev.erickvieira.flashbooking.domain.model.Quantity
import dev.erickvieira.flashbooking.domain.model.ReservationIdempotency
import dev.erickvieira.flashbooking.domain.model.UserId
import dev.erickvieira.flashbooking.fixtures.fake
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class CreateReservationCommandTest {
	@Nested
	@DisplayName("idempotency")
	inner class Idempotency {
		@Test
		fun `is null when there is no idempotency key`() {
			val command = CreateReservationCommand.fake(idempotencyKey = null)

			assertThat(command.idempotency).isNull()
		}

		@Test
		fun `couples the key with a deterministic v3 fingerprint of the payload`() {
			val userId = UserId.fake()
			val eventId = UUID.randomUUID()
			val quantity = Quantity.fake()
			val command =
				CreateReservationCommand.fake(
					userId = userId,
					eventId = eventId,
					quantity = quantity,
					idempotencyKey = "key-1",
				)

			val expected =
				ReservationIdempotency(
					key = "key-1",
					fingerprint = UUID.nameUUIDFromBytes("flashbooking|${userId.value}|$eventId|${quantity.value}".encodeToByteArray()),
				)

			assertThat(command.idempotency).usingRecursiveComparison().isEqualTo(expected)
		}
	}
}
