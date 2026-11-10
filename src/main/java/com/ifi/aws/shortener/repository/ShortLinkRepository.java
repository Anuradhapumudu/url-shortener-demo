package com.ifi.aws.shortener.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Plain JdbcTemplate so the real service can point it at the master datasource regardless of tenant. */
@Repository
public class ShortLinkRepository {

	private static final String COLUMNS =
			"id, code, long_url, click_count, created_at, last_clicked_at, tenant_id, source, expires_at, disabled_at";

	private final JdbcTemplate jdbcTemplate;

	public ShortLinkRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** Throws DuplicateKeyException if the code already exists. */
	public void insert(ShortLink link) {
		jdbcTemplate.update(
				"INSERT INTO short_link (code, long_url, click_count, created_at, tenant_id, source, expires_at) "
						+ "VALUES (?, ?, 0, ?, ?, ?, ?)",
				link.code(), link.longUrl(), link.createdAt(), link.tenantId(), link.source(), link.expiresAt());
	}

	/** Includes disabled links so callers can tell "gone" from "never existed". */
	public Optional<ShortLink> findByCode(String code) {
		return jdbcTemplate.query("SELECT " + COLUMNS + " FROM short_link WHERE code = ?", this::mapRow, code)
				.stream()
				.findFirst();
	}

	/** One atomic UPDATE so concurrent clicks are never lost. */
	public void recordClick(String code, LocalDateTime clickedAt) {
		jdbcTemplate.update(
				"UPDATE short_link SET click_count = click_count + 1, last_clicked_at = ? WHERE code = ?",
				clickedAt, code);
	}

	public List<ShortLink> findRecent(int limit) {
		return jdbcTemplate.query(
				"SELECT " + COLUMNS + " FROM short_link WHERE disabled_at IS NULL ORDER BY id DESC LIMIT ?",
				this::mapRow, limit);
	}

	/** Soft delete. Returns true if an active link was disabled. */
	public boolean disable(String code, LocalDateTime now) {
		return jdbcTemplate.update(
				"UPDATE short_link SET disabled_at = ? WHERE code = ? AND disabled_at IS NULL", now, code) > 0;
	}

	/** Returns [active links, total clicks on active links]. */
	public long[] totals() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*), COALESCE(SUM(click_count), 0) FROM short_link WHERE disabled_at IS NULL",
				(rs, rowNum) -> new long[] { rs.getLong(1), rs.getLong(2) });
	}

	private ShortLink mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new ShortLink(
				rs.getLong("id"),
				rs.getString("code"),
				rs.getString("long_url"),
				rs.getLong("click_count"),
				rs.getObject("created_at", LocalDateTime.class),
				rs.getObject("last_clicked_at", LocalDateTime.class),
				rs.getString("tenant_id"),
				rs.getString("source"),
				rs.getObject("expires_at", LocalDateTime.class),
				rs.getObject("disabled_at", LocalDateTime.class));
	}
}
