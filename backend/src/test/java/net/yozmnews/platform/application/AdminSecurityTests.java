package net.yozmnews.platform.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 토큰을 설정한 인스턴스의 운영자 경로 — 토큰 없음 · 틀림 · 다른 스킴은 401, 맞으면 통과. 운영자 경로 밖은 토큰 없이 열린다.
 * <p>
 * 지키는 규칙: platform-R10 · platform-R11 · platform-R12 · platform-R13
 */
@WebMvcTest(controllers = AdminSecurityTests.ProbeController.class,
		properties = "yozmnews.admin.token=" + AdminSecurityTests.TOKEN)
@Import({ WebSliceSecurity.class, AdminSecurityTests.ProbeController.class })
class AdminSecurityTests {

	static final String TOKEN = "f3a9c1e07b5d42e8a6c3f19d0b7e4a52c8d1f6e3b9a07c24";

	private static final String UNAUTHORIZED = """
			{"type":"urn:yozmnews:problem:admin-unauthorized","title":"운영자 인증이 필요하다","status":401,
			 "detail":"운영자 토큰이 없거나 맞지 않는다","instance":"/api/admin/probe"}
			""";

	@Autowired
	MockMvc mockMvc;

	@Test
	void requestWithoutATokenIsUnauthorized() throws Exception {
		this.mockMvc.perform(get("/api/admin/probe"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json(UNAUTHORIZED, JsonCompareMode.STRICT));
	}

	@Test
	void wrongTokenIsUnauthorized() throws Exception {
		this.mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN + "0"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json(UNAUTHORIZED, JsonCompareMode.STRICT));
	}

	@Test
	void theTokenUnderAnotherSchemeIsUnauthorized() throws Exception {
		this.mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Basic " + TOKEN))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, TOKEN))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void theRightTokenPasses() throws Exception {
		this.mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
			.andExpect(status().isOk())
			.andExpect(content().string("admin"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		this.mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "bearer " + TOKEN))
			.andExpect(status().isOk());
	}

	@Test
	void stateChangingRequestsNeedNoCsrfTokenButStillTheAdminToken() throws Exception {
		this.mockMvc.perform(post("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
			.andExpect(status().isOk());
		this.mockMvc.perform(post("/api/admin/probe")).andExpect(status().isUnauthorized());
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

		@GetMapping("/api/probe-open")
		String open() {
			return "open";
		}

	}

}
