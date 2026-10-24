package com.ifi.aws.shortener.repository;

import java.time.LocalDateTime;

public record ShortLink(
		Long id,
		String code,
		String longUrl,
		int clickCount,
		LocalDateTime createdAt,
		LocalDateTime lastClickedAt) {
}
