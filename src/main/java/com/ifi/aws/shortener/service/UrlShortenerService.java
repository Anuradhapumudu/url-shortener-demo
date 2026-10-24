package com.ifi.aws.shortener.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.ifi.aws.shortener.config.ShortLinkProperties;
import com.ifi.aws.shortener.repository.ShortLink;
import com.ifi.aws.shortener.repository.ShortLinkRepository;

@Service
public class UrlShortenerService {

	private static final int MAX_ATTEMPTS = 5;

	private final ShortLinkRepository shortLinkRepository;
	private final ShortLinkProperties properties;

	public UrlShortenerService(ShortLinkRepository shortLinkRepository, ShortLinkProperties properties) {
		this.shortLinkRepository = shortLinkRepository;
		this.properties = properties;
	}

	/** Drop-in replacement for the Bitly call: give it a long URL, get back the stored short link. */
	public ShortLink shorten(String longUrl) {
		validate(longUrl);

		// 62^7 ≈ 3.5 trillion codes, so a collision is extremely unlikely; the unique key + retry covers it anyway
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String code = RandomStringUtils.secure().nextAlphanumeric(properties.codeLength());
			try {
				shortLinkRepository.insert(code, longUrl, LocalDateTime.now());
				return shortLinkRepository.findByCode(code).orElseThrow();
			} catch (DuplicateKeyException e) {
				// code already taken, try another one
			}
		}
		throw new IllegalStateException("Could not generate a unique short code");
	}

	/** Returns the long URL for a code and counts the click. */
	public Optional<String> resolve(String code) {
		Optional<ShortLink> link = shortLinkRepository.findByCode(code);
		link.ifPresent(l -> shortLinkRepository.recordClick(code, LocalDateTime.now()));
		return link.map(ShortLink::longUrl);
	}

	public Optional<ShortLink> find(String code) {
		return shortLinkRepository.findByCode(code);
	}

	public List<ShortLink> recent(int limit) {
		return shortLinkRepository.findRecent(limit);
	}

	public boolean delete(String code) {
		return shortLinkRepository.delete(code);
	}

	public long[] totals() {
		return shortLinkRepository.totals();
	}

	public String shortUrl(String code) {
		return properties.baseUrl() + "/" + code;
	}

	private void validate(String longUrl) {
		URI uri;
		try {
			uri = new URI(longUrl.trim());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Not a valid URL");
		}

		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw new IllegalArgumentException("Only http and https URLs can be shortened");
		}

		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		if (host.isEmpty()) {
			throw new IllegalArgumentException("Not a valid URL");
		}
		if (host.equals(URI.create(properties.baseUrl()).getHost().toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("That's already a short link");
		}

		// Empty allow-list = open shortener (any site); otherwise only the listed hosts
		if (properties.allowedHosts().isEmpty()) {
			return;
		}
		boolean allowed = properties.allowedHosts().stream()
				.map(h -> h.toLowerCase(Locale.ROOT))
				.anyMatch(h -> host.equals(h) || host.endsWith("." + h));
		if (!allowed) {
			throw new IllegalArgumentException("Host '" + host + "' is not allowed. Allowed: " + properties.allowedHosts());
		}
	}
}
