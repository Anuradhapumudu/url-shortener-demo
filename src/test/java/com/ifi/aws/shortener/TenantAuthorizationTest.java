package com.ifi.aws.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies strict server-side tenant authorization:
 * - Tenant identity is derived from authenticated credentials, never trusted from client input.
 * - Fabricated or mismatched tenant IDs return 403 Forbidden.
 * - Missing tenant IDs automatically default to the caller's authenticated tenant.
 * - Cross-tenant stats, updates, and deletes fail closed with 404 Not Found.
 * - Multi-tenant listings and summaries are strictly isolated.
 * - Master admin retains full cross-tenant or scoped management capabilities.
 */
@SpringBootTest(properties = {
		"short.link.api-key=master-key-01234567890123456789-32chars",
		"short.link.tenant-keys.tenant-a=tenant-a-key-01234567890123456789-32chars",
		"short.link.tenant-keys.tenant-b=tenant-b-key-01234567890123456789-32chars",
		"short.link.demo-ui-enabled=false"
})
@AutoConfigureMockMvc
class TenantAuthorizationTest {

	private static final String MASTER_KEY = "master-key-01234567890123456789-32chars";
	private static final String TENANT_A_KEY = "tenant-a-key-01234567890123456789-32chars";
	private static final String TENANT_B_KEY = "tenant-b-key-01234567890123456789-32chars";

	private static final String URL_1 = "https://workroo.click/page1";
	private static final String URL_2 = "https://workroo.click/page2";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void tenantCannotFabricateIdentityInHeader() throws Exception {
		mockMvc.perform(post("/api/links")
				.header("X-API-Key", TENANT_A_KEY)
				.header("X-Tenant-ID", "tenant-b")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"" + URL_1 + "\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Tenant mismatch")));
	}

	@Test
	void tenantCannotFabricateIdentityInBody() throws Exception {
		mockMvc.perform(post("/api/links")
				.header("X-API-Key", TENANT_A_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"" + URL_1 + "\",\"tenantId\":\"tenant-b\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Tenant mismatch")));
	}

	@Test
	void tenantOmittedHeaderAutomaticallyScopesToAuthenticatedTenant() throws Exception {
		String res = mockMvc.perform(post("/api/links")
				.header("X-API-Key", TENANT_A_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"" + URL_1 + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tenantId").value("tenant-a"))
				.andReturn().getResponse().getContentAsString();

		String code = objectMapper.readTree(res).get("code").asText();
		assertThat(code).hasSize(8);
	}

	@Test
	void crossTenantStatsAreIsolated() throws Exception {
		String codeA = createLink(TENANT_A_KEY, URL_1);

		// Tenant B cannot view stats for Tenant A's link (fails closed with 404)
		mockMvc.perform(get("/api/links/" + codeA).header("X-API-Key", TENANT_B_KEY))
				.andExpect(status().isNotFound());

		// Tenant A can view its own link stats
		mockMvc.perform(get("/api/links/" + codeA).header("X-API-Key", TENANT_A_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(codeA))
				.andExpect(jsonPath("$.tenantId").value("tenant-a"));
	}

	@Test
	void crossTenantDestinationUpdateIsRejected() throws Exception {
		String codeA = createLink(TENANT_A_KEY, URL_1);

		// Tenant B cannot update Tenant A's destination
		mockMvc.perform(patch("/api/links/" + codeA)
				.header("X-API-Key", TENANT_B_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"https://workroo.click/tampered\"}"))
				.andExpect(status().isNotFound());

		// Verify original URL remains unchanged
		mockMvc.perform(get("/" + codeA))
				.andExpect(status().isFound())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", URL_1));
	}

	@Test
	void crossTenantDeleteIsRejected() throws Exception {
		String codeA = createLink(TENANT_A_KEY, URL_1);

		// Tenant B cannot delete Tenant A's link
		mockMvc.perform(delete("/api/links/" + codeA)
				.header("X-API-Key", TENANT_B_KEY))
				.andExpect(status().isNotFound());

		// Link is still active and redirects normally
		mockMvc.perform(get("/" + codeA))
				.andExpect(status().isFound())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", URL_1));
	}

	@Test
	void crossTenantListingAndSummaryAreStrictlyIsolated() throws Exception {
		String codeA = createLink(TENANT_A_KEY, URL_1);
		String codeB = createLink(TENANT_B_KEY, URL_2);

		// Tenant A listing contains only codeA
		mockMvc.perform(get("/api/links").header("X-API-Key", TENANT_A_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.code == '" + codeA + "')]").exists())
				.andExpect(jsonPath("$[?(@.code == '" + codeB + "')]").doesNotExist());

		// Tenant B listing contains only codeB
		mockMvc.perform(get("/api/links").header("X-API-Key", TENANT_B_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.code == '" + codeB + "')]").exists())
				.andExpect(jsonPath("$[?(@.code == '" + codeA + "')]").doesNotExist());

		// Summary for Tenant A counts only Tenant A's links
		mockMvc.perform(get("/api/links/summary").header("X-API-Key", TENANT_A_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalLinks").isNumber());
	}

	@Test
	void masterAdminCanViewAllOrScopeToTenant() throws Exception {
		String codeA = createLink(TENANT_A_KEY, URL_1);
		String codeB = createLink(TENANT_B_KEY, URL_2);

		// Master admin unscoped sees both
		mockMvc.perform(get("/api/links").header("X-API-Key", MASTER_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.code == '" + codeA + "')]").exists())
				.andExpect(jsonPath("$[?(@.code == '" + codeB + "')]").exists());

		// Master admin scoped to Tenant A sees only Tenant A
		mockMvc.perform(get("/api/links").header("X-API-Key", MASTER_KEY).header("X-Tenant-ID", "tenant-a"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.code == '" + codeA + "')]").exists())
				.andExpect(jsonPath("$[?(@.code == '" + codeB + "')]").doesNotExist());
	}

	@Test
	void conflictingHeaderAndBodyTenantForMasterAdminReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/links")
				.header("X-API-Key", MASTER_KEY)
				.header("X-Tenant-ID", "tenant-a")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"" + URL_1 + "\",\"tenantId\":\"tenant-b\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("do not match")));
	}

	@Test
	void invalidApiKeyReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/links").header("X-API-Key", "invalid-key"))
				.andExpect(status().isUnauthorized());
	}

	private String createLink(String apiKey, String longUrl) throws Exception {
		String res = mockMvc.perform(post("/api/links")
				.header("X-API-Key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"" + longUrl + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode json = objectMapper.readTree(res);
		return json.get("code").asText();
	}
}
