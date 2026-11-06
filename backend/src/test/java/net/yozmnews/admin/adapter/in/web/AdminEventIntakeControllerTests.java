package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.event.IntakeFillResult;
import net.yozmnews.platform.application.WebSliceSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 사건 묶기 채우기 — 결과의 두 수, 범위 밖 기간 · 본문 형식의 400, 운영자 토큰.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R3
 */
@WebMvcTest(controllers = AdminEventIntakeController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeEventIntakeAdministration.Config.class })
class AdminEventIntakeControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeEventIntakeAdministration administration;

	@BeforeEach
	void reset() {
		this.administration.reset();
	}

	@Test
	void aFillAnswersTheSeenAndTheNewlyQueuedArticles() throws Exception {
		this.administration.fill = new IntakeFillResult.Filled(120, 87);

		this.mockMvc.perform(json(post("/api/admin/event-intake-fills"), "{\"days\":7}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"FILLED","articles":120,"enqueued":87}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("fill 7");
	}

	@Test
	void daysOutOfRangeAreABadRequest() throws Exception {
		this.administration.fill = new IntakeFillResult.DaysOutOfRange(30);

		this.mockMvc.perform(json(post("/api/admin/event-intake-fills"), "{\"days\":31}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"채울 기간은 1일에서 30일까지다",
					 "instance":"/api/admin/event-intake-fills"}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("fill 31");
	}

	@Test
	void aBodyWithoutTheDaysIsABadRequest() throws Exception {
		this.mockMvc.perform(json(post("/api/admin/event-intake-fills"), "{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(json(post("/api/admin/event-intake-fills"), "{\"days\":\"seven\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(this.administration.calls).isEmpty();
	}

	@Test
	void theOperatorTokenGuardsTheFillPath() throws Exception {
		this.mockMvc
			.perform(post("/api/admin/event-intake-fills").contentType(MediaType.APPLICATION_JSON).content("{\"days\":1}"))
			.andExpect(status().isUnauthorized());

		assertThat(this.administration.calls).isEmpty();
	}

}
