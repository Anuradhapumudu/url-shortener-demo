package com.ifi.aws.shortener.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

final class TooManyRequests {

	private TooManyRequests() {
	}

	static void write(HttpServletResponse response) throws IOException {
		response.setStatus(429);
		response.setHeader("Retry-After", "60");
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"Too many requests, slow down\"}");
	}
}
