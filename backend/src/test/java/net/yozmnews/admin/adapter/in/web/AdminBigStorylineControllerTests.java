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
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventTally;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.platform.application.WebSliceSecurity;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.SourceId;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.BigStorylineCriteria;
import net.yozmnews.storyline.BigStorylineCriteriaChange;
import net.yozmnews.storyline.BigStorylineCriteriaChangeResult;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.storyline.UnlinkedEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 큰 스토리라인 · 큰 판정 기준 — 기간의 큰 스토리라인과 지금 기준 · 소속 없는 사건, 기준 상태 · 이력, 미리보기의 두 목록, 변경의
 * 결과, 범위 밖 기준 · 기간의 400, 운영자 토큰.
 * <p>
 * 기간은 {@code Asia/Seoul} 11-01 ~ 11-07 → {@code [2026-10-31T15:00Z, 2026-11-07T15:00Z)}. 스토리라인 7 의 대표 사건은 70 이고 그 대표
 * 기사는 41(피드 5 — 한겨레)이다. 스토리라인 9 의 대표 사건 90 은 대표 기사가 없다(그새 합쳐졌거나 지워졌다).
 * <p>
 * 지키는 규칙: admin-R1 · admin-R3 · admin-R6 · admin-R7
 */
@WebMvcTest(controllers = AdminBigStorylineController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeStorylineDirectory.Config.class, FakeEventDirectory.Config.class,
		FakeBigStorylineAdministration.Config.class,
		FakeArticleDirectory.Config.class, FakeFeedLabels.Config.class })
class AdminBigStorylineControllerTests {

	private static final String PERIOD = "from=2026-11-01&to=2026-11-07&zone=Asia/Seoul";

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	private static final BigStoryline SEVEN = new BigStoryline(
			new StorylineView(new StorylineId(7), StorylineState.ACTIVE, 3, 12, Instant.parse("2026-11-01T00:00:00Z"), T,
					new EventId(70), Instant.parse("2026-11-01T01:00:00Z"), null),
			new EventTally(2, 4, new BigDecimal("3.75")));

	private static final BigStoryline NINE = new BigStoryline(
			new StorylineView(new StorylineId(9), StorylineState.ENDED, 2, 5, Instant.parse("2026-09-01T00:00:00Z"),
					Instant.parse("2026-11-03T00:00:00Z"), new EventId(90), Instant.parse("2026-09-01T01:00:00Z"), null),
			new EventTally(1, 3, null));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeStorylineDirectory directory;

	@Autowired
	FakeEventDirectory events;

	@Autowired
	FakeBigStorylineAdministration administration;

	@Autowired
	FakeArticleDirectory articles;

	@Autowired
	FakeFeedLabels labels;

	@BeforeEach
	void reset() {
		this.directory.reset();
		this.events.reset();
		this.administration.reset();
		this.articles.reset();
		this.labels.reset();
		this.articles.contents.put(new ArticleId(41), new ArticleContent(new ArticleId(41), "국회 본회의 통과", null,
				URI.create("https://news.example/41"), T,
				List.of(new ArticleEntryView(new SourceId(3), new FeedId(5), List.of()))));
		this.labels.labels.put(new FeedId(5), new FeedLabel("한겨레", "정치"));
		this.events.representatives.put(new EventId(70), new ArticleId(41));
	}

	@Test
	void theBigStorylinesOfThePeriodComeWithTheCurrentCriteria() throws Exception {
		this.directory.bigStorylines = List.of(SEVEN, NINE);
		this.directory.unlinked = new UnlinkedEvents(2, 5);

		this.mockMvc.perform(authorized(get("/api/admin/big-storylines?" + PERIOD)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"criteria":{"minSources":3,"minImportance":3.00},
					 "storylines":[
					   {"id":7,"state":"ACTIVE","eventCount":3,"articleCount":12,"firstArticleAt":"2026-11-01T00:00:00Z",
					    "lastArticleAt":"2026-11-05T00:00:00Z","periodArticles":2,"periodSources":4,"importance":3.75,
					    "representativeArticleId":41,"representative":{"title":"국회 본회의 통과","sources":["한겨레"]}},
					   {"id":9,"state":"ENDED","eventCount":2,"articleCount":5,"firstArticleAt":"2026-09-01T00:00:00Z",
					    "lastArticleAt":"2026-11-03T00:00:00Z","periodArticles":1,"periodSources":3,"importance":null,
					    "representativeArticleId":null,"representative":null}],
					 "unlinked":{"events":2,"articles":5},
					 "maxDays":31}
					""", JsonCompareMode.STRICT));
		assertThat(this.directory.calls).containsExactly("bigStorylines 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z",
				"unlinked 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z");
		assertThat(this.events.calls).containsExactly("representativesOfAll [70, 90]");
		assertThat(this.articles.calls).containsExactly("findAll [41]");
	}

	@Test
	void theCriteriaStatusCarriesTheHistoryNewestFirst() throws Exception {
		this.administration.criteria = new BigStorylineCriteria(4, new BigDecimal("3.5"));
		this.administration.changes = List.of(
				new BigStorylineCriteriaChange(BigStorylineCriteria.DEFAULT, new BigStorylineCriteria(4, new BigDecimal("3.5")),
						ChangeActor.OPERATOR, Instant.parse("2026-11-06T00:00:00Z")),
				new BigStorylineCriteriaChange(null, BigStorylineCriteria.DEFAULT, ChangeActor.SYSTEM, T));

		this.mockMvc.perform(authorized(get("/api/admin/big-storyline-criteria")))
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
		this.directory.bigStorylines = List.of(SEVEN);
		this.administration.preview = List.of(SEVEN, NINE);

		this.mockMvc
			.perform(json(post("/api/admin/big-storyline-criteria-previews"), """
					{"minSources":3,"minImportance":2.5,"from":"2026-11-01","to":"2026-11-07","zone":"Asia/Seoul"}
					"""))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"current":{"minSources":3,"minImportance":3.00},"proposed":{"minSources":3,"minImportance":2.50},
					 "currentStorylines":[
					   {"id":7,"state":"ACTIVE","eventCount":3,"articleCount":12,"firstArticleAt":"2026-11-01T00:00:00Z",
					    "lastArticleAt":"2026-11-05T00:00:00Z","periodArticles":2,"periodSources":4,"importance":3.75,
					    "representativeArticleId":41,"representative":{"title":"국회 본회의 통과","sources":["한겨레"]}}],
					 "proposedStorylines":[
					   {"id":7,"state":"ACTIVE","eventCount":3,"articleCount":12,"firstArticleAt":"2026-11-01T00:00:00Z",
					    "lastArticleAt":"2026-11-05T00:00:00Z","periodArticles":2,"periodSources":4,"importance":3.75,
					    "representativeArticleId":41,"representative":{"title":"국회 본회의 통과","sources":["한겨레"]}},
					   {"id":9,"state":"ENDED","eventCount":2,"articleCount":5,"firstArticleAt":"2026-09-01T00:00:00Z",
					    "lastArticleAt":"2026-11-03T00:00:00Z","periodArticles":1,"periodSources":3,"importance":null,
					    "representativeArticleId":null,"representative":null}]}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("criteria",
				"preview 3 2.50 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z");
	}

	@Test
	void aPreviewOutOfRangeIsABadRequestAndJudgesNothing() throws Exception {
		previewBadRequest("{\"minSources\":0,\"minImportance\":3,\"from\":\"2026-11-01\",\"to\":\"2026-11-07\","
				+ "\"zone\":\"Asia/Seoul\"}", "큰 판정 기준은 소스 1 이상, 중요도 1 ~ 5 의 소수 2자리까지다");
		previewBadRequest("{\"minSources\":3,\"minImportance\":3.125,\"from\":\"2026-11-01\",\"to\":\"2026-11-07\","
				+ "\"zone\":\"Asia/Seoul\"}", "큰 판정 기준은 소스 1 이상, 중요도 1 ~ 5 의 소수 2자리까지다");
		previewBadRequest("{\"minSources\":3,\"minImportance\":3,\"from\":\"2026-11-01\",\"to\":\"2026-12-02\","
				+ "\"zone\":\"Asia/Seoul\"}", "기간은 1일에서 31일까지다");
		assertThat(this.administration.calls).isEmpty();
		assertThat(this.directory.calls).isEmpty();
		assertThat(this.events.calls).isEmpty();
	}

	@Test
	void aChangeAnswersChangedOrUnchangedAsTheOperator() throws Exception {
		this.administration.change = new BigStorylineCriteriaChangeResult.Changed(BigStorylineCriteria.DEFAULT,
				new BigStorylineCriteria(4, new BigDecimal("3.5")));

		this.mockMvc.perform(json(put("/api/admin/big-storyline-criteria"), "{\"minSources\":4,\"minImportance\":3.5}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"CHANGED","previous":{"minSources":3,"minImportance":3.00},
					 "current":{"minSources":4,"minImportance":3.50}}
					""", JsonCompareMode.STRICT));

		this.administration.change = new BigStorylineCriteriaChangeResult.Unchanged(BigStorylineCriteria.DEFAULT);
		this.mockMvc.perform(json(put("/api/admin/big-storyline-criteria"), "{\"minSources\":3,\"minImportance\":3}"))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNCHANGED","previous":null,"current":{"minSources":3,"minImportance":3.00}}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("change 4 3.5 OPERATOR", "change 3 3 OPERATOR");
	}

	@Test
	void aChangeOutOfRangeOrWithoutValuesIsABadRequest() throws Exception {
		this.administration.change = new BigStorylineCriteriaChangeResult.OutOfRange();

		this.mockMvc.perform(json(put("/api/admin/big-storyline-criteria"), "{\"minSources\":0,\"minImportance\":3}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"status":400,"detail":"큰 판정 기준은 소스 1 이상, 중요도 1 ~ 5 의 소수 2자리까지다"}
					"""));
		this.mockMvc.perform(json(put("/api/admin/big-storyline-criteria"), "{\"minSources\":3}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		assertThat(this.administration.calls).containsExactly("change 0 3 OPERATOR");
	}

	@Test
	void aFractionalSourceCountIsABadRequestNotATruncation() throws Exception {
		this.administration.change = new BigStorylineCriteriaChangeResult.Unchanged(BigStorylineCriteria.DEFAULT);
		// 대조 — 소수점 없는 정수는 그대로 받는다
		this.mockMvc.perform(json(put("/api/admin/big-storyline-criteria"), "{\"minSources\":3,\"minImportance\":3}"))
			.andExpect(status().isOk());

		this.mockMvc.perform(json(put("/api/admin/big-storyline-criteria"), "{\"minSources\":3.5,\"minImportance\":3}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc
			.perform(json(post("/api/admin/big-storyline-criteria-previews"),
					"{\"minSources\":2.5,\"minImportance\":3,\"from\":\"2026-11-01\",\"to\":\"2026-11-07\","
							+ "\"zone\":\"Asia/Seoul\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		assertThat(this.administration.calls).containsExactly("change 3 3 OPERATOR");
	}

	@Test
	void theBigStorylinesNeedTheOperatorToken() throws Exception {
		this.mockMvc.perform(get("/api/admin/big-storylines?" + PERIOD)).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/big-storyline-criteria")).andExpect(status().isUnauthorized());
		this.mockMvc
			.perform(put("/api/admin/big-storyline-criteria").contentType(MediaType.APPLICATION_JSON)
				.content("{\"minSources\":4,\"minImportance\":3.5}"))
			.andExpect(status().isUnauthorized());
		assertThat(this.administration.calls).isEmpty();
	}

	private void previewBadRequest(String body, String detail) throws Exception {
		this.mockMvc.perform(json(post("/api/admin/big-storyline-criteria-previews"), body))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json("{\"status\":400,\"detail\":\"" + detail + "\"}"));
	}

}
