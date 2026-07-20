ALTER TABLE merchants
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING BTRIM(currency);
