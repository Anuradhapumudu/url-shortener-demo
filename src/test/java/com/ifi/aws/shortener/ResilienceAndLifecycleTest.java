package com.ifi.aws.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifi.aws.shortener.repository.ShortLinkRepository;
import com.ifi.aws.shortener.service.UrlShortenerService;

@SpringBootTest
@AutoConfigureMockMvc
class ResilienceAndLifecycleTest {

	private static final String URL = "https://progress.workroo.click/p?code=abc";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private UrlShortenerService service;
	@MockitoSpyBean
	private ShortLinkRepository repository;

	@AfterEach
	void resetSpy() {
		reset(repository);
	}

	@Test
	void redirectSurvivesAClickCountingFailure() throws Exception {
		String code = shorten("{\"longUrl\":\"" + URL + "\"}");
		doThrow(new DataAccessResourceFailureException("db down")).when(repository).recordClick(anyString(), any());

		mockMvc.perform(get("/" + code)).andExpect(status().isFound()).andExpect(header().string("Location", URL));
	}

	@Test
	void databaseOutageOnLookupIsA503NotAStackTrace() throws Exception {
		doThrow(new DataAccessResourceFailureException("db down")).when(repository).findByCode(anyString());

		mockMvc.perform(get("/Zz99Zz9")).andExpect(status().isServiceUnavailable());
	}

	@Test
	void botsAreRedirectedButNotCounted() throws Exception {
		String code = shorten("{\"longUrl\":\"" + URL + "\"}");
		mockMvc.perform(get("/" + code).header("User-Agent", "WhatsApp link preview bot/2.0")).andExpect(status().isFound());
		mockMvc.perform(get("/" + code).header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)")).andExpect(status().isFound());

		long count = 0;
		for (int i = 0; i < 40; i++) {
			String res = mockMvc.perform(get("/api/links/" + code)).andReturn().getResponse().getContentAsString();
			count = objectMapper.readTree(res).get("clickCount").asLong();
			if (count == 1) break;
			Thread.sleep(50);
		}
		assertThat(count).isEqualTo(1);
	}

	@Test
	void expiredLinksReturn410() throws Exception {
		String code = shorten("{\"longUrl\":\"" + URL + "\",\"expiresInDays\":7,\"tenantId\":\"acme\",\"source\":\"quote\"}");
		mockMvc.perform(get("/api/links/" + code))
				.andExpect(jsonPath("$.tenantId").value("acme"))
				.andExpect(jsonPath("$.source").value("quote"))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty());
		mockMvc.perform(get("/" + code)).andExpect(status().isFound());

		// A link that is already past its expiry when first looked up (the lookup cache is keyed per code)
		String fresh = shorten("{\"longUrl\":\"" + URL + "\",\"expiresInDays\":1}");
		jdbc.update("UPDATE short_link SET expires_at = TIMESTAMPADD(DAY, -1, CURRENT_TIMESTAMP) WHERE code = ?", fresh);
		mockMvc.perform(get("/" + fresh)).andExpect(status().isGone());
	}

	@Test
	void badStoredUrlIsA404NotA500() throws Exception {
		jdbc.update("INSERT INTO short_link (code, long_url, click_count, created_at) VALUES ('BadRow1', 'not a url', 0, CURRENT_TIMESTAMP)");
		mockMvc.perform(get("/BadRow1")).andExpect(status().isNotFound());
		jdbc.update("INSERT INTO short_link (code, long_url, click_count, created_at) VALUES ('BadRow2', 'javascript:alert(1)', 0, CURRENT_TIMESTAMP)");
		mockMvc.perform(get("/BadRow2")).andExpect(status().isNotFound());
	}

	@Test
	void headAndPrefetchRequestsAreNotCounted() throws Exception {
		String code = shorten("{\"longUrl\":\"" + URL + "\"}");
		mockMvc.perform(head("/" + code)).andExpect(status().isFound());
		mockMvc.perform(get("/" + code).header("Sec-Purpose", "prefetch")).andExpect(status().isFound());
		mockMvc.perform(get("/" + code)).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));

		long count = 0;
		for (int i = 0; i < 40; i++) {
			String res = mockMvc.perform(get("/api/links/" + code)).andReturn().getResponse().getContentAsString();
			count = objectMapper.readTree(res).get("clickCount").asLong();
			if (count == 1) break;
			Thread.sleep(50);
		}
		assertThat(count).isEqualTo(1);
	}

	@Test
	void invalidExpiryIsRejected() throws Exception {
		mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"" + URL + "\",\"expiresInDays\":0}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void codesAvoidLookAlikeCharacters() throws Exception {
		for (int i = 0; i < 50; i++) {
			assertThat(service.shorten(URL).code()).hasSize(8).doesNotContainPattern("[0OIl1]");
		}
	}

	@Test
	void concurrentClicksAreAllCounted() throws Exception {
		String code = shorten("{\"longUrl\":\"" + URL + "\"}");
		int clicks = 50;
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
			for (int i = 0; i < clicks; i++) {
				futures.add(pool.submit(() -> {
					try {
						mockMvc.perform(get("/" + code)).andExpect(status().isFound());
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}));
			}
			for (var f : futures) {
				f.get(30, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}
		long count = 0;
		for (int i = 0; i < 60; i++) {
			String res = mockMvc.perform(get("/api/links/" + code)).andReturn().getResponse().getContentAsString();
			count = objectMapper.readTree(res).get("clickCount").asLong();
			if (count == clicks) break;
			Thread.sleep(50);
		}
		assertThat(count).isEqualTo(clicks);
	}

	
	@Test
	void updateDestinationChangesRedirectAndEvictsCache() throws Exception {
		String code = shorten("{\"longUrl\":\"" + URL + "\"}");
		mockMvc.perform(get("/" + code)).andExpect(status().isFound()).andExpect(header().string("Location", URL));

		String newUrl = "https://progress.workroo.click/updated";
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/links/" + code)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"" + newUrl + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.longUrl").value(newUrl));

		mockMvc.perform(get("/" + code)).andExpect(status().isFound()).andExpect(header().string("Location", newUrl));
	}

	@Test
	void tenantIsolationPreventsCrossTenantAccess() throws Exception {
		String code = shorten("{\"longUrl\":\"" + URL + "\",\"tenantId\":\"tenant-alpha\"}");

		// Listing for tenant-beta does not include tenant-alpha's link
		mockMvc.perform(get("/api/links").header("X-Tenant-ID", "tenant-beta"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.code == '" + code + "')]").doesNotExist());

		// Deleting with tenant-beta fails (link belongs to tenant-alpha)
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/links/" + code)
				.header("X-Tenant-ID", "tenant-beta"))
				.andExpect(status().isNotFound());

		// Deleting with correct tenant succeeds
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/links/" + code)
				.header("X-Tenant-ID", "tenant-alpha"))
				.andExpect(status().isNoContent());
	}

	
	@Test
	void crossTenantStatsAndSummaryIsolation() throws Exception {
		String codeA = shorten("{\"longUrl\":\"" + URL + "\",\"tenantId\":\"tenant-a\"}");
		String codeB = shorten("{\"longUrl\":\"" + URL + "\",\"tenantId\":\"tenant-b\"}");

		// Stats lookup scoped to tenant-a can access codeA, but not codeB
		mockMvc.perform(get("/api/links/" + codeA).header("X-Tenant-ID", "tenant-a"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/links/" + codeB).header("X-Tenant-ID", "tenant-a"))
				.andExpect(status().isNotFound());

		// Summary scoped to tenant-a counts only tenant-a links
		mockMvc.perform(get("/api/links/summary").header("X-Tenant-ID", "tenant-a"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalLinks").value(1));
	}

	@Test
	void expiresAtBeyondTenYearsIsRejected() throws Exception {
		mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"" + URL + "\",\"expiresAt\":\"2045-01-01T00:00:00\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("10 years")));
	}

	private String shorten(String json) throws Exception {
		String body = mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("code").asText();
	}
}
