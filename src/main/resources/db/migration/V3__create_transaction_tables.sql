-- Main transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    amount_value DECIMAL(19, 4) NOT NULL,
    amount_currency VARCHAR(3) NOT NULL,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    merchant_id UUID,
    device_id UUID,
    location_id UUID NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE,
    updated_at         TIMESTAMP WITH TIME ZONE,
    revision           INTEGER NOT NULL,

    CONSTRAINT chk_transaction_type CHECK (type IN ('PURCHASE', 'ATM_WITHDRAWAL', 'TRANSFER', 'PAYMENT', 'REFUND')),
    CONSTRAINT chk_channel CHECK (channel IN ('CARD', 'ACH', 'WIRE', 'MOBILE', 'ONLINE', 'POS', 'ATM'))
);

-- Merchants table
CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY,
    merchant_id VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100)
);

-- Devices table
CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY,
    device_id VARCHAR(255) UNIQUE NOT NULL
);

-- Locations table
CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    country VARCHAR(100),
    city VARCHAR(100),
    timestamp TIMESTAMP NOT NULL
);

-- Foreign key constraints
ALTER TABLE transactions
    ADD CONSTRAINT fk_transaction_merchant
    FOREIGN KEY (merchant_id) REFERENCES merchants(id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transaction_device
    FOREIGN KEY (device_id) REFERENCES devices(id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transaction_location
    FOREIGN KEY (location_id) REFERENCES locations(id);

-- Indexes
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_timestamp ON transactions(timestamp);
CREATE INDEX idx_transactions_merchant_id ON transactions(merchant_id);
CREATE INDEX idx_transactions_device_id ON transactions(device_id);
CREATE INDEX idx_merchants_merchant_id ON merchants(merchant_id);
CREATE INDEX idx_devices_device_id ON devices(device_id);
CREATE INDEX idx_locations_coordinates ON locations(latitude, longitude);
CREATE INDEX idx_transactions_account_id_timestamp ON transactions(account_id, timestamp);
