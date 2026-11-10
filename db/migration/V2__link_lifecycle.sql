-- Adds link lifecycle columns to an existing short_link table (MySQL 8).
-- Safe to hand to Liquibase/Flyway as-is; run once, in a transaction-less DDL context.
ALTER TABLE short_link
	ADD COLUMN tenant_id   VARCHAR(64) NULL,
	ADD COLUMN source      VARCHAR(32) NULL,
	ADD COLUMN expires_at  DATETIME    NULL,
	ADD COLUMN disabled_at DATETIME    NULL,
	ADD INDEX idx_short_link_tenant (tenant_id);
