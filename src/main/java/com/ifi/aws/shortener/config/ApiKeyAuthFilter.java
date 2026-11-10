package com.ifi.aws.shortener.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Authenticates service-to-service calls that carry the shared key in the X-API-Key header. */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-API-Key";

	private final byte[] expected;

	public ApiKeyAuthFilter(String apiKey) {
		this.expected = apiKey.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String supplied = request.getHeader(HEADER);
		if (expected.length > 0 && supplied != null
				&& MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
			SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
					"api-client", null, List.of(new SimpleGrantedAuthority("ROLE_API"))));
		}
		chain.doFilter(request, response);
	}
}
