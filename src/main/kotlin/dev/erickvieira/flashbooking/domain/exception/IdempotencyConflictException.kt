package dev.erickvieira.flashbooking.domain.exception

class IdempotencyConflictException(val idempotencyKey: String?) :
	RuntimeException("idempotency key '$idempotencyKey' was reused with a different payload")
