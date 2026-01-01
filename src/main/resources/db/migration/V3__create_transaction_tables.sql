CREATE TABLE IF NOT EXISTS transaction
(
    id              UUID PRIMARY KEY,
    account_id      VARCHAR(255)   NOT NULL,
    amount_value    DECIMAL(19, 4) NOT NULL,
    amount_currency VARCHAR(3)     NOT NULL,
    type            VARCHAR(50)    NOT NULL,
    channel         VARCHAR(50)    NOT NULL,
    device_id       VARCHAR(255),
    timestamp       TIMESTAMP      NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    revision        INTEGER        NOT NULL,

    CONSTRAINT chk_transaction_type CHECK (type IN ('PURCHASE', 'ATM_WITHDRAWAL', 'TRANSFER', 'PAYMENT', 'REFUND')),
    CONSTRAINT chk_channel CHECK (channel IN ('CARD', 'ACH', 'WIRE', 'MOBILE', 'ONLINE', 'POS', 'ATM'))
);

CREATE TABLE IF NOT EXISTS merchant
(
    id             VARCHAR(255),
    name           VARCHAR(255) NOT NULL,
    category       VARCHAR(100),
    transaction_id UUID         NOT NULL REFERENCES transaction (id),
    PRIMARY KEY (id, transaction_id)
);

CREATE TABLE IF NOT EXISTS location
(
    transaction_id UUID PRIMARY KEY REFERENCES transaction (id),
    latitude       DOUBLE PRECISION NOT NULL,
    longitude      DOUBLE PRECISION NOT NULL,
    country        VARCHAR(100),
    city           VARCHAR(100)
);

CREATE INDEX idx_transaction_account_id ON transaction (account_id);
CREATE INDEX idx_transaction_timestamp ON transaction (timestamp);
CREATE INDEX idx_transaction_device_id ON transaction (device_id);
CREATE INDEX idx_location_coordinates ON location (latitude, longitude);
CREATE INDEX idx_transaction_account_id_timestamp ON transaction (account_id, timestamp);
