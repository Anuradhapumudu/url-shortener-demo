package com.ifi.aws.shortener.controller;

import java.net.URI;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.ifi.aws.shortener.service.Resolution;
import com.ifi.aws.shortener.service.UrlShortenerService;

import jakarta.servlet.http.HttpServletRequest;

/** What the customer hits when they tap the SMS link. The regex keeps static files like /index.html out. */
@RestController
public class RedirectController {

	private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

	private final UrlShortenerService urlShortenerService;

	public RedirectController(UrlShortenerService urlShortenerService) {
		this.urlShortenerService = urlShortenerService;
	}

	@GetMapping("/{code:[A-Za-z0-9]{4,16}}")
	public ResponseEntity<String> redirect(@PathVariable String code, HttpServletRequest request) {
		Resolution resolution = urlShortenerService.resolve(
				code, request.getHeader(HttpHeaders.USER_AGENT), isRealVisit(request));
		return switch (resolution.status()) {
			case FOUND -> redirectTo(code, resolution.longUrl());
			case GONE -> text(HttpStatus.GONE, "This link has expired or is no longer available.");
			case NOT_FOUND -> text(HttpStatus.NOT_FOUND, "This link doesn't exist.");
		};
	}

	/** HEAD probes and browser prefetches are not a person opening the link. */
	private static boolean isRealVisit(HttpServletRequest request) {
		if (HttpMethod.HEAD.matches(request.getMethod())) {
			return false;
		}
		String purpose = request.getHeader("Sec-Purpose");
		if (purpose == null) {
			purpose = request.getHeader("Purpose");
		}
		return purpose == null || !purpose.toLowerCase(Locale.ROOT).contains("prefetch");
	}

	/** A bad stored row must not become a 500 or a non-web redirect. */
	private ResponseEntity<String> redirectTo(String code, String longUrl) {
		try {
			URI target = URI.create(longUrl);
			String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase(Locale.ROOT);
			if ((scheme.equals("http") || scheme.equals("https")) && target.getHost() != null) {
				return ResponseEntity.status(HttpStatus.FOUND)
						.location(target)
						.cacheControl(CacheControl.noStore())
						.header("X-Robots-Tag", "noindex, nofollow")
						.<String>build();
			}
		} catch (IllegalArgumentException e) {
			// handled below
		}
		log.error("Stored target for code {} is not a valid http(s) URL; refusing to redirect", code);
		return text(HttpStatus.NOT_FOUND, "This link doesn't exist.");
	}

	private static ResponseEntity<String> text(HttpStatus status, String body) {
		return ResponseEntity.status(status)
				.contentType(MediaType.TEXT_PLAIN)
				.cacheControl(CacheControl.noStore())
				.body(body);
	}
}
