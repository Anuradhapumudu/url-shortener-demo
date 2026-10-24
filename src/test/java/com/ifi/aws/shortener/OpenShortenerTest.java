package com.ifi.aws.shortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** With an empty allow-list, any http/https site can be shortened. */
@SpringBootTest(properties = "short.link.allowed-hosts=")
@AutoConfigureMockMvc
class OpenShortenerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void shortensAnySite() throws Exception {
		mockMvc.perform(post("/api/links")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=abc\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void stillRejectsNonHttpSchemes() throws Exception {
		mockMvc.perform(post("/api/links")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"longUrl\":\"javascript:alert(1)\"}"))
				.andExpect(status().isBadRequest());
	}
}
