package com.ifi.aws.shortener.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ifi.aws.shortener.config.InvalidLinkException;

import io.micrometer.core.instrument.MeterRegistry;

/** Deliberate, non-leaky error responses. Details go to the log, never to the client. */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private final MeterRegistry meters;

	public ApiExceptionHandler(MeterRegistry meters) {
		this.meters = meters;
	}

	@ExceptionHandler(InvalidLinkException.class)
	public ResponseEntity<Map<String, String>> badUrl(InvalidLinkException e) {
		return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class })
	public ResponseEntity<Map<String, String>> invalidRequest(Exception e) {
		return ResponseEntity.badRequest().body(Map.of("error",
				"longUrl is required (max 2048 characters); expiresInDays must be 1 to 3650"));
	}

	/** Code generation exhausted its retries: alert-worthy, but not the client's fault. */
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, String>> unavailable(IllegalStateException e) {
		meters.counter("shortlink.errors", "type", "state").increment();
		log.error("Service could not complete the request", e);
		return serviceUnavailable();
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<Map<String, String>> databaseDown(DataAccessException e) {
		meters.counter("shortlink.errors", "type", "database").increment();
		log.error("Database error: {}", e.getClass().getSimpleName());
		return serviceUnavailable();
	}

	private ResponseEntity<Map<String, String>> serviceUnavailable() {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.header(HttpHeaders.RETRY_AFTER, "5")
				.body(Map.of("error", "Temporarily unavailable, please retry shortly"));
	}
}
