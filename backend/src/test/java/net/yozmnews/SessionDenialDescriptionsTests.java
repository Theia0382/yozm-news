package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import net.yozmnews.platform.AllowedBeforePasswordChange;
import net.yozmnews.platform.AllowedWithoutLlmKey;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 화면용 API 명세의 세션 경로 거부 응답 — 표시의 조합마다 다른 시험용 처리기를 더한 앱에서 명세를 읽어, 401 의 유무와 403 설명의 까닭 조합을
 * 손으로 적은 문구와 견준다. 앱의 실제 세션 처리기는 모두 키 없이 허용 표시가 있어 키의 까닭은 이 시험 처리기에서만 나타난다.
 * <p>
 * 지키는 규칙: platform-R6 · platform-R21
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = { YozmnewsApplication.class, SessionDenialDescriptionsTests.MarksController.class },
		properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class SessionDenialDescriptionsTests {

	private static final String CSRF = "요청 보호 토큰이 없거나 맞지 않는다";

	private static final String PASSWORD = "비밀번호를 바꿔야 한다";

	private static final String KEY = "LLM 키를 등록해야 한다";

	@Autowired
	MockMvc mockMvc;

	@Test
	void aHandlerWithoutMarksCarriesEveryReason() throws Exception {
		JsonNode paths = paths();

		assertThat(description(paths, "/api/test/marks/plain", "get", "401")).isEqualTo("세션이 없거나 만료됐다");
		assertThat(description(paths, "/api/test/marks/plain", "get", "403")).isEqualTo(PASSWORD + " · " + KEY);
		assertThat(description(paths, "/api/test/marks/plain", "post", "403"))
			.isEqualTo(CSRF + " · " + PASSWORD + " · " + KEY);
	}

	@Test
	void eachMarkRemovesItsOwnReason() throws Exception {
		JsonNode paths = paths();

		assertThat(description(paths, "/api/test/marks/without-key", "get", "403")).isEqualTo(PASSWORD);
		assertThat(description(paths, "/api/test/marks/before-change", "get", "403")).isEqualTo(KEY);
		assertThat(description(paths, "/api/test/marks/both", "post", "403")).isEqualTo(CSRF);
		assertThat(paths.at("/~1api~1test~1marks~1both/get/responses").has("403")).isFalse();
		assertThat(paths.at("/~1api~1test~1marks~1both/get/responses").has("401")).isTrue();
	}

	@Test
	void aPublicHandlerCarriesOnlyTheCsrfReasonOnChangingMethods() throws Exception {
		JsonNode paths = paths();

		assertThat(paths.at("/~1api~1test~1marks~1public/get/responses").has("401")).isFalse();
		assertThat(paths.at("/~1api~1test~1marks~1public/get/responses").has("403")).isFalse();
		assertThat(paths.at("/~1api~1test~1marks~1public/post/responses").has("401")).isFalse();
		assertThat(description(paths, "/api/test/marks/public", "post", "403")).isEqualTo(CSRF);
	}

	@Test
	void theAppsOwnSessionHandlersAreAllOpenWithoutAKey() throws Exception {
		String spec = body("/v3/api-docs");
		JsonNode paths = JsonMapper.builder().build().readTree(spec).path("paths");

		// 대조 — 앱의 세션 처리기가 명세에 있고 그 설명에는 키의 까닭이 없다
		assertThat(description(paths, "/api/notices", "get", "403")).isEqualTo(PASSWORD);
		assertThat(description(paths, "/api/account/recovery-code", "post", "403")).isEqualTo(CSRF + " · " + PASSWORD);
		assertThat(description(paths, "/api/account/password", "put", "403")).isEqualTo(CSRF);
	}

	private JsonNode paths() throws Exception {
		return JsonMapper.builder().build().readTree(body("/v3/api-docs")).path("paths");
	}

	private static String description(JsonNode paths, String path, String method, String status) {
		return paths.path(path).path(method).path("responses").path(status).path("description").asString();
	}

	private String body(String path) throws Exception {
		return this.mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	// 중첩 클래스라 다른 테스트 컨텍스트의 컴포넌트 스캔에는 걸리지 않는다 — 이 컨텍스트에만 명시해 등록한다
	@RestController
	static class MarksController {

		@GetMapping("/api/test/marks/plain")
		String plain(UserId user) {
			return "plain";
		}

		@PostMapping("/api/test/marks/plain")
		String changePlain(UserId user) {
			return "plain";
		}

		@AllowedWithoutLlmKey(reason = "시험 — 키 없이 연다")
		@GetMapping("/api/test/marks/without-key")
		String withoutKey(UserId user) {
			return "without-key";
		}

		@AllowedBeforePasswordChange(reason = "시험 — 변경 전에 연다")
		@GetMapping("/api/test/marks/before-change")
		String beforeChange(UserId user) {
			return "before-change";
		}

		@AllowedBeforePasswordChange(reason = "시험 — 변경 전에 연다")
		@AllowedWithoutLlmKey(reason = "시험 — 키 없이 연다")
		@GetMapping("/api/test/marks/both")
		String both(UserId user) {
			return "both";
		}

		@AllowedBeforePasswordChange(reason = "시험 — 변경 전에 연다")
		@AllowedWithoutLlmKey(reason = "시험 — 키 없이 연다")
		@PostMapping("/api/test/marks/both")
		String changeBoth(UserId user) {
			return "both";
		}

		@PublicApi(reason = "시험 — 공개 처리기")
		@GetMapping("/api/test/marks/public")
		String open() {
			return "public";
		}

		@PublicApi(reason = "시험 — 공개 처리기")
		@PostMapping("/api/test/marks/public")
		String changeOpen() {
			return "public";
		}

	}

}
