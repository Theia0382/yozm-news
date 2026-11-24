package net.yozmnews.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.platform.PublicApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 토큰을 설정한 인스턴스의 운영자 경로 — Bearer 헤더를 낸 요청은 토큰으로만 판정한다: 맞으면 통과하고(변경 요청도 CSRF 없이) 세션을
 * 만들지 않으며, 틀림 · 다른 스킴 · 스킴 없음은 401 {@code admin-unauthorized}. 헤더가 없으면 세션 인증으로 넘어가 세션이 없으므로 401
 * {@code unauthenticated}(변경 요청은 CSRF 가 먼저 막아 403). 운영자 경로 밖은 토큰 없이 열린다.
 * <p>
 * 지키는 규칙: user-R1 · user-R2 · user-R3 · user-R10 · platform-R12
 */
@WebMvcTest(controllers = AdminSecurityTests.ProbeController.class,
		properties = "yozmnews.admin.token=" + AdminSecurityTests.TOKEN)
@Import({ WebSliceSecurity.class, AdminSecurityTests.ProbeController.class })
class AdminSecurityTests {

	static final String TOKEN = "f3a9c1e07b5d42e8a6c3f19d0b7e4a52c8d1f6e3b9a07c24";

	private static final String WRONG_TOKEN = """
			{"type":"urn:yozmnews:problem:admin-unauthorized","title":"운영자 인증이 필요하다","status":401,
			 "detail":"운영자 토큰(Bearer)이 맞지 않거나 형식이 틀리다","instance":"/api/admin/probe"}
			""";

	private static final String NO_SESSION = """
			{"type":"urn:yozmnews:problem:unauthenticated","title":"로그인이 필요하다","status":401,
			 "detail":"로그인하지 않았거나 세션이 만료됐다","instance":"/api/admin/probe"}
			""";

	@Autowired
	MockMvc mockMvc;

	@Test
	void requestWithoutAHeaderNeedsASession() throws Exception {
		this.mockMvc.perform(get("/api/admin/probe"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json(NO_SESSION, JsonCompareMode.STRICT));
	}

	@Test
	void wrongTokenIsUnauthorized() throws Exception {
		this.mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN + "0"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json(WRONG_TOKEN, JsonCompareMode.STRICT));
	}

	@Test
	void theTokenUnderAnotherSchemeIsUnauthorized() throws Exception {
		this.mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Basic " + TOKEN))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(content().json(WRONG_TOKEN, JsonCompareMode.STRICT));
		this.mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, TOKEN))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json(WRONG_TOKEN, JsonCompareMode.STRICT));
	}

	@Test
	void theRightTokenPassesWithoutMakingASession() throws Exception {
		MvcResult result = this.mockMvc
			.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(content().string("admin"))
			.andReturn();
		assertThat(result.getRequest().getSession(false)).as("운영자 토큰 인증은 세션을 만들지 않는다").isNull();
		this.mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "bearer " + TOKEN))
			.andExpect(status().isOk());
	}

	@Test
	void stateChangingRequestsWithTheTokenNeedNoCsrfToken() throws Exception {
		MvcResult result = this.mockMvc
			.perform(post("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(result.getRequest().getSession(false)).as("운영자 토큰 인증은 세션을 만들지 않는다").isNull();
		// 헤더가 없는 변경 요청은 CSRF 가 인가보다 먼저 막는다
		this.mockMvc.perform(post("/api/admin/probe"))
			.andExpect(status().isForbidden())
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:csrf-rejected","title":"요청 보호 토큰이 맞지 않는다","status":403,
					 "detail":"요청 보호 토큰(X-XSRF-TOKEN)이 없거나 맞지 않는다","instance":"/api/admin/probe"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void pathsOutsideTheAdminPathsStayOpen() throws Exception {
		this.mockMvc.perform(get("/api/probe-open")).andExpect(status().isOk()).andExpect(content().string("open"));
	}

	@RestController
	static class ProbeController {

		@GetMapping("/api/admin/probe")
		String admin() {
			return "admin";
		}

		@PostMapping("/api/admin/probe")
		String adminChange() {
			return "admin";
		}

		@PublicApi(reason = "시험 — 운영자 경로 밖의 공개 처리기")
		@GetMapping("/api/probe-open")
		String open() {
			return "open";
		}

	}

}
