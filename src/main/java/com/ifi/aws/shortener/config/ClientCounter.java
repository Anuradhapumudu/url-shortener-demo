package com.ifi.aws.shortener.config;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/** Per-client counter over a fixed one-minute window. */
class ClientCounter {

	private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(1))
			.maximumSize(100_000)
			.build();

	int increment(String client) {
		return counters.get(client, k -> new AtomicInteger()).incrementAndGet();
	}

	int current(String client) {
		AtomicInteger count = counters.getIfPresent(client);
		return count == null ? 0 : count.get();
	}
}
