package com.ifi.aws.shortener.config;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Throttles clients that keep requesting codes that do not exist, which slows down link enumeration. */
@Component
public class MissThrottleFilter extends OncePerRequestFilter {

	private static final Pattern CODE_PATH = Pattern.compile("/[A-Za-z0-9]{4,16}");

	private final int missesPerMinute;
	private final ClientCounter misses = new ClientCounter();

	public MissThrottleFilter(ShortLinkProperties properties) {
		this.missesPerMinute = properties.missThrottlePerMinute();
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return missesPerMinute <= 0 || !CODE_PATH.matcher(request.getRequestURI()).matches();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String client = request.getRemoteAddr();
		if (misses.current(client) >= missesPerMinute) {
			TooManyRequests.write(response);
			return;
		}
		chain.doFilter(request, response);
		if (response.getStatus() == HttpStatus.NOT_FOUND.value()) {
			misses.increment(client);
		}
	}
}
