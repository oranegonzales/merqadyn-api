CREATE INDEX idx_product_merchant_name_id ON products (merchant_id, name, id);
CREATE INDEX idx_inventory_merchant_updated_id ON inventory_items (merchant_id, updated_at DESC, id);
CREATE INDEX idx_device_merchant_name ON devices (merchant_id, name);
CREATE INDEX idx_processed_merchant_created ON processed_mutations (merchant_id, created_at DESC);
CREATE INDEX idx_change_log_occurred ON change_log (merchant_id, occurred_at DESC);
