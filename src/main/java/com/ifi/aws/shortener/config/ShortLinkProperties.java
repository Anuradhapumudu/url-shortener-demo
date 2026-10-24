package com.ifi.aws.shortener.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short.link")
public record ShortLinkProperties(String baseUrl, int codeLength, List<String> allowedHosts) {

	public ShortLinkProperties {
		// An env var that exists but is empty (e.g. on Render) overrides the default, so fall back here too
		baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8090" : baseUrl.trim().replaceAll("/+$", "");
		allowedHosts = allowedHosts == null ? List.of() : allowedHosts.stream().filter(h -> !h.isBlank()).toList();
	}
}
