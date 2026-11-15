package net.yozmnews.user.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * 운영자 토큰을 설정하지 않은 인스턴스 — 운영자 경로가 닫히지 않는다. Bearer 헤더를 낸 요청은 어떤 토큰이어도 401
 * {@code admin-unauthorized} 이고, 헤더가 없으면 세션 인증으로 넘어가 세션이 없으므로 401 {@code unauthenticated} 다.
 * <p>
 * 지키는 규칙: user-R1 · user-R2 · user-R3
 */
@WebMvcTest(controllers = AdminSecurityTests.ProbeController.class)
@Import({ WebSliceSecurity.class, AdminSecurityTests.ProbeController.class })
class AdminSecurityWithoutTokenTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void requestWithoutAHeaderNeedsASession() throws Exception {
		this.mockMvc.perform(get("/api/admin/probe"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:unauthenticated","title":"로그인이 필요하다","status":401,
					 "detail":"로그인하지 않았거나 세션이 만료됐다","instance":"/api/admin/probe"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void anyBearerTokenIsUnauthorized() throws Exception {
		this.mockMvc
			.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + AdminSecurityTests.TOKEN))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(content().json("""
					{"type":"urn:yozmnews:problem:admin-unauthorized","title":"운영자 인증이 필요하다","status":401,
					 "detail":"운영자 토큰(Bearer)이 맞지 않거나 형식이 틀리다","instance":"/api/admin/probe"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void pathsOutsideTheAdminPathsStayOpen() throws Exception {
		this.mockMvc.perform(get("/api/probe-open")).andExpect(status().isOk());
	}

}
