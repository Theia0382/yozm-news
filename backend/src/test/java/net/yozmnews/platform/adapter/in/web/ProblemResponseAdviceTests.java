package net.yozmnews.platform.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.platform.application.WebSliceSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 컨트롤러 오류의 문제 응답 — 형식 · 다섯 속성 · 예외 메시지 비노출.
 * <p>
 * 지키는 규칙: platform-R12
 */
@WebMvcTest(controllers = ProblemResponseAdviceTests.FailingController.class)
@Import({ WebSliceSecurity.class, ProblemResponseAdviceTests.FailingController.class })
class ProblemResponseAdviceTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void unknownPathIsAProblemWithEveryProperty() throws Exception {
		this.mockMvc.perform(get("/api/nowhere"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,
					 "detail":"No static resource api/nowhere.","instance":"/api/nowhere"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void responseStatusExceptionKeepsItsReason() throws Exception {
		this.mockMvc.perform(get("/api/test/missing"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,
					 "detail":"그런 것은 없다","instance":"/api/test/missing"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void malformedPathValueIsABadRequest() throws Exception {
		this.mockMvc.perform(get("/api/test/number/abc"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void concurrentChangeIsAConflict() throws Exception {
		this.mockMvc.perform(get("/api/test/conflict"))
			.andExpect(status().isConflict())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Conflict","status":409,
					 "detail":"다른 요청이 먼저 바꿨다 — 다시 읽고 시도한다","instance":"/api/test/conflict"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void unexpectedFailureDoesNotLeakItsMessage() throws Exception {
		String body = this.mockMvc.perform(get("/api/test/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Internal Server Error","status":500,
					 "detail":"서버에서 요청을 처리하지 못했다","instance":"/api/test/boom"}
					""", JsonCompareMode.STRICT))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("db-password-hunter2");
	}

	@RestController
	static class FailingController {

		@GetMapping("/api/test/missing")
		String missing() {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "그런 것은 없다");
		}

		@GetMapping("/api/test/number/{value}")
		String number(@PathVariable long value) {
			return Long.toString(value);
		}

		@GetMapping("/api/test/conflict")
		String conflict() {
			throw new OptimisticLockingFailureException("version 3");
		}

		@GetMapping("/api/test/boom")
		String boom() {
			throw new IllegalStateException("db-password-hunter2");
		}

	}

}
