package com.ifi.aws.shortener.controller;

import java.net.URI;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.ifi.aws.shortener.service.UrlShortenerService;

/**
 * The part the customer hits when they tap the SMS link.
 * The regex keeps this from catching static files like /index.html or /favicon.ico.
 */
@RestController
public class RedirectController {

	private final UrlShortenerService urlShortenerService;

	public RedirectController(UrlShortenerService urlShortenerService) {
		this.urlShortenerService = urlShortenerService;
	}

	@GetMapping("/{code:[A-Za-z0-9]{4,16}}")
	public ResponseEntity<String> redirect(@PathVariable String code) {
		return urlShortenerService.resolve(code)
				.map(longUrl -> ResponseEntity.status(HttpStatus.FOUND)
						.location(URI.create(longUrl))
						// no-store so every tap reaches us and gets counted
						.cacheControl(CacheControl.noStore())
						.<String>build())
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.contentType(MediaType.TEXT_PLAIN)
						.body("This link doesn't exist or has expired."));
	}
}
