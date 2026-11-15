package net.yozmnews.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 화면용 API 명세의 세션 사용자 인자 — 세션에서 채우는 {@code UserId} 인자는 명세의 요청 인자가 아니다. 알림 목록의 인자는 커서
 * {@code before} 하나뿐이다.
 * <p>
 * 지키는 규칙: user-R6
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class SessionUserApiSpecTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void theSessionUserIsNotARequestParameter() throws Exception {
		JsonNode spec = JsonMapper.builder()
			.build()
			.readTree(this.mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(StandardCharsets.UTF_8));
		List<String> parameters = new ArrayList<>();
		for (JsonNode path : spec.get("paths")) {
			for (JsonNode operation : path) {
				for (JsonNode parameter : operation.path("parameters")) {
					parameters.add(parameter.get("name").asString());
				}
			}
		}

		assertThat(parameters).as("명세의 요청 인자").isNotEmpty().doesNotContain("user");
		assertThat(spec.at("/components/schemas").has("UserId")).as("UserId 스키마").isFalse();
		assertThat(spec.at("/paths/~1api~1notices/get/parameters"))
			.extracting(parameter -> parameter.get("name").asString())
			.containsExactly("before");
	}

}
