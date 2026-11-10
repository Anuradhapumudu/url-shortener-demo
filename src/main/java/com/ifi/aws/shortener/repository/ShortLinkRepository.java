package com.ifi.aws.shortener.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShortLinkRepository {

	private static final String COLUMNS =
			"id, code, long_url, click_count, created_at, last_clicked_at, tenant_id, source, expires_at, disabled_at";

	private final JdbcTemplate jdbcTemplate;

	public ShortLinkRepository(
			@Autowired(required = false) @Qualifier("masterJdbcTemplate") JdbcTemplate masterJdbcTemplate,
			JdbcTemplate defaultJdbcTemplate) {
		this.jdbcTemplate = masterJdbcTemplate != null ? masterJdbcTemplate : defaultJdbcTemplate;
	}

	public void insert(ShortLink link) {
		jdbcTemplate.update(
				"INSERT INTO short_link (code, long_url, click_count, created_at, tenant_id, source, expires_at) "
						+ "VALUES (?, ?, 0, ?, ?, ?, ?)",
				link.code(), link.longUrl(), link.createdAt(), link.tenantId(), link.source(), link.expiresAt());
	}

	public Optional<ShortLink> findByCode(String code) {
		return jdbcTemplate.query("SELECT " + COLUMNS + " FROM short_link WHERE code = ?", this::mapRow, code)
				.stream()
				.findFirst();
	}

	public Optional<ShortLink> findByCode(String code, String tenantId) {
		if (tenantId != null && !tenantId.isBlank()) {
			return jdbcTemplate.query(
					"SELECT " + COLUMNS + " FROM short_link WHERE code = ? AND tenant_id = ?",
					this::mapRow, code, tenantId)
					.stream()
					.findFirst();
		}
		return findByCode(code);
	}

	public void recordClick(String code, LocalDateTime clickedAt) {
		jdbcTemplate.update(
				"UPDATE short_link SET click_count = click_count + 1, last_clicked_at = ? WHERE code = ?",
				clickedAt, code);
	}

	public List<ShortLink> findRecent(String tenantId, LocalDateTime now, int limit) {
		if (tenantId != null && !tenantId.isBlank()) {
			return jdbcTemplate.query(
					"SELECT " + COLUMNS + " FROM short_link "
							+ "WHERE tenant_id = ? AND disabled_at IS NULL AND (expires_at IS NULL OR expires_at > ?) "
							+ "ORDER BY id DESC LIMIT ?",
					this::mapRow, tenantId, now, limit);
		}
		return jdbcTemplate.query(
				"SELECT " + COLUMNS + " FROM short_link "
						+ "WHERE disabled_at IS NULL AND (expires_at IS NULL OR expires_at > ?) "
						+ "ORDER BY id DESC LIMIT ?",
				this::mapRow, now, limit);
	}

	public boolean disable(String code, String tenantId, LocalDateTime now) {
		if (tenantId != null && !tenantId.isBlank()) {
			return jdbcTemplate.update(
					"UPDATE short_link SET disabled_at = ? WHERE code = ? AND tenant_id = ? AND disabled_at IS NULL",
					now, code, tenantId) > 0;
		}
		return jdbcTemplate.update(
				"UPDATE short_link SET disabled_at = ? WHERE code = ? AND disabled_at IS NULL", now, code) > 0;
	}

	public boolean updateDestination(String code, String newLongUrl, String tenantId) {
		if (tenantId != null && !tenantId.isBlank()) {
			return jdbcTemplate.update(
					"UPDATE short_link SET long_url = ? WHERE code = ? AND tenant_id = ? AND disabled_at IS NULL",
					newLongUrl, code, tenantId) > 0;
		}
		return jdbcTemplate.update(
				"UPDATE short_link SET long_url = ? WHERE code = ? AND disabled_at IS NULL",
				newLongUrl, code) > 0;
	}

	public long[] totals(String tenantId, LocalDateTime now) {
		if (tenantId != null && !tenantId.isBlank()) {
			return jdbcTemplate.queryForObject(
					"SELECT COUNT(*), COALESCE(SUM(click_count), 0) FROM short_link "
							+ "WHERE tenant_id = ? AND disabled_at IS NULL AND (expires_at IS NULL OR expires_at > ?)",
					(rs, rowNum) -> new long[] { rs.getLong(1), rs.getLong(2) }, tenantId, now);
		}
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*), COALESCE(SUM(click_count), 0) FROM short_link "
						+ "WHERE disabled_at IS NULL AND (expires_at IS NULL OR expires_at > ?)",
				(rs, rowNum) -> new long[] { rs.getLong(1), rs.getLong(2) }, now);
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
