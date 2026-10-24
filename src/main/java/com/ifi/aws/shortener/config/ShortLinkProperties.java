package com.ifi.aws.shortener.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short.link")
public record ShortLinkProperties(String baseUrl, int codeLength, List<String> allowedHosts) {

	public ShortLinkProperties {
		allowedHosts = allowedHosts == null ? List.of() : allowedHosts.stream().filter(h -> !h.isBlank()).toList();
	}
}
