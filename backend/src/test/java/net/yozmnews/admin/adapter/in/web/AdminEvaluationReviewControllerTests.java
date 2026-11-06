package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.DistributionShare;
import net.yozmnews.evaluation.EvaluationCursor;
import net.yozmnews.evaluation.EvaluationDetailView;
import net.yozmnews.evaluation.EvaluationFilter;
import net.yozmnews.evaluation.EvaluationId;
import net.yozmnews.evaluation.EvaluationListResult;
import net.yozmnews.evaluation.EvaluationListing;
import net.yozmnews.evaluation.EvaluationRow;
import net.yozmnews.evaluation.EvaluationSampleResult;
import net.yozmnews.evaluation.EvaluationSampleView;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.NamedTag;
import net.yozmnews.evaluation.StoredTagView;
import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.TagName;
import net.yozmnews.evaluation.TagRef;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetThresholds;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.TopicStatus;
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
 * 운영자 API 의 평가 검수 — 목록 · 표본 · 상세의 JSON 모양, 기사 정보 맞붙이기, 거르기 · 커서 · 쪽 크기 · 표본의 400, 버전 · 평가의 404.
 * <p>
 * 기간은 {@code Asia/Seoul} 10-28 ~ 11-03 → {@code [2026-10-27T15:00Z, 2026-11-03T15:00Z)} 로 공개 API 에 간다. 기사 41 은 피드 셋에
 * 실렸다 — 피드 5(한겨레 · 정치), 피드 6(이름 없음), 피드 7(한겨레 · 사회). 기사 42 는 그새 지워 기사 조회에 없다.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R2 · admin-R3 · admin-R6
 */
@WebMvcTest(controllers = AdminEvaluationReviewController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeEvaluationReview.Config.class, FakeArticleDirectory.Config.class,
		FakeFeedLabels.Config.class })
class AdminEvaluationReviewControllerTests {

	private static final String PERIOD = "from=2026-10-28&to=2026-11-03&zone=Asia/Seoul";

	private static final Instant FROM = Instant.parse("2026-10-27T15:00:00Z");

	private static final Instant TO = Instant.parse("2026-11-03T15:00:00Z");

	private static final Instant AT = Instant.parse("2026-11-01T00:00:00Z");

	private static final EvaluationRow ROW = new EvaluationRow(new EvaluationId(9), new ArticleId(41), AT,
			TopicStatus.TAGGED, List.of(new NamedTag("politics.executive", "대통령실·행정부", 0.8)),
			List.of(new TagName("politics", "정치")), List.of(new NamedTag("domestic", "국내", 0.9)),
			new NamedTag("straight", "스트레이트", 0.7), 3.25);

	private static final EvaluationRow DELETED = new EvaluationRow(new EvaluationId(8), new ArticleId(42), AT,
			TopicStatus.UNCLASSIFIED, List.of(), List.of(), List.of(), new NamedTag("opinion", "오피니언·칼럼", 0.6), null);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeEvaluationReview review;

	@Autowired
	FakeArticleDirectory articles;

	@Autowired
	FakeFeedLabels labels;

	@BeforeEach
	void reset() {
		this.review.reset();
		this.articles.reset();
		this.labels.reset();
		this.articles.contents.put(new ArticleId(41), new ArticleContent(new ArticleId(41), "국회 본회의 통과",
				"<b>요약</b> 원문", URI.create("https://news.example/a/41"), Instant.parse("2026-10-28T00:00:00Z"),
				List.of(new ArticleEntryView(new SourceId(3), new FeedId(5), List.of("정치", "국회")),
						new ArticleEntryView(new SourceId(3), new FeedId(6), List.of()),
						new ArticleEntryView(new SourceId(3), new FeedId(7), List.of("사회")))));
		this.labels.labels.put(new FeedId(5), new FeedLabel("한겨레", "정치"));
		this.labels.labels.put(new FeedId(6), new FeedLabel(null, null));
		this.labels.labels.put(new FeedId(7), new FeedLabel("한겨레", "사회"));
	}

	@Test
	void theListCarriesTheRowsWithTheirArticlesAndTheNextCursor() throws Exception {
		this.review.list = new EvaluationListResult.Listed(new EvaluationListing(new TagSetId(1), FROM, TO,
				List.of(ROW, DELETED), new EvaluationCursor(AT, new EvaluationId(8))));

		this.mockMvc.perform(authorized(get("/api/admin/evaluations?version=1&" + PERIOD)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"version":1,"from":"2026-10-27T15:00:00Z","to":"2026-11-03T15:00:00Z",
					 "rows":[
					  {"id":9,"articleId":41,"evaluatedAt":"2026-11-01T00:00:00Z","topicStatus":"TAGGED",
					   "topics":[{"key":"politics.executive","name":"대통령실·행정부","probability":0.8}],
					   "topicCategories":[{"key":"politics","name":"정치"}],
					   "regions":[{"key":"domestic","name":"국내","probability":0.9}],
					   "format":{"key":"straight","name":"스트레이트","probability":0.7},"importance":3.25,
					   "article":{"title":"국회 본회의 통과","sources":["한겨레"]}},
					  {"id":8,"articleId":42,"evaluatedAt":"2026-11-01T00:00:00Z","topicStatus":"UNCLASSIFIED",
					   "topics":[],"topicCategories":[],"regions":[],
					   "format":{"key":"opinion","name":"오피니언·칼럼","probability":0.6},"importance":null,
					   "article":null}],
					 "nextEvaluatedAt":"2026-11-01T00:00:00Z","nextId":8}
					""", JsonCompareMode.STRICT));
		// 기본 쪽 크기 20, 거르기 · 커서 없음. 기사 조회 · 피드 조회는 한 번씩
		assertThat(this.review.calls).containsExactly("list 1 " + FROM + " " + TO
				+ " EvaluationFilter[tag=null, topicStatus=null] null 20");
		assertThat(this.articles.calls).containsExactly("findAll [41, 42]");
		assertThat(this.labels.calls).containsExactly("labels [5, 6, 7]");
	}

	@Test
	void theListPassesTheFilterTheCursorAndThePageSize() throws Exception {
		this.review.list = new EvaluationListResult.Listed(new EvaluationListing(new TagSetId(1), FROM, TO, List.of(),
				null));

		this.mockMvc.perform(authorized(get("/api/admin/evaluations?version=1&" + PERIOD
				+ "&tagKind=TOPIC&tag=politics&topicStatus=TAGGED"
				+ "&afterEvaluatedAt=2026-11-01T00:00:00Z&afterId=9&limit=5")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"rows":[],"nextEvaluatedAt":null,"nextId":null}
					"""));
		assertThat(this.review.calls).containsExactly("list 1 " + FROM + " " + TO + " "
				+ new EvaluationFilter(new TagRef(TagKind.TOPIC, "politics"), TopicStatus.TAGGED)
				+ " " + new EvaluationCursor(AT, new EvaluationId(9)) + " 5");
	}

	@Test
	void theListRefusesHalfAFilterOrACursorAndBadValues() throws Exception {
		badRequest("/api/admin/evaluations?version=1&" + PERIOD + "&tagKind=TOPIC", "tagKind 와 tag 는 함께 준다");
		badRequest("/api/admin/evaluations?version=1&" + PERIOD + "&tag=politics", "tagKind 와 tag 는 함께 준다");
		badRequest("/api/admin/evaluations?version=1&" + PERIOD + "&afterId=9",
				"afterEvaluatedAt 과 afterId 는 함께 준다");
		badRequest("/api/admin/evaluations?version=1&" + PERIOD + "&afterEvaluatedAt=yesterday&afterId=9",
				"시각은 ISO-8601 형식이다: yesterday");
		badRequest("/api/admin/evaluations?version=1&" + PERIOD + "&afterEvaluatedAt=2026-11-01T00:00:00Z&afterId=0",
				"평가 ID 는 1 이상이다: 0");
		assertThat(this.review.calls).isEmpty();
	}

	@Test
	void theListTurnsTheResultsOfThePublicApiIntoProblems() throws Exception {
		this.review.list = new EvaluationListResult.UnknownTag(new TagRef(TagKind.REGION, "mars"));
		badRequest("/api/admin/evaluations?version=1&" + PERIOD + "&tagKind=REGION&tag=mars",
				"그 태그 체계 버전에 없는 태그다: REGION mars");

		this.review.list = new EvaluationListResult.LimitOutOfRange();
		badRequest("/api/admin/evaluations?version=1&" + PERIOD + "&limit=51", "한 번에 50건까지 본다");

		this.review.list = new EvaluationListResult.UnknownVersion();
		this.mockMvc.perform(authorized(get("/api/admin/evaluations?version=9&" + PERIOD)))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"status":404,"detail":"없는 태그 체계 버전: 9","instance":"/api/admin/evaluations"}
					"""));
		this.mockMvc.perform(authorized(get("/api/admin/evaluations?version=0&" + PERIOD)))
			.andExpect(status().isNotFound());
		this.review.calls.clear();
		// 기간의 400 은 공개 API 를 부르기 전이다(AdminPeriods)
		this.mockMvc.perform(authorized(get("/api/admin/evaluations?version=1&from=2026-10-28&to=2026-12-31&zone=UTC")))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("{\"status\":400,\"detail\":\"기간은 1일에서 31일까지다\"}"));
		assertThat(this.review.calls).isEmpty();
	}

	@Test
	void theDetailCarriesTheEvaluationAndTheArticleWithSummaryLinkAndEntries() throws Exception {
		this.review.detail = Optional.of(detail(41));

		this.mockMvc.perform(authorized(get("/api/admin/evaluations/9")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"id":9,"articleId":41,"version":1,"versionNumber":1,"evaluator":"jev","modelVersion":"jev-1.14.0",
					 "evaluatedAt":"2026-11-01T00:00:00Z","inputTokens":1234,
					 "topicThresholds":{"kind":"TOPIC","tagThreshold":0.5,"storeThreshold":0.1},
					 "regionThresholds":{"kind":"REGION","tagThreshold":0.5,"storeThreshold":0.1},
					 "topicStatus":"TAGGED",
					 "topics":[{"key":"politics.executive","name":"대통령실·행정부","probability":0.8,"attached":true},
					           {"key":"politics.legislature","name":"국회·정당·선거","probability":0.4,"attached":false}],
					 "topicCategories":[{"key":"politics","name":"정치"}],
					 "regions":[{"key":"domestic","name":"국내","probability":0.9,"attached":true}],
					 "formats":[{"key":"straight","name":"스트레이트","probability":0.7},
					            {"key":"opinion","name":"오피니언·칼럼","probability":0.3}],
					 "format":"straight",
					 "importanceLevels":[{"key":"3","name":"여러 분야","probability":1.0}],
					 "importance":3.0,
					 "article":{"title":"국회 본회의 통과","summary":"<b>요약</b> 원문",
					  "canonicalUrl":"https://news.example/a/41",
					  "entries":[{"sourceName":"한겨레","section":"정치","categories":["정치","국회"]},
					             {"sourceName":null,"section":null,"categories":[]},
					             {"sourceName":"한겨레","section":"사회","categories":["사회"]}]}}
					""", JsonCompareMode.STRICT));
		assertThat(this.review.calls).containsExactly("detail 9");
	}

	@Test
	void theDetailOfADeletedArticleHasNoArticleAndAnUnknownEvaluationIsNotFound() throws Exception {
		this.review.detail = Optional.of(detail(42));
		this.mockMvc.perform(authorized(get("/api/admin/evaluations/8")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"articleId":42,"article":null}
					"""));

		this.review.detail = Optional.empty();
		this.mockMvc.perform(authorized(get("/api/admin/evaluations/7")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"status":404,"detail":"없는 평가: 7","instance":"/api/admin/evaluations/7"}
					"""));
		this.review.calls.clear();
		this.mockMvc.perform(authorized(get("/api/admin/evaluations/0")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"detail":"없는 평가: 0"}
					"""));
		// 1 미만은 공개 API 를 부르기 전에 404 다
		assertThat(this.review.calls).isEmpty();
	}

	@Test
	void theSampleCarriesTheRowsWithTheSeedAndThePopulation() throws Exception {
		this.review.sample = new EvaluationSampleResult.Sampled(new EvaluationSampleView(new TagSetId(1), FROM, TO, 42,
				1_234, List.of(ROW)));

		this.mockMvc.perform(authorized(get("/api/admin/evaluation-samples?version=1&" + PERIOD + "&seed=42")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"version":1,"from":"2026-10-27T15:00:00Z","to":"2026-11-03T15:00:00Z","seed":42,"population":1234,
					 "rows":[{"id":9,"articleId":41,"article":{"title":"국회 본회의 통과","sources":["한겨레"]}}]}
					"""));
		// 크기가 없으면 기본 100
		assertThat(this.review.calls).containsExactly("sample 1 " + FROM + " " + TO + " 42 100");
	}

	@Test
	void theSampleTurnsTheResultsOfThePublicApiIntoProblems() throws Exception {
		this.review.sample = new EvaluationSampleResult.SizeOutOfRange();
		badRequest("/api/admin/evaluation-samples?version=1&" + PERIOD + "&seed=42&size=201", "표본은 1건에서 200건까지다");

		this.review.sample = new EvaluationSampleResult.SeedOutOfRange();
		badRequest("/api/admin/evaluation-samples?version=1&" + PERIOD + "&seed=-1",
				"씨앗은 0에서 9007199254740991까지다");

		this.review.sample = new EvaluationSampleResult.UnknownVersion();
		this.mockMvc.perform(authorized(get("/api/admin/evaluation-samples?version=9&" + PERIOD + "&seed=42")))
			.andExpect(status().isNotFound());
		this.mockMvc.perform(authorized(get("/api/admin/evaluation-samples?version=1&" + PERIOD)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		this.review.calls.clear();
		// 기간의 400 은 공개 API 를 부르기 전이다(AdminPeriods)
		this.mockMvc
			.perform(authorized(get("/api/admin/evaluation-samples?version=1&from=2026-10-28&to=2026-11-03&zone=Mars/Base"
					+ "&seed=42")))
			.andExpect(status().isBadRequest());
		assertThat(this.review.calls).isEmpty();
	}

	@Test
	void theReviewNeedsTheOperatorToken() throws Exception {
		this.mockMvc.perform(get("/api/admin/evaluations?version=1&" + PERIOD)).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/evaluations/9")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/evaluation-samples?version=1&" + PERIOD + "&seed=1"))
			.andExpect(status().isUnauthorized());
	}

	private void badRequest(String url, String detail) throws Exception {
		this.mockMvc.perform(authorized(get(url)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json("{\"status\":400,\"detail\":\"" + detail + "\"}"));
	}

	private static EvaluationDetailView detail(long article) {
		return new EvaluationDetailView(new EvaluationId(9), new ArticleId(article), new TagSetId(1), 1, "jev",
				"jev-1.14.0", AT, 1_234,
				new TagSetThresholds(new TagSetId(1),
						new TagThresholds(MultiTagKind.TOPIC, new BigDecimal("0.5"), new BigDecimal("0.1")),
						new TagThresholds(MultiTagKind.REGION, new BigDecimal("0.5"), new BigDecimal("0.1"))),
				TopicStatus.TAGGED,
				List.of(new StoredTagView("politics.executive", "대통령실·행정부", 0.8, true),
						new StoredTagView("politics.legislature", "국회·정당·선거", 0.4, false)),
				List.of(new TagName("politics", "정치")), List.of(new StoredTagView("domestic", "국내", 0.9, true)),
				List.of(new DistributionShare("straight", "스트레이트", 0.7),
						new DistributionShare("opinion", "오피니언·칼럼", 0.3)),
				"straight", List.of(new DistributionShare("3", "여러 분야", 1.0)), 3.0);
	}

}
