package com.ifi.aws.shortener.config;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ProductionSafetyValidator {

	private static final Logger log = LoggerFactory.getLogger(ProductionSafetyValidator.class);

	static final int MIN_API_KEY_LENGTH = 32;
	static final int MIN_CODE_LENGTH = 8;

	private final Environment environment;
	private final ShortLinkProperties properties;

	public ProductionSafetyValidator(Environment environment, ShortLinkProperties properties) {
		this.environment = environment;
		this.properties = properties;
	}

	@PostConstruct
	void validateOnStartup() {
		boolean isProdProfile = environment.acceptsProfiles(Profiles.of("prod", "production"));
		if (isProdProfile || properties.productionMode()) {
			validateForProduction(properties);
		} else if (isPublicDomain(properties.baseUrl())) {
			log.warn("short.link.base-url ('{}') points to a non-local host, but active profile is not 'prod'. Ensure production profile is activated before live deployment.", properties.baseUrl());
		}
	}

	public static boolean isPublicDomain(String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank()) return false;
		String host = URI.create(baseUrl).getHost();
		if (host == null) return false;
		return !host.equalsIgnoreCase("localhost")
				&& !host.equals("127.0.0.1")
				&& !host.endsWith(".local")
				&& !host.endsWith(".test");
	}

	public static void validateForProduction(ShortLinkProperties p) {
		if (p.allowedHosts().isEmpty()) {
			throw new IllegalStateException(
					"short.link.allowed-hosts (SHORT_LINK_ALLOWED_HOSTS) must not be empty in production: an empty list makes this an open redirector");
		}
		if (p.apiKey().length() < MIN_API_KEY_LENGTH) {
			throw new IllegalStateException(
					"short.link.api-key (SHORT_LINK_API_KEY) must be set to at least " + MIN_API_KEY_LENGTH + " characters in production");
		}
		if (p.codeLength() < MIN_CODE_LENGTH) {
			throw new IllegalStateException("short.link.code-length must be at least " + MIN_CODE_LENGTH + " in production");
		}
		if (!"https".equalsIgnoreCase(URI.create(p.baseUrl()).getScheme())) {
			throw new IllegalStateException("short.link.base-url must use https in production");
		}
	}
}
