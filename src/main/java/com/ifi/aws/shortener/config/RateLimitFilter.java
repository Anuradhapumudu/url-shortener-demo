package com.ifi.aws.shortener.config;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Limits link creation per client. The edge (Cloudflare or the load balancer) should enforce a limit too. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

	private final int limitPerMinute;
	private final ClientCounter counter = new ClientCounter();

	public RateLimitFilter(ShortLinkProperties properties) {
		this.limitPerMinute = properties.createRateLimitPerMinute();
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return limitPerMinute <= 0 || !"POST".equals(request.getMethod())
				|| !request.getRequestURI().startsWith("/api/links");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (counter.increment(request.getRemoteAddr()) > limitPerMinute) {
			TooManyRequests.write(response);
			return;
		}
		chain.doFilter(request, response);
	}
}
