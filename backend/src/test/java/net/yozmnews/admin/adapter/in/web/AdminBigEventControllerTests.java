package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.event.BigEvent;
import net.yozmnews.event.BigEventCriteria;
import net.yozmnews.event.BigEventCriteriaChange;
import net.yozmnews.event.BigEventCriteriaChangeResult;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventTally;
import net.yozmnews.platform.application.WebSliceSecurity;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 큰 사건 · 큰 사건 기준 — 기간의 큰 사건과 지금 기준, 기준 상태 · 이력, 미리보기의 두 목록, 변경의 결과, 범위 밖 기준 · 기간의
 * 400, 운영자 토큰.
 * <p>
 * 기간은 {@code Asia/Seoul} 11-01 ~ 11-07 → {@code [2026-10-31T15:00Z, 2026-11-07T15:00Z)}. 사건 7 의 대표는 기사 41(피드 5 — 한겨레),
 * 사건 9 는 대표가 없다.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R3 · admin-R6 · admin-R7
 */
@WebMvcTest(controllers = AdminBigEventController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeEventDirectory.Config.class, FakeBigEventAdministration.Config.class,
		FakeArticleDirectory.Config.class, FakeFeedLabels.Config.class })
class AdminBigEventControllerTests {

	private static final String PERIOD = "from=2026-11-01&to=2026-11-07&zone=Asia/Seoul";

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	private static final BigEvent SEVEN = new BigEvent(new EventId(7), List.of(new ArticleId(41), new ArticleId(43)),
			new EventTally(2, 4, new BigDecimal("3.75")));

	private static final BigEvent NINE = new BigEvent(new EventId(9), List.of(new ArticleId(44)),
			new EventTally(1, 3, null));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeEventDirectory directory;

	@Autowired
	FakeBigEventAdministration administration;

	@Autowired
	FakeArticleDirectory articles;

	@Autowired
	FakeFeedLabels labels;

	@BeforeEach
	void reset() {
		this.directory.reset();
		this.administration.reset();
		this.articles.reset();
		this.labels.reset();
		this.articles.contents.put(new ArticleId(41), new ArticleContent(new ArticleId(41), "국회 본회의 통과", null,
				URI.create("https://news.example/41"), T,
				List.of(new ArticleEntryView(new SourceId(3), new FeedId(5), List.of()))));
		this.labels.labels.put(new FeedId(5), new FeedLabel("한겨레", "정치"));
		this.directory.representatives.put(new EventId(7), new ArticleId(41));
	}

	@Test
	void theBigEventsOfThePeriodComeWithTheCurrentCriteria() throws Exception {
		this.directory.bigEvents = List.of(SEVEN, NINE);

		this.mockMvc.perform(authorized(get("/api/admin/big-events?" + PERIOD)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"criteria":{"minSources":3,"minImportance":3.00},
					 "events":[
					   {"id":7,"articles":2,"sources":4,"importance":3.75,"representativeId":41,
					    "representative":{"title":"국회 본회의 통과","sources":["한겨레"]}},
					   {"id":9,"articles":1,"sources":3,"importance":null,"representativeId":null,"representative":null}],
					 "maxDays":31}
					""", JsonCompareMode.STRICT));
		assertThat(this.directory.calls).containsExactly("bigEvents 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z",
				"representativesOfAll [7, 9]");
		assertThat(this.articles.calls).containsExactly("findAll [41]");
	}

	@Test
	void theCriteriaStatusCarriesTheHistoryNewestFirst() throws Exception {
		this.administration.criteria = new BigEventCriteria(4, new BigDecimal("3.5"));
		this.administration.changes = List.of(
				new BigEventCriteriaChange(BigEventCriteria.DEFAULT, new BigEventCriteria(4, new BigDecimal("3.5")),
						ChangeActor.OPERATOR, Instant.parse("2026-11-06T00:00:00Z")),
				new BigEventCriteriaChange(null, BigEventCriteria.DEFAULT, ChangeActor.SYSTEM, T));

		this.mockMvc.perform(authorized(get("/api/admin/big-event-criteria")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"current":{"minSources":4,"minImportance":3.50},
					 "changes":[
					   {"previous":{"minSources":3,"minImportance":3.00},"current":{"minSources":4,"minImportance":3.50},
					    "actor":"OPERATOR","changedAt":"2026-11-06T00:00:00Z"},
					   {"previous":null,"current":{"minSources":3,"minImportance":3.00},"actor":"SYSTEM",
					    "changedAt":"2026-11-05T00:00:00Z"}]}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void aPreviewJudgesThePeriodWithBothCriteria() throws Exception {
		this.directory.bigEvents = List.of(SEVEN);
		this.administration.preview = List.of(SEVEN, NINE);

		this.mockMvc
			.perform(json(post("/api/admin/big-event-criteria-previews"), """
					{"minSources":3,"minImportance":2.5,"from":"2026-11-01","to":"2026-11-07","zone":"Asia/Seoul"}
					"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"current":{"minSources":3,"minImportance":3.00},"proposed":{"minSources":3,"minImportance":2.50},
					 "currentEvents":[
					   {"id":7,"articles":2,"sources":4,"importance":3.75,"representativeId":41,
					    "representative":{"title":"국회 본회의 통과","sources":["한겨레"]}}],
					 "proposedEvents":[
					   {"id":7,"articles":2,"sources":4,"importance":3.75,"representativeId":41,
					    "representative":{"title":"국회 본회의 통과","sources":["한겨레"]}},
					   {"id":9,"articles":1,"sources":3,"importance":null,"representativeId":null,"representative":null}]}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("criteria",
				"preview 3 2.50 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z");
	}

	@Test
	void aPreviewOutOfRangeIsABadRequestAndJudgesNothing() throws Exception {
		previewBadRequest("{\"minSources\":0,\"minImportance\":3,\"from\":\"2026-11-01\",\"to\":\"2026-11-07\","
				+ "\"zone\":\"Asia/Seoul\"}", "큰 사건 기준은 소스 1 이상, 중요도 1 ~ 5 의 소수 2자리까지다");
		previewBadRequest("{\"minSources\":3,\"minImportance\":3.125,\"from\":\"2026-11-01\",\"to\":\"2026-11-07\","
				+ "\"zone\":\"Asia/Seoul\"}", "큰 사건 기준은 소스 1 이상, 중요도 1 ~ 5 의 소수 2자리까지다");
		previewBadRequest("{\"minSources\":3,\"minImportance\":3,\"from\":\"2026-11-01\",\"to\":\"2026-12-02\","
				+ "\"zone\":\"Asia/Seoul\"}", "기간은 1일에서 31일까지다");
		assertThat(this.administration.calls).isEmpty();
		assertThat(this.directory.calls).isEmpty();
	}

	@Test
	void aChangeAnswersChangedOrUnchangedAsTheOperator() throws Exception {
		this.administration.change = new BigEventCriteriaChangeResult.Changed(BigEventCriteria.DEFAULT,
				new BigEventCriteria(4, new BigDecimal("3.5")));

		this.mockMvc.perform(json(put("/api/admin/big-event-criteria"), "{\"minSources\":4,\"minImportance\":3.5}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"CHANGED","previous":{"minSources":3,"minImportance":3.00},
					 "current":{"minSources":4,"minImportance":3.50}}
					""", JsonCompareMode.STRICT));

		this.administration.change = new BigEventCriteriaChangeResult.Unchanged(BigEventCriteria.DEFAULT);
		this.mockMvc.perform(json(put("/api/admin/big-event-criteria"), "{\"minSources\":3,\"minImportance\":3}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNCHANGED","previous":null,"current":{"minSources":3,"minImportance":3.00}}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("change 4 3.5 OPERATOR", "change 3 3 OPERATOR");
	}

	@Test
	void aChangeOutOfRangeOrWithoutValuesIsABadRequest() throws Exception {
		this.administration.change = new BigEventCriteriaChangeResult.OutOfRange();

		this.mockMvc.perform(json(put("/api/admin/big-event-criteria"), "{\"minSources\":0,\"minImportance\":3}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"status":400,"detail":"큰 사건 기준은 소스 1 이상, 중요도 1 ~ 5 의 소수 2자리까지다"}
					"""));
		this.mockMvc.perform(json(put("/api/admin/big-event-criteria"), "{\"minSources\":3}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		assertThat(this.administration.calls).containsExactly("change 0 3 OPERATOR");
	}

	@Test
	void theBigEventsNeedTheOperatorToken() throws Exception {
		this.mockMvc.perform(get("/api/admin/big-events?" + PERIOD)).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/big-event-criteria")).andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(put("/api/admin/big-event-criteria").contentType(MediaType.APPLICATION_JSON)
				.content("{\"minSources\":4,\"minImportance\":3.5}"))
			.andExpect(status().isUnauthorized());
		assertThat(this.administration.calls).isEmpty();
	}

	private void previewBadRequest(String body, String detail) throws Exception {
		this.mockMvc.perform(json(post("/api/admin/big-event-criteria-previews"), body))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json("{\"status\":400,\"detail\":\"" + detail + "\"}"));
	}

}
