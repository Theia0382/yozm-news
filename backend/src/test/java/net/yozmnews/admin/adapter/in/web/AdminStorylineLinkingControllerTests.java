package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.yozmnews.platform.application.WebSliceSecurity;
import net.yozmnews.storyline.LinkingFillResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 스토리라인 잇기 채우기 — 결과의 두 수, 범위 밖 기간 · 본문 형식의 400, 운영자 토큰.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R3
 */
@WebMvcTest(controllers = AdminStorylineLinkingController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeStorylineLinkingAdministration.Config.class })
class AdminStorylineLinkingControllerTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeStorylineLinkingAdministration administration;

	@BeforeEach
	void reset() {
		this.administration.reset();
	}

	@Test
	void aFillAnswersTheEventsAndTheNewlyQueued() throws Exception {
		this.administration.fill = new LinkingFillResult.Filled(42, 30);

		this.mockMvc.perform(json(post("/api/admin/storyline-linking-fills"), "{\"days\":7}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"FILLED","events":42,"enqueued":30}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("fill 7");
	}

	@Test
	void daysOutOfRangeAreABadRequest() throws Exception {
		this.administration.fill = new LinkingFillResult.DaysOutOfRange(180);

		this.mockMvc.perform(json(post("/api/admin/storyline-linking-fills"), "{\"days\":181}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"채울 기간은 1일에서 180일까지다",
					 "instance":"/api/admin/storyline-linking-fills"}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("fill 181");
	}

	@Test
	void aBodyWithoutTheDaysIsABadRequest() throws Exception {
		this.mockMvc.perform(json(post("/api/admin/storyline-linking-fills"), "{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(json(post("/api/admin/storyline-linking-fills"), "{\"days\":\"seven\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		// 소수는 버림해 받지 않는다 — 7.5 일이 7 일 채우기가 되지 않는다
		this.mockMvc.perform(json(post("/api/admin/storyline-linking-fills"), "{\"days\":7.5}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(this.administration.calls).isEmpty();
	}

	@Test
	void theOperatorTokenGuardsTheFillPath() throws Exception {
		this.mockMvc
			.perform(post("/api/admin/storyline-linking-fills").contentType(MediaType.APPLICATION_JSON).content("{\"days\":1}"))
			.andExpect(status().isUnauthorized());

		assertThat(this.administration.calls).isEmpty();
	}

}
