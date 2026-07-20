CREATE TABLE merchants (
    id UUID PRIMARY KEY,
    name VARCHAR(140) NOT NULL,
    currency CHAR(3) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE locations (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(140) NOT NULL,
    address VARCHAR(240) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_location_code UNIQUE (merchant_id, code)
);

CREATE TABLE devices (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    name VARCHAR(120) NOT NULL,
    platform VARCHAR(40) NOT NULL,
    app_version VARCHAR(32) NOT NULL,
    last_cursor BIGINT NOT NULL DEFAULT 0,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_device_name UNIQUE (merchant_id, name)
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    category VARCHAR(100) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    price NUMERIC(14, 2) NOT NULL CHECK (price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_product_sku UNIQUE (merchant_id, sku)
);

CREATE TABLE inventory_items (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    product_id UUID NOT NULL REFERENCES products(id),
    on_hand NUMERIC(14, 3) NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
    reserved NUMERIC(14, 3) NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_inventory_location_product UNIQUE (location_id, product_id)
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    product_id UUID NOT NULL REFERENCES products(id),
    device_id UUID REFERENCES devices(id),
    mutation_id UUID,
    movement_type VARCHAR(40) NOT NULL,
    quantity_delta NUMERIC(14, 3) NOT NULL,
    reason VARCHAR(240) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE change_log (
    cursor BIGSERIAL PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID NOT NULL,
    operation VARCHAR(40) NOT NULL,
    entity_version BIGINT NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE processed_mutations (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    mutation_id UUID NOT NULL,
    mutation_type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    response_payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_processed_device_mutation UNIQUE (device_id, mutation_id)
);

CREATE TABLE sync_conflicts (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    mutation_id UUID NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID NOT NULL,
    reason VARCHAR(160) NOT NULL,
    client_version BIGINT,
    server_version BIGINT NOT NULL,
    client_payload TEXT NOT NULL,
    server_payload TEXT NOT NULL,
    resolution VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_product_merchant ON products(merchant_id);
CREATE INDEX idx_inventory_merchant ON inventory_items(merchant_id);
CREATE INDEX idx_change_log_merchant_cursor ON change_log(merchant_id, cursor);
CREATE INDEX idx_conflict_merchant_created ON sync_conflicts(merchant_id, created_at DESC);
CREATE INDEX idx_movement_merchant_created ON stock_movements(merchant_id, created_at DESC);
