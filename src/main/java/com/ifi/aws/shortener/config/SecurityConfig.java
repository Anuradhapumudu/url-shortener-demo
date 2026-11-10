package com.ifi.aws.shortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

/** Public: redirects and health probes. The API needs the API key. Everything else is denied. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String CODE_PATH = "/[A-Za-z0-9]{4,16}";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ShortLinkProperties properties) throws Exception {
		boolean apiOpen = properties.apiKey().isEmpty() && properties.tenantKeys().isEmpty();

		http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.headers(h -> h
						.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
						.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
						.frameOptions(f -> f.deny()));
		if (!properties.demoUiEnabled()) {
			http.headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
		} else {
			http.headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
					"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' data: https://www.google.com; connect-src 'self'; frame-ancestors 'none'")));
		}
		http
				.addFilterBefore(new ApiKeyAuthFilter(properties.apiKey(), properties.tenantKeys()), UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> {
					auth.requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, CODE_PATH)).permitAll();
					auth.requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.HEAD, CODE_PATH)).permitAll();
					auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
					if (properties.demoUiEnabled()) {
						auth.requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/qrcode.min.js", "/app.js").permitAll();
						auth.requestMatchers(HttpMethod.HEAD, "/", "/index.html", "/favicon.ico", "/qrcode.min.js", "/app.js").permitAll();
					}
					if (apiOpen) {
						auth.requestMatchers("/api/**").permitAll();
					} else {
						auth.requestMatchers("/api/**").hasRole("API");
					}
					auth.anyRequest().hasRole("API");
				});
		return http.build();
	}
}
