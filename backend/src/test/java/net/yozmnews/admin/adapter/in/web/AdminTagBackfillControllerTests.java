package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.application.WebSliceSecurity;
import net.yozmnews.tag.TagBackfillRequestResult;
import net.yozmnews.tag.TagBackfillReviewResult;
import net.yozmnews.tag.TagBackfillRunView;
import net.yozmnews.tag.TagBackfillState;
import net.yozmnews.tag.TagBackfillVersionCount;
import net.yozmnews.tag.TagBackfillVersionView;
import net.yozmnews.tag.TagSetId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 빠진 태그 채움 — 확인 · 요청의 판별자 · 속성, 목록과 상한, 범위 밖 기간 · 본문 형식의 400, 운영자 토큰.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R3
 */
@WebMvcTest(controllers = AdminTagBackfillController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeTagBackfillAdministration.Config.class })
class AdminTagBackfillControllerTests {

	private static final TagBackfillRunView RUN = new TagBackfillRunView(4, 30, Instant.parse("2026-10-01T00:00:00Z"),
			Instant.parse("2026-10-31T00:00:00Z"), Instant.parse("2026-10-31T00:00:00Z"), TagBackfillState.RUNNING,
			Instant.parse("2026-10-31T00:01:10Z"), null, 3, 2, 5, 1, 0,
			List.of(new TagBackfillVersionView(new TagSetId(1), 1, new BigDecimal("0.100"), new BigDecimal("0.100"), 2, 1,
					0, 1, 0),
					new TagBackfillVersionView(new TagSetId(7), 2, new BigDecimal("0.050"), new BigDecimal("0.100"), 1, 1,
							5, 0, 0)));

	private static final String RUN_JSON = """
			{"id":4,"days":30,"from":"2026-10-01T00:00:00Z","to":"2026-10-31T00:00:00Z",
			 "requestedAt":"2026-10-31T00:00:00Z","state":"RUNNING","startedAt":"2026-10-31T00:01:10Z","finishedAt":null,
			 "evaluations":3,"processed":2,"tagsAdded":5,"skippedOtherEvaluator":1,"skippedUnreadable":0,
			 "versions":[
			   {"id":1,"number":1,"topicStoreThreshold":0.100,"regionStoreThreshold":0.100,"evaluations":2,"processed":1,
			    "tagsAdded":0,"skippedOtherEvaluator":1,"skippedUnreadable":0},
			   {"id":7,"number":2,"topicStoreThreshold":0.050,"regionStoreThreshold":0.100,"evaluations":1,"processed":1,
			    "tagsAdded":5,"skippedOtherEvaluator":0,"skippedUnreadable":0}]}
			""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeTagBackfillAdministration administration;

	@BeforeEach
	void reset() {
		this.administration.reset();
	}

	@Test
	void theReviewAnswersTheRangeAndTheCountsByVersion() throws Exception {
		this.administration.review = new TagBackfillReviewResult.Ready(Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-10-31T00:00:00Z"), 3,
				List.of(new TagBackfillVersionCount(new TagSetId(1), 1, 2), new TagBackfillVersionCount(new TagSetId(7), 2, 1)));

		this.mockMvc.perform(json(post("/api/admin/tag-backfill-reviews"), "{\"days\":30}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"READY","from":"2026-10-01T00:00:00Z","to":"2026-10-31T00:00:00Z","evaluations":3,
					 "versions":[{"id":1,"number":1,"evaluations":2},{"id":7,"number":2,"evaluations":1}]}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("review 30");
	}

	@Test
	void aRequestAnswersTheRecordedOrTheOpenBackfill() throws Exception {
		this.administration.request = new TagBackfillRequestResult.Requested(RUN);
		this.mockMvc.perform(json(post("/api/admin/tag-backfills"), "{\"days\":30}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REQUESTED","run":%s}
					""".formatted(RUN_JSON), JsonCompareMode.STRICT));

		this.administration.request = new TagBackfillRequestResult.AlreadyOpen(RUN);
		this.mockMvc.perform(json(post("/api/admin/tag-backfills"), "{\"days\":7}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"ALREADY_OPEN","run":%s}
					""".formatted(RUN_JSON), JsonCompareMode.STRICT));

		assertThat(this.administration.calls).containsExactly("request 30", "request 7");
	}

	@Test
	void listsTheBackfillsLatestFirstWithTheMaximumDays() throws Exception {
		this.administration.runs = List.of(RUN);

		this.mockMvc.perform(authorized(get("/api/admin/tag-backfills")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"runs":[%s],"maxDays":180}
					""".formatted(RUN_JSON), JsonCompareMode.STRICT));
	}

	@Test
	void daysOutOfRangeAreABadRequest() throws Exception {
		this.administration.review = new TagBackfillReviewResult.DaysOutOfRange();
		this.mockMvc.perform(json(post("/api/admin/tag-backfill-reviews"), "{\"days\":181}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"채울 기간은 1일에서 180일까지다",
					 "instance":"/api/admin/tag-backfill-reviews"}
					""", JsonCompareMode.STRICT));

		this.administration.request = new TagBackfillRequestResult.DaysOutOfRange();
		this.mockMvc.perform(json(post("/api/admin/tag-backfills"), "{\"days\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"채울 기간은 1일에서 180일까지다",
					 "instance":"/api/admin/tag-backfills"}
					""", JsonCompareMode.STRICT));

		assertThat(this.administration.calls).containsExactly("review 181", "request 0");
	}

	@Test
	void aBodyWithoutTheDaysIsABadRequest() throws Exception {
		this.mockMvc.perform(json(post("/api/admin/tag-backfills"), "{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(json(post("/api/admin/tag-backfill-reviews"), "{\"days\":\"thirty\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(this.administration.calls).isEmpty();
	}

	@Test
	void theOperatorTokenGuardsTheBackfillPaths() throws Exception {
		this.mockMvc.perform(get("/api/admin/tag-backfills")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(post("/api/admin/tag-backfills").contentType(MediaType.APPLICATION_JSON).content("{\"days\":1}"))
			.andExpect(status().isUnauthorized());

		assertThat(this.administration.calls).isEmpty();
	}

}
