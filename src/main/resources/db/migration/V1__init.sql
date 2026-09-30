CREATE TABLE events (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    starts_at timestamptz NOT NULL,
    capacity integer NOT NULL CHECK (capacity > 0),
    available integer NOT NULL CHECK (available >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (available <= capacity)
);

CREATE TABLE reservations (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES events (id),
    quantity integer NOT NULL CHECK (quantity > 0),
    status text NOT NULL CHECK (status IN ('PENDING', 'EXPIRED', 'CANCELLED')),
    idempotency_key text,
    fingerprint uuid,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

-- Índice único simples: no Postgres NULLs são distintos, então várias reservas sem chave de
-- idempotência convivem; e este índice é inferível por `ON CONFLICT (idempotency_key)` (um índice
-- parcial exigiria repetir o predicado no target do ON CONFLICT).
CREATE UNIQUE INDEX ux_reservations_idempotency_key
    ON reservations (idempotency_key);

CREATE INDEX ix_reservations_expiry
    ON reservations (status, expires_at);

CREATE INDEX ix_reservations_event
    ON reservations (event_id);
