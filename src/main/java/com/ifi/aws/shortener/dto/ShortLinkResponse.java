package com.ifi.aws.shortener.dto;

import java.time.LocalDateTime;

public record ShortLinkResponse(
		String code,
		String shortUrl,
		String longUrl,
		int clickCount,
		LocalDateTime createdAt,
		LocalDateTime lastClickedAt) {
}
