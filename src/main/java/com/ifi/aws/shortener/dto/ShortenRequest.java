package com.ifi.aws.shortener.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShortenRequest(
		@NotBlank @Size(max = 2048) String longUrl,
		@Min(1) @Max(3650) Integer expiresInDays,
		@Size(max = 64) String tenantId,
		@Size(max = 32) String source,
		LocalDateTime expiresAt) {

	public ShortenRequest(String longUrl) {
		this(longUrl, null, null, null, null);
	}

	public ShortenRequest(String longUrl, Integer expiresInDays, String tenantId, String source) {
		this(longUrl, expiresInDays, tenantId, source, null);
	}
}
