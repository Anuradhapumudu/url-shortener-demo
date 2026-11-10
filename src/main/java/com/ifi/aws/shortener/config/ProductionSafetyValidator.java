package com.ifi.aws.shortener.config;

import java.net.URI;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/** Fails startup in the prod profile when the configuration is unsafe. */
@Component
public class ProductionSafetyValidator {

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
		if (environment.acceptsProfiles(Profiles.of("prod"))) {
			validateForProduction(properties);
		}
	}

	public static void validateForProduction(ShortLinkProperties p) {
		if (p.allowedHosts().isEmpty()) {
			throw new IllegalStateException(
					"short.link.allowed-hosts (SHORT_LINK_ALLOWED_HOSTS) must not be empty in prod: an empty list makes this an open redirector");
		}
		if (p.apiKey().length() < MIN_API_KEY_LENGTH) {
			throw new IllegalStateException(
					"short.link.api-key (SHORT_LINK_API_KEY) must be set to at least " + MIN_API_KEY_LENGTH + " characters in prod");
		}
		if (p.codeLength() < MIN_CODE_LENGTH) {
			throw new IllegalStateException("short.link.code-length must be at least " + MIN_CODE_LENGTH + " in prod");
		}
		if (!"https".equalsIgnoreCase(URI.create(p.baseUrl()).getScheme())) {
			throw new IllegalStateException("short.link.base-url must use https in prod");
		}
	}
}
