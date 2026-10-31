package net.yozmnews.platform.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.platform.application.WebSliceSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 계약 확인 엔드포인트 — 웹 계층만 띄운 MockMvc 로 본다.
 * <p>
 * 지키는 규칙: platform-R7
 */
@WebMvcTest(PingController.class)
@Import(WebSliceSecurity.class)
class PingControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void pingRespondsWithPong() throws Exception {
		this.mockMvc.perform(get("/api/ping"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(content().json("""
					{"message":"pong"}
					""", JsonCompareMode.STRICT));
	}

}
