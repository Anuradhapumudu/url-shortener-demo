package com.ifi.aws.shortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "short.link.miss-throttle-per-minute=3")
@AutoConfigureMockMvc
class MissThrottleTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void repeatedUnknownCodesGetThrottled() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(get("/Nope000" + i)).andExpect(status().isNotFound());
		}
		mockMvc.perform(get("/Nope0009"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "60"));
	}
}
