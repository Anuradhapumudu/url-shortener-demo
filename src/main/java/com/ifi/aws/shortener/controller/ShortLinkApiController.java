package com.ifi.aws.shortener.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ifi.aws.shortener.dto.ShortLinkResponse;
import com.ifi.aws.shortener.dto.ShortenRequest;
import com.ifi.aws.shortener.repository.ShortLink;
import com.ifi.aws.shortener.service.UrlShortenerService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/links")
public class ShortLinkApiController {

	private final UrlShortenerService urlShortenerService;

	public ShortLinkApiController(UrlShortenerService urlShortenerService) {
		this.urlShortenerService = urlShortenerService;
	}

	@PostMapping
	public ResponseEntity<ShortLinkResponse> shorten(@Valid @RequestBody ShortenRequest request) {
		ShortLink link = urlShortenerService.shorten(request.longUrl());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(link));
	}

	@GetMapping
	public List<ShortLinkResponse> recent() {
		return urlShortenerService.recent(20).stream().map(this::toResponse).toList();
	}

	@GetMapping("/summary")
	public Map<String, Long> summary() {
		long[] totals = urlShortenerService.totals();
		return Map.of("totalLinks", totals[0], "totalClicks", totals[1]);
	}

	@GetMapping("/{code}")
	public ResponseEntity<ShortLinkResponse> stats(@PathVariable String code) {
		return urlShortenerService.find(code)
				.map(link -> ResponseEntity.ok(toResponse(link)))
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{code}")
	public ResponseEntity<Void> delete(@PathVariable String code) {
		return urlShortenerService.delete(code)
				? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badUrl(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> invalidRequest(MethodArgumentNotValidException e) {
		return ResponseEntity.badRequest().body(Map.of("error", "longUrl is required (max 2048 characters)"));
	}

	private ShortLinkResponse toResponse(ShortLink link) {
		return new ShortLinkResponse(
				link.code(),
				urlShortenerService.shortUrl(link.code()),
				link.longUrl(),
				link.clickCount(),
				link.createdAt(),
				link.lastClickedAt());
	}
}
