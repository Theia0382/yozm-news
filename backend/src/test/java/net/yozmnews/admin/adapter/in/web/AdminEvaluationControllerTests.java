package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.yozmnews.platform.application.WebSliceSecurity;
import net.yozmnews.tag.CategoryCoverage;
import net.yozmnews.tag.CountBucket;
import net.yozmnews.tag.EvaluationOverview;
import net.yozmnews.tag.EvaluationQueueCounts;
import net.yozmnews.tag.ProbabilityBucket;
import net.yozmnews.tag.ProbabilityHistogram;
import net.yozmnews.tag.Share;
import net.yozmnews.tag.StoredRange;
import net.yozmnews.tag.SubtopicCoverage;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagShare;
import net.yozmnews.tag.TokenBucket;
import net.yozmnews.tag.TokenSummary;
import net.yozmnews.tag.TopicCoverageResult;
import net.yozmnews.tag.TopicCoverageView;
import net.yozmnews.tag.TopicStatusCounts;
import net.yozmnews.tag.ValueBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 평가 현황 · 주제별 보도량 · 대기 건수 — JSON 모양, 날짜 · 시간대 · 기간 · 주제의 400, 버전의 404, 토큰.
 * <p>
 * 기간은 {@code Asia/Seoul} 10-28 ~ 11-03 → {@code [2026-10-27T15:00Z, 2026-11-03T15:00Z)} 로 공개 API 에 간다.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R2 · admin-R3 · tag-R28
 */
@WebMvcTest(controllers = AdminEvaluationController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeEvaluationAdministration.Config.class })
class AdminEvaluationControllerTests {

	private static final String PERIOD = "from=2026-10-28&to=2026-11-03&zone=Asia/Seoul";

	private static final String CALLED_PERIOD = "2026-10-27T15:00:00Z 2026-11-03T15:00:00Z";

	private static final Instant FROM = Instant.parse("2026-10-27T15:00:00Z");

	private static final Instant TO = Instant.parse("2026-11-03T15:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeEvaluationAdministration evaluation;

	@BeforeEach
	void reset() {
		this.evaluation.reset();
	}

	@Test
	void topicCoverageCarriesTheViewWithTheActiveVersionAsANumberAndTheMaximumDays() throws Exception {
		this.evaluation.coverage = new TopicCoverageResult.Counted(new TopicCoverageView(new TagSetId(7), FROM, TO, 4, 1,
				List.of(new CategoryCoverage("politics", "정치", 2,
						List.of(new SubtopicCoverage("politics.executive", "대통령실·행정부", 2))))));

		this.mockMvc.perform(authorized(get("/api/admin/topic-coverage?" + PERIOD)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"activeVersion":7,"from":"2026-10-27T15:00:00Z","to":"2026-11-03T15:00:00Z","articles":4,
					 "withoutActiveTopic":1,
					 "categories":[{"key":"politics","name":"정치","articles":2,
					                "subtopics":[{"key":"politics.executive","name":"대통령실·행정부","articles":2}]}],
					 "maxDays":31}
					""", JsonCompareMode.STRICT));

		assertThat(this.evaluation.calls).containsExactly("count " + CALLED_PERIOD + " []");
	}

	@Test
	void repeatedTopicsAreTheChosenSet() throws Exception {
		this.evaluation.coverage = new TopicCoverageResult.Counted(new TopicCoverageView(new TagSetId(7), FROM, TO, 0, 0,
				List.of()));

		this.mockMvc.perform(authorized(get("/api/admin/topic-coverage?" + PERIOD
				+ "&topic=politics&topic=economy.finance&topic=politics")))
			.andExpect(status().isOk());

		assertThat(this.evaluation.calls).containsExactly("count " + CALLED_PERIOD + " [economy.finance, politics]");
	}

	@Test
	void topicsMissingFromTheActiveVersionAreABadRequest() throws Exception {
		this.evaluation.coverage = new TopicCoverageResult.UnknownTopics(Set.of("zeta", "alpha"));

		this.mockMvc.perform(authorized(get("/api/admin/topic-coverage?" + PERIOD + "&topic=zeta&topic=alpha")))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"활성 태그 체계에 없는 주제다: alpha, zeta",
					 "instance":"/api/admin/topic-coverage"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void evaluationOverviewCarriesTheOverviewWithTheVersionAsANumber() throws Exception {
		this.evaluation.overview = Optional.of(new EvaluationOverview(new TagSetId(7), FROM, TO, 2,
				new TopicStatusCounts(new Share(1, 0.5), new Share(1, 0.5), new Share(0, 0.0)),
				List.of(new TagShare("politics.executive", "대통령실·행정부", new Share(1, 0.5))),
				List.of(new TagShare("politics", "정치", new Share(1, 0.5))),
				List.of(new TagShare("domestic", "국내", new Share(0, 0.0))),
				List.of(new TagShare("straight", "스트레이트", new Share(2, 1.0))),
				List.of(new CountBucket(0, false, new Share(1, 0.5)), new CountBucket(1, true, new Share(1, 0.5))),
				List.of(new CountBucket(0, true, new Share(2, 1.0))),
				new ProbabilityHistogram(new BigDecimal("0.1"), true,
						List.of(new ProbabilityBucket(new BigDecimal("0.0"), new BigDecimal("0.1"), false, 0,
								StoredRange.NONE))),
				new ProbabilityHistogram(new BigDecimal("0.1"), false, List.of()),
				new ProbabilityHistogram(null, false,
						List.of(new ProbabilityBucket(new BigDecimal("0.9"), new BigDecimal("1.0"), true, 2,
								StoredRange.ALL))),
				List.of(new ValueBucket(new BigDecimal("1.0"), new BigDecimal("1.5"), false, new Share(1, 0.5))), 1,
				new TokenSummary(1_250.0, 1_000, 1_500,
						List.of(new TokenBucket(1_000, 1_100, 1), new TokenBucket(1_500, 1_600, 1)))));

		this.mockMvc.perform(authorized(get("/api/admin/evaluation-overview?version=7&" + PERIOD)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"version":7,"from":"2026-10-27T15:00:00Z","to":"2026-11-03T15:00:00Z","evaluations":2,
					 "topicStatus":{"tagged":{"count":1,"ratio":0.5},"lowConfidence":{"count":1,"ratio":0.5},
					                "unclassified":{"count":0,"ratio":0.0}},
					 "topics":[{"key":"politics.executive","name":"대통령실·행정부","share":{"count":1,"ratio":0.5}}],
					 "topicCategories":[{"key":"politics","name":"정치","share":{"count":1,"ratio":0.5}}],
					 "regions":[{"key":"domestic","name":"국내","share":{"count":0,"ratio":0.0}}],
					 "formats":[{"key":"straight","name":"스트레이트","share":{"count":2,"ratio":1.0}}],
					 "topicsPerArticle":[{"tags":0,"orMore":false,"share":{"count":1,"ratio":0.5}},
					                     {"tags":1,"orMore":true,"share":{"count":1,"ratio":0.5}}],
					 "regionsPerArticle":[{"tags":0,"orMore":true,"share":{"count":2,"ratio":1.0}}],
					 "topicProbabilities":{"storeThreshold":0.1,"storeThresholdChanged":true,
					   "buckets":[{"lower":0.0,"upper":0.1,"upperInclusive":false,"count":0,"stored":"NONE"}]},
					 "regionProbabilities":{"storeThreshold":0.1,"storeThresholdChanged":false,"buckets":[]},
					 "formatProbabilities":{"storeThreshold":null,"storeThresholdChanged":false,
					   "buckets":[{"lower":0.9,"upper":1.0,"upperInclusive":true,"count":2,"stored":"ALL"}]},
					 "importance":[{"lower":1.0,"upper":1.5,"upperInclusive":false,"share":{"count":1,"ratio":0.5}}],
					 "importanceMissing":1,
					 "inputTokens":{"mean":1250.0,"min":1000,"max":1500,
					   "buckets":[{"lower":1000,"upper":1100,"count":1},{"lower":1500,"upper":1600,"count":1}]}}
					""", JsonCompareMode.STRICT));

		assertThat(this.evaluation.calls).containsExactly("overview 7 " + CALLED_PERIOD);
	}

	@Test
	void anUnknownOrImpossibleVersionIsNotFound() throws Exception {
		this.mockMvc.perform(authorized(get("/api/admin/evaluation-overview?version=9&" + PERIOD)))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"없는 태그 체계 버전: 9",
					 "instance":"/api/admin/evaluation-overview"}
					""", JsonCompareMode.STRICT));
		this.mockMvc.perform(authorized(get("/api/admin/evaluation-overview?version=0&" + PERIOD)))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"없는 태그 체계 버전: 0",
					 "instance":"/api/admin/evaluation-overview"}
					""", JsonCompareMode.STRICT));

		// 1 미만은 공개 API 를 부르지 않는다
		assertThat(this.evaluation.calls).containsExactly("overview 9 " + CALLED_PERIOD);
	}

	@Test
	void aBadDateZoneOrPeriodIsABadRequestWithoutCallingTheApi() throws Exception {
		badRequest("/api/admin/topic-coverage?from=2026/10/28&to=2026-11-03&zone=Asia/Seoul",
				"날짜는 yyyy-MM-dd 형식이다: 2026/10/28");
		badRequest("/api/admin/topic-coverage?from=2026-10-28&to=2026-11-31&zone=Asia/Seoul",
				"날짜는 yyyy-MM-dd 형식이다: 2026-11-31");
		badRequest("/api/admin/topic-coverage?from=2026-10-28&to=2026-11-03&zone=Mars/Base", "없는 시간대다: Mars/Base");
		// 32일 · 끝이 시작보다 앞섬 — 대조: 31일은 공개 API 에 간다(aThirtyOneDayRangeReachesTheApi)
		badRequest("/api/admin/topic-coverage?from=2026-10-01&to=2026-11-01&zone=Asia/Seoul", "기간은 1일에서 31일까지다");
		badRequest("/api/admin/evaluation-overview?version=7&from=2026-11-03&to=2026-11-02&zone=Asia/Seoul",
				"기간은 1일에서 31일까지다");
		badRequest("/api/admin/evaluation-overview?version=7&from=2011-12-30&to=2011-12-30&zone=Pacific/Apia",
				"기간은 1일에서 31일까지다");

		assertThat(this.evaluation.calls).isEmpty();
	}

	@Test
	void aThirtyOneDayRangeReachesTheApi() throws Exception {
		this.evaluation.coverage = new TopicCoverageResult.Counted(new TopicCoverageView(new TagSetId(7),
				Instant.parse("2026-09-30T15:00:00Z"), Instant.parse("2026-10-31T15:00:00Z"), 0, 0, List.of()));

		this.mockMvc.perform(authorized(get("/api/admin/topic-coverage?from=2026-10-01&to=2026-10-31&zone=Asia/Seoul")))
			.andExpect(status().isOk());

		assertThat(this.evaluation.calls).containsExactly("count 2026-09-30T15:00:00Z 2026-10-31T15:00:00Z []");
	}

	@Test
	void aMissingOrMalformedParameterIsABadRequest() throws Exception {
		this.mockMvc.perform(authorized(get("/api/admin/topic-coverage?from=2026-10-28&to=2026-11-03")))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(authorized(get("/api/admin/evaluation-overview?" + PERIOD)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		this.mockMvc.perform(authorized(get("/api/admin/evaluation-overview?version=seven&" + PERIOD)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(this.evaluation.calls).isEmpty();
	}

	@Test
	void evaluationQueueCarriesTheCurrentCounts() throws Exception {
		this.evaluation.queue = new EvaluationQueueCounts(3, 1, 2);

		this.mockMvc.perform(authorized(get("/api/admin/evaluation-queue")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"waiting":3,"rejected":1,"exhausted":2}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void theOperatorTokenGuardsTheEvaluationPaths() throws Exception {
		this.mockMvc.perform(get("/api/admin/topic-coverage?" + PERIOD)).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/evaluation-overview?version=7&" + PERIOD))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/evaluation-queue")).andExpect(status().isUnauthorized());

		assertThat(this.evaluation.calls).isEmpty();
	}

	private void badRequest(String path, String detail) throws Exception {
		this.mockMvc.perform(authorized(get(path)))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"%s","instance":"%s"}
					""".formatted(detail, path.substring(0, path.indexOf('?'))), JsonCompareMode.STRICT));
	}

}
