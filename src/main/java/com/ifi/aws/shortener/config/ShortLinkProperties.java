package com.ifi.aws.shortener.config;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param apiKey                   shared secret for /api/**; blank leaves the API open (rejected in prod)
 * @param createRateLimitPerMinute link creations allowed per client per minute (0 disables)
 * @param linkCacheTtlSeconds      how long a code lookup is cached in memory
 * @param defaultExpiresInDays     lifetime applied when a request gives none (0 means never)
 * @param missThrottlePerMinute    unknown-code lookups allowed per client per minute (0 disables)
 */
@ConfigurationProperties(prefix = "short.link")
public record ShortLinkProperties(
		String baseUrl,
		int codeLength,
		List<String> allowedHosts,
		String apiKey,
		@DefaultValue("true") boolean demoUiEnabled,
		@DefaultValue("30") int createRateLimitPerMinute,
		@DefaultValue("30") int linkCacheTtlSeconds,
		@DefaultValue("0") int defaultExpiresInDays,
		@DefaultValue("20") int missThrottlePerMinute) {

	public ShortLinkProperties {
		baseUrl = normalizeBaseUrl(baseUrl);
		apiKey = apiKey == null ? "" : apiKey.trim();
		if (codeLength < 4 || codeLength > 16) {
			throw new IllegalArgumentException("short.link.code-length must be between 4 and 16");
		}
		allowedHosts = allowedHosts == null
				? List.of()
				: allowedHosts.stream().filter(h -> !h.isBlank()).map(ShortLinkProperties::normalizeHost).toList();
	}

	private static String normalizeBaseUrl(String value) {
		String candidate = value == null || value.isBlank()
				? "http://localhost:8090"
				: value.trim().replaceAll("/+$", "");
		URI uri = parseUri(candidate);
		boolean web = uri.getScheme() != null
				&& (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"));
		if (!web || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
				|| uri.getFragment() != null) {
			throw new IllegalArgumentException(
					"short.link.base-url must be an http(s) URL with a host and no credentials, query, or fragment");
		}
		if (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) {
			throw new IllegalArgumentException(
					"short.link.base-url must not contain a path component (use root domain or subdomain)");
		}
		return candidate;
	}

	private static String normalizeHost(String value) {
		String host = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
		if (host.isEmpty() || host.contains("/") || host.contains("@") || host.contains(":")) {
			throw new IllegalArgumentException("short.link.allowed-hosts must contain hostnames only");
		}
		try {
			return IDN.toASCII(host);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid host in short.link.allowed-hosts: " + value, e);
		}
	}

	private static URI parseUri(String value) {
		try {
			return new URI(value);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("short.link.base-url must be a valid URL", e);
		}
	}
}
