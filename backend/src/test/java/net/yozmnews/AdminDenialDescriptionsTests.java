package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 화면용 API 명세의 운영자 경로 거부 응답 — 앱의 운영자 처리기에서 명세를 읽어, 401 · 403 설명을 손으로 적은 문구와 견준다. 변경 메서드의
 * 403 에는 CSRF 의 까닭이 앞선다(서버의 판정 순서).
 * <p>
 * 지키는 규칙: platform-R6 · account-R1 · account-R3
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = YozmnewsApplication.class, properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class AdminDenialDescriptionsTests {

	private static final String UNAUTHORIZED = "세션이 없거나 만료됐다 · 운영자 토큰(Bearer)이 맞지 않는다";

	private static final String READ_FORBIDDEN = "비밀번호를 바꿔야 한다 · 운영자 권한이 없다";

	private static final String CHANGE_FORBIDDEN = "요청 보호 토큰이 없거나 맞지 않는다 · 비밀번호를 바꿔야 한다 · 운영자 권한이 없다";

	@Autowired
	MockMvc mockMvc;

	@Test
	void readingOperationsCarryTheSessionTokenPasswordAndRoleReasons() throws Exception {
		JsonNode paths = paths();

		assertThat(description(paths, "/api/admin/presets", "get", "401")).isEqualTo(UNAUTHORIZED);
		assertThat(description(paths, "/api/admin/presets", "get", "403")).isEqualTo(READ_FORBIDDEN);
	}

	@Test
	void changingOperationsPutTheCsrfReasonFirst() throws Exception {
		JsonNode paths = paths();

		assertThat(description(paths, "/api/admin/presets/{key}/registration", "post", "401")).isEqualTo(UNAUTHORIZED);
		assertThat(description(paths, "/api/admin/presets/{key}/registration", "post", "403"))
			.isEqualTo(CHANGE_FORBIDDEN);
		assertThat(description(paths, "/api/admin/feeds/{id}/poll-interval", "put", "403")).isEqualTo(CHANGE_FORBIDDEN);
		assertThat(description(paths, "/api/admin/feeds/{id}", "delete", "403")).isEqualTo(CHANGE_FORBIDDEN);
	}

	private JsonNode paths() throws Exception {
		String spec = this.mockMvc.perform(get("/v3/api-docs"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		return JsonMapper.builder().build().readTree(spec).path("paths");
	}

	private static String description(JsonNode paths, String path, String method, String status) {
		return paths.path(path).path(method).path("responses").path(status).path("description").asString();
	}

}
