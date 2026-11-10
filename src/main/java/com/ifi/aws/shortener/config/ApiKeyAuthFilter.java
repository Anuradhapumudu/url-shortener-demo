package com.ifi.aws.shortener.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-API-Key";

	private final byte[] masterKey;
	private final Map<String, byte[]> tenantKeyBytes;

	public ApiKeyAuthFilter(String masterApiKey, Map<String, String> tenantKeys) {
		this.masterKey = (masterApiKey != null ? masterApiKey.trim() : "").getBytes(StandardCharsets.UTF_8);
		this.tenantKeyBytes = new HashMap<>();
		if (tenantKeys != null) {
			tenantKeys.forEach((tenantId, key) -> {
				if (key != null && !key.isBlank() && tenantId != null && !tenantId.isBlank()) {
					this.tenantKeyBytes.put(tenantId.trim(), key.trim().getBytes(StandardCharsets.UTF_8));
				}
			});
		}
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		try {
			String supplied = request.getHeader(HEADER);
			if (supplied != null && !supplied.isBlank()) {
				byte[] suppliedBytes = supplied.trim().getBytes(StandardCharsets.UTF_8);
				if (masterKey.length > 0 && MessageDigest.isEqual(masterKey, suppliedBytes)) {
					ApiClientPrincipal principal = new ApiClientPrincipal("master-admin", null, true);
					SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
							principal, null, List.of(new SimpleGrantedAuthority("ROLE_API"), new SimpleGrantedAuthority("ROLE_ADMIN"))));
				} else {
					for (Map.Entry<String, byte[]> entry : tenantKeyBytes.entrySet()) {
						if (MessageDigest.isEqual(entry.getValue(), suppliedBytes)) {
							ApiClientPrincipal principal = new ApiClientPrincipal("tenant-" + entry.getKey(), entry.getKey(), false);
							SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
									principal, null, List.of(new SimpleGrantedAuthority("ROLE_API"), new SimpleGrantedAuthority("ROLE_TENANT"))));
							break;
						}
					}
				}
			}
			chain.doFilter(request, response);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}
}
