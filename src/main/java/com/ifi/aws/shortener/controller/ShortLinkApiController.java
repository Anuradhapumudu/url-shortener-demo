package com.ifi.aws.shortener.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ifi.aws.shortener.dto.ShortLinkResponse;
import com.ifi.aws.shortener.dto.ShortenRequest;
import com.ifi.aws.shortener.repository.ShortLink;
import com.ifi.aws.shortener.service.UrlShortenerService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/links")
public class ShortLinkApiController {

	public record UpdateDestinationRequest(@NotBlank @Size(max = 2048) String longUrl) {}

	private final UrlShortenerService urlShortenerService;

	public ShortLinkApiController(UrlShortenerService urlShortenerService) {
		this.urlShortenerService = urlShortenerService;
	}

	@PostMapping
	public ResponseEntity<ShortLinkResponse> shorten(
			@Valid @RequestBody ShortenRequest request,
			@RequestHeader(value = "X-Tenant-ID", required = false) String headerTenantId) {
		String tenantId = request.tenantId() != null && !request.tenantId().isBlank()
				? request.tenantId()
				: headerTenantId;
		ShortLink link = urlShortenerService.shorten(
				request.longUrl(), request.expiresInDays(), tenantId, request.source(), request.expiresAt());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(link));
	}

	@GetMapping
	public List<ShortLinkResponse> recent(
			@RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
		return urlShortenerService.recent(tenantId, 20).stream().map(this::toResponse).toList();
	}

	@GetMapping("/summary")
	public Map<String, Long> summary(
			@RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
		long[] totals = urlShortenerService.totals(tenantId);
		return Map.of("totalLinks", totals[0], "totalClicks", totals[1]);
	}

	@GetMapping("/{code}")
	public ResponseEntity<ShortLinkResponse> stats(
			@PathVariable String code,
			@RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
		return urlShortenerService.find(code, tenantId)
				.map(link -> ResponseEntity.ok(toResponse(link)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PatchMapping("/{code}")
	public ResponseEntity<ShortLinkResponse> updateDestination(
			@PathVariable String code,
			@Valid @RequestBody UpdateDestinationRequest request,
			@RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
		ShortLink link = urlShortenerService.updateDestination(code, request.longUrl(), tenantId);
		return ResponseEntity.ok(toResponse(link));
	}

	@DeleteMapping("/{code}")
	public ResponseEntity<Void> delete(
			@PathVariable String code,
			@RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
		return urlShortenerService.delete(code, tenantId)
				? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	private ShortLinkResponse toResponse(ShortLink link) {
		return new ShortLinkResponse(
				link.code(),
				urlShortenerService.shortUrl(link.code()),
				link.longUrl(),
				link.clickCount(),
				link.createdAt(),
				link.lastClickedAt(),
				link.tenantId(),
				link.source(),
				link.expiresAt());
	}
}
