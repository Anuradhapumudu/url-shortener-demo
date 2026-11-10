package com.ifi.aws.shortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/** With an API key configured, only the redirect and health are public. */
@SpringBootTest(properties = { "short.link.api-key=test-key-0123456789-test-key-0123456789", "short.link.demo-ui-enabled=false" })
@AutoConfigureMockMvc
class SecurityTest {

	private static final String KEY = "test-key-0123456789-test-key-0123456789";
	private static final String BODY = "{\"longUrl\":\"https://progress.workroo.click/p?code=abc\"}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void apiRejectsMissingOrWrongKey() throws Exception {
		mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/links").header("X-API-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/links")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/links/summary")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/links/Ab3xK9q")).andExpect(status().isUnauthorized());
	}

	@Test
	void apiAcceptsCorrectKeyAndRedirectStaysPublic() throws Exception {
		String response = mockMvc.perform(post("/api/links").header("X-API-Key", KEY)
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String code = objectMapper.readTree(response).get("code").asText();

		mockMvc.perform(get("/" + code))
				.andExpect(status().isFound())
				.andExpect(header().string("Referrer-Policy", "no-referrer"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mockMvc.perform(get("/api/links").header("X-API-Key", KEY)).andExpect(status().isOk());
	}

	@Test
	void healthIsPublicButOtherActuatorEndpointsAreNot() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
		mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
	}

	@Test
	void demoPageIsNotServedWhenDisabled() throws Exception {
		mockMvc.perform(get("/index.html")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/")).andExpect(status().isUnauthorized());
	}
}
