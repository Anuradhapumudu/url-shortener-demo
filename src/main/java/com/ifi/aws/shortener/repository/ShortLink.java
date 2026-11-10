package com.ifi.aws.shortener.repository;

import java.time.LocalDateTime;

public record ShortLink(
		Long id,
		String code,
		String longUrl,
		long clickCount,
		LocalDateTime createdAt,
		LocalDateTime lastClickedAt,
		String tenantId,
		String source,
		LocalDateTime expiresAt,
		LocalDateTime disabledAt) {

	public boolean isDisabled() {
		return disabledAt != null;
	}

	public boolean isExpired(LocalDateTime now) {
		return expiresAt != null && !expiresAt.isAfter(now);
	}
}
