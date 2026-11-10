package com.ifi.aws.shortener.dto;

import java.time.LocalDateTime;

public record ShortLinkResponse(
		String code,
		String shortUrl,
		String longUrl,
		long clickCount,
		LocalDateTime createdAt,
		LocalDateTime lastClickedAt,
		String tenantId,
		String source,
		LocalDateTime expiresAt) {
}
