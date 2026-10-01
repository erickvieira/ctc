ALTER TABLE reservations
    ADD COLUMN user_id uuid NOT NULL;

CREATE INDEX ix_reservations_user
    ON reservations (user_id);

DROP INDEX ux_reservations_idempotency_key;

CREATE UNIQUE INDEX ux_reservations_user_idempotency_key
    ON reservations (user_id, idempotency_key);
