-- ascii_bin makes code lookups case-sensitive; MySQL's default collation would treat "Ab3xK9q" and "ab3xk9q" as equal.
-- CREATE TABLE IF NOT EXISTS never alters an existing table: apply db/migration/*.sql to older databases.
CREATE TABLE IF NOT EXISTS short_link (
	id              BIGINT        NOT NULL AUTO_INCREMENT,
	code            VARCHAR(16)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	long_url        VARCHAR(2048) CHARACTER SET utf8mb4 NOT NULL,
	click_count     BIGINT        NOT NULL DEFAULT 0,
	created_at      DATETIME      NOT NULL,
	last_clicked_at DATETIME      NULL,
	tenant_id       VARCHAR(64)   NULL,
	source          VARCHAR(32)   NULL,
	expires_at      DATETIME      NULL,
	disabled_at     DATETIME      NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_short_link_code UNIQUE (code),
	INDEX idx_short_link_tenant (tenant_id)
) ENGINE=InnoDB;
