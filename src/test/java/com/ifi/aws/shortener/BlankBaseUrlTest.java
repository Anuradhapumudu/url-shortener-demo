package com.ifi.aws.shortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** An empty SHORT_LINK_BASEURL used to crash shortening with a 500. */
@SpringBootTest(properties = { "short.link.base-url=", "short.link.allowed-hosts=" })
@AutoConfigureMockMvc
class BlankBaseUrlTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void shortensWithDefaultBaseUrl() throws Exception {
		mockMvc.perform(post("/api/links")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"https://www.example.com/page\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shortUrl").value(org.hamcrest.Matchers.startsWith("http://localhost:8090/")));
	}
}
