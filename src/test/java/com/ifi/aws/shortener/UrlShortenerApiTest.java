package com.ifi.aws.shortener;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerApiTest {

	private static final String PORTAL_URL = "https://progress.workroo.click?code=Xy7Pq2Lm9Rt4Vb8Nc3Kd6Fh1Jw5Gs0Az&account=acme";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void shortenThenRedirectCountsClicks() throws Exception {
		String code = shorten(PORTAL_URL);
		assertThat(code).hasSize(7).matches("[A-Za-z0-9]+");

		mockMvc.perform(get("/" + code))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", PORTAL_URL))
				.andExpect(header().string("Cache-Control", "no-store"));
		mockMvc.perform(get("/" + code)).andExpect(status().isFound());

		mockMvc.perform(get("/api/links/" + code))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clickCount").value(2))
				.andExpect(jsonPath("$.shortUrl").value("http://localhost:8090/" + code))
				.andExpect(jsonPath("$.lastClickedAt").isNotEmpty());
	}

	@Test
	void everyShortenGivesADifferentCode() throws Exception {
		assertThat(shorten(PORTAL_URL)).isNotEqualTo(shorten(PORTAL_URL));
	}

	@Test
	void summaryCountsLinksAndClicks() throws Exception {
		String code = shorten(PORTAL_URL);
		mockMvc.perform(get("/" + code)).andExpect(status().isFound());

		mockMvc.perform(get("/api/links/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalLinks").isNumber())
				.andExpect(jsonPath("$.totalClicks").isNumber());
	}

	@Test
	void deletedLinkStopsRedirecting() throws Exception {
		String code = shorten(PORTAL_URL);

		mockMvc.perform(delete("/api/links/" + code)).andExpect(status().isNoContent());
		mockMvc.perform(get("/" + code)).andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/links/" + code)).andExpect(status().isNotFound());
	}

	@Test
	void unknownCodeReturns404() throws Exception {
		mockMvc.perform(get("/Zz99Zz9")).andExpect(status().isNotFound());
	}

	@Test
	void rejectsHostsThatAreNotAllowed() throws Exception {
		mockMvc.perform(post("/api/links")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"https://evil-phishing.example/login\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void rejectsShorteningAShortLink() throws Exception {
		mockMvc.perform(post("/api/links")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"http://localhost:8090/Ab3xK9q\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("That's already a short link"));
	}

	@Test
	void rejectsNonHttpSchemes() throws Exception {
		mockMvc.perform(post("/api/links")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"javascript:alert(1)\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsEmptyUrl() throws Exception {
		mockMvc.perform(post("/api/links")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

	private String shorten(String longUrl) throws Exception {
		String body = mockMvc.perform(post("/api/links")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("longUrl", longUrl))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		JsonNode json = objectMapper.readTree(body);
		return json.get("code").asText();
	}
}
