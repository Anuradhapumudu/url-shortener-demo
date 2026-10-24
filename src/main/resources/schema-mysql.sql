-- code uses ascii_bin so lookups are case-sensitive ("Ab3xK9q" and "ab3xk9q" are different links).
-- MySQL's default collation is case-insensitive, which would cause false collisions and wrong redirects.
CREATE TABLE IF NOT EXISTS short_link (
	id              BIGINT        NOT NULL AUTO_INCREMENT,
	code            VARCHAR(16)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	long_url        VARCHAR(2048) NOT NULL,
	click_count     INT           NOT NULL DEFAULT 0,
	created_at      DATETIME      NOT NULL,
	last_clicked_at DATETIME      NULL,
	PRIMARY KEY (id),
	CONSTRAINT uk_short_link_code UNIQUE (code)
);
