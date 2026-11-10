package com.ifi.aws.shortener.service;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ifi.aws.shortener.config.InvalidLinkException;
import com.ifi.aws.shortener.config.ShortLinkProperties;
import com.ifi.aws.shortener.repository.ShortLink;
import com.ifi.aws.shortener.repository.ShortLinkRepository;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class UrlShortenerService {

	private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);

	private static final int MAX_ATTEMPTS = 5;

	/** Omits look-alike characters (0/O, 1/l/I) because codes may be read out or typed. */
	static final char[] CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	/** Words that must never become a code, since they could collide with top-level routes. */
	static final Set<String> RESERVED_CODES = Set.of(
			"admin", "api", "health", "actuator", "index", "static", "login", "logout", "error", "metrics", "swagger");

	/** Preview fetchers, scanners and bots are redirected but not counted as customer clicks. */
	private static final Pattern BOT_USER_AGENT = Pattern.compile(
			"(?i)(bot\\b|bot/|crawler|spider|preview|facebookexternalhit|slurp|slackbot|telegram|skypeuripreview"
					+ "|googleimageproxy|headlesschrome|curl/|wget/|python-requests|java/|go-http-client|monitor|uptime)");

	private final ShortLinkRepository shortLinkRepository;
	private final ShortLinkProperties properties;
	private final MeterRegistry meters;
	private final Cache<String, ShortLink> linkCache;

	public UrlShortenerService(ShortLinkRepository shortLinkRepository, ShortLinkProperties properties,
			MeterRegistry meters) {
		this.shortLinkRepository = shortLinkRepository;
		this.properties = properties;
		this.meters = meters;
		this.linkCache = Caffeine.newBuilder()
				.maximumSize(10_000)
				.expireAfterWrite(Duration.ofSeconds(Math.max(1, properties.linkCacheTtlSeconds())))
				.build();
	}

	public ShortLink shorten(String longUrl) {
		return shorten(longUrl, null, null, null);
	}

	public ShortLink shorten(String longUrl, Integer expiresInDays, String tenantId, String source) {
		validate(longUrl);
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		LocalDateTime expiresAt = expiryFor(expiresInDays, now);

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String code = RandomStringUtils.secure().next(properties.codeLength(), CODE_ALPHABET);
			if (RESERVED_CODES.contains(code.toLowerCase(Locale.ROOT))) {
				continue;
			}
			ShortLink link = new ShortLink(null, code, longUrl.trim(), 0, now, null,
					blankToNull(tenantId), blankToNull(source), expiresAt, null);
			try {
				shortLinkRepository.insert(link);
				meters.counter("shortlink.created").increment();
				return link;
			} catch (DuplicateKeyException e) {
				meters.counter("shortlink.code.collisions").increment();
			}
		}
		throw new IllegalStateException("Could not generate a unique short code");
	}

	private LocalDateTime expiryFor(Integer requestedDays, LocalDateTime now) {
		int days = requestedDays != null ? requestedDays : properties.defaultExpiresInDays();
		return days > 0 ? now.plusDays(days) : null;
	}

	/** Looks up a code (cached) and counts the click when asked to. Counting never blocks the redirect. */
	public Resolution resolve(String code, String userAgent, boolean countClick) {
		ShortLink link = linkCache.getIfPresent(code);
		if (link == null) {
			link = shortLinkRepository.findByCode(code).orElse(null);
			if (link == null) {
				meters.counter("shortlink.redirects", "result", "not_found").increment();
				return Resolution.notFound();
			}
			linkCache.put(code, link);
		}
		if (link.isDisabled() || link.isExpired(LocalDateTime.now(ZoneOffset.UTC))) {
			meters.counter("shortlink.redirects", "result", "gone").increment();
			return Resolution.gone();
		}
		if (countClick && !isBot(userAgent)) {
			recordClickQuietly(code);
		}
		meters.counter("shortlink.redirects", "result", "found").increment();
		return Resolution.found(link.longUrl());
	}

	private void recordClickQuietly(String code) {
		try {
			shortLinkRepository.recordClick(code, LocalDateTime.now(ZoneOffset.UTC));
		} catch (RuntimeException e) {
			meters.counter("shortlink.click.failures").increment();
			log.warn("Could not record click for code {}: {}", code, e.getClass().getSimpleName());
		}
	}

	static boolean isBot(String userAgent) {
		return userAgent != null && BOT_USER_AGENT.matcher(userAgent).find();
	}

	/** Stats lookup; disabled links are treated as gone. */
	public Optional<ShortLink> find(String code) {
		return shortLinkRepository.findByCode(code).filter(l -> !l.isDisabled());
	}

	public List<ShortLink> recent(int limit) {
		return shortLinkRepository.findRecent(limit);
	}

	/** Soft delete. Returns true if an active link was disabled. */
	public boolean delete(String code) {
		boolean disabled = shortLinkRepository.disable(code, LocalDateTime.now(ZoneOffset.UTC));
		linkCache.invalidate(code);
		return disabled;
	}

	public long[] totals() {
		return shortLinkRepository.totals();
	}

	public String shortUrl(String code) {
		return properties.baseUrl() + "/" + code;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void validate(String longUrl) {
		URI uri;
		try {
			uri = new URI(longUrl.trim());
		} catch (URISyntaxException e) {
			throw new InvalidLinkException("Not a valid URL");
		}

		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw new InvalidLinkException("Only http and https URLs can be shortened");
		}
		if (uri.getUserInfo() != null) {
			throw new InvalidLinkException("URLs with embedded credentials are not allowed");
		}

		String host = uri.getHost() == null ? "" : normalizeHost(uri.getHost());
		if (host.isEmpty()) {
			throw new InvalidLinkException("Not a valid URL");
		}
		String ownHost = URI.create(properties.baseUrl()).getHost();
		if (ownHost != null && host.equals(normalizeHost(ownHost))) {
			throw new InvalidLinkException("That's already a short link");
		}

		if (properties.allowedHosts().isEmpty()) {
			return;
		}
		boolean allowed = properties.allowedHosts().stream()
				.anyMatch(h -> host.equals(h) || host.endsWith("." + h));
		if (!allowed) {
			throw new InvalidLinkException("That website is not allowed");
		}
	}

	private String normalizeHost(String host) {
		try {
			return IDN.toASCII(host.toLowerCase(Locale.ROOT).replaceAll("\\.+$", ""));
		} catch (IllegalArgumentException e) {
			throw new InvalidLinkException("Not a valid URL", e);
		}
	}
}
