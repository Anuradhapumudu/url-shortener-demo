CREATE TABLE IF NOT EXISTS short_link (
	id              BIGINT        NOT NULL AUTO_INCREMENT,
	code            VARCHAR(16)   NOT NULL,
	long_url        VARCHAR(2048) NOT NULL,
	click_count     BIGINT        NOT NULL DEFAULT 0,
	created_at      TIMESTAMP     NOT NULL,
	last_clicked_at TIMESTAMP     NULL,
	tenant_id       VARCHAR(64)   NULL,
	source          VARCHAR(32)   NULL,
	expires_at      TIMESTAMP     NULL,
	disabled_at     TIMESTAMP     NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_short_link_code UNIQUE (code)
);
