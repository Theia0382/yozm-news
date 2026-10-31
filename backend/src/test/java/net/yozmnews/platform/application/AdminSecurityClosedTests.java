package net.yozmnews.platform.application;

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
 * 운영자 토큰을 설정하지 않은 인스턴스 — 운영자 경로는 어떤 토큰으로도 열리지 않고 닫힘을 알린다.
 * <p>
 * 지키는 규칙: 운영자 접근 통제(docs/specs/platform.md §5)
 */
@WebMvcTest
@Import({ WebSliceSecurity.class, AdminSecurityTests.ProbeController.class })
class AdminSecurityClosedTests {

	private static final String CLOSED = """
			{"type":"urn:yozmnews:problem:admin-closed","title":"운영자 기능이 닫혀 있다","status":403,
			 "detail":"이 인스턴스는 운영자 토큰(YOZMNEWS_ADMIN_TOKEN)을 설정하지 않아 운영자 기능을 열지 않았다",
			 "instance":"/api/admin/probe"}
			""";

	@Autowired
	MockMvc mockMvc;

	@Test
	void adminPathsAreClosedWithoutAToken() throws Exception {
		this.mockMvc.perform(get("/api/admin/probe"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json(CLOSED, JsonCompareMode.STRICT));
	}

	@Test
	void adminPathsAreClosedWhateverTokenIsPresented() throws Exception {
		this.mockMvc.perform(get("/api/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + AdminSecurityTests.TOKEN))
			.andExpect(status().isForbidden())
			.andExpect(content().json(CLOSED, JsonCompareMode.STRICT));
	}

	@Test
	void pathsOutsideTheAdminPathsStayOpen() throws Exception {
		this.mockMvc.perform(get("/api/probe-open")).andExpect(status().isOk());
	}

}
