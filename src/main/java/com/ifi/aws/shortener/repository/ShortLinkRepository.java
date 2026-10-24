package com.ifi.aws.shortener.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Plain JdbcTemplate on purpose: in the real service this lets us write to the
 * master (aws_master) datasource directly, regardless of which tenant the request belongs to.
 */
@Repository
public class ShortLinkRepository {

	private final JdbcTemplate jdbcTemplate;

	public ShortLinkRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** Throws DuplicateKeyException if the code already exists. */
	public void insert(String code, String longUrl, LocalDateTime createdAt) {
		jdbcTemplate.update(
				"INSERT INTO short_link (code, long_url, click_count, created_at) VALUES (?, ?, 0, ?)",
				code, longUrl, Timestamp.valueOf(createdAt));
	}

	public Optional<ShortLink> findByCode(String code) {
		return jdbcTemplate.query("SELECT * FROM short_link WHERE code = ?", this::mapRow, code)
				.stream()
				.findFirst();
	}

	/** Single atomic UPDATE so concurrent clicks are never lost. */
	public void recordClick(String code, LocalDateTime clickedAt) {
		jdbcTemplate.update(
				"UPDATE short_link SET click_count = click_count + 1, last_clicked_at = ? WHERE code = ?",
				Timestamp.valueOf(clickedAt), code);
	}

	public List<ShortLink> findRecent(int limit) {
		return jdbcTemplate.query("SELECT * FROM short_link ORDER BY id DESC LIMIT ?", this::mapRow, limit);
	}

	/** Returns true if a link was deleted. */
	public boolean delete(String code) {
		return jdbcTemplate.update("DELETE FROM short_link WHERE code = ?", code) > 0;
	}

	/** Returns [total links, total clicks]. */
	public long[] totals() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*), COALESCE(SUM(click_count), 0) FROM short_link",
				(rs, rowNum) -> new long[] { rs.getLong(1), rs.getLong(2) });
	}

	private ShortLink mapRow(ResultSet rs, int rowNum) throws SQLException {
		Timestamp lastClicked = rs.getTimestamp("last_clicked_at");
		return new ShortLink(
				rs.getLong("id"),
				rs.getString("code"),
				rs.getString("long_url"),
				rs.getInt("click_count"),
				rs.getTimestamp("created_at").toLocalDateTime(),
				lastClicked != null ? lastClicked.toLocalDateTime() : null);
	}
}
