package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import net.yozmnews.account.application.WebSliceSecurity;
import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.evaluation.JudgedEvent;
import net.yozmnews.event.ArticleAssignment;
import net.yozmnews.event.ArticleKeyTerms;
import net.yozmnews.event.AssignmentCandidate;
import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventView;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.SourceId;
import net.yozmnews.tag.EvaluationId;
import net.yozmnews.tag.EvaluationRow;
import net.yozmnews.tag.NamedTag;
import net.yozmnews.tag.TagName;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicStatus;
import net.yozmnews.tag.VersionedEvaluationRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 기사 상세 — JSON 모양, 기사 정보 · 평가 · 사건 소속 · 핵심 말 맞붙이기, 평가 · 배정 · 표현이 없는 기사, 사라진 사건 · 지운 판정
 * 원본, 없는(지운) 기사의 404, 운영자 토큰.
 * <p>
 * 기사 41 은 피드 5(한겨레 · 정치) · 피드 6(이름 없음)에 실렸다. 기사 43 은 평가 · 배정 · 표현이 없다. 기사 42 는 그새 지워 기사 조회에 없다.
 * <p>
 * 지키는 규칙: admin-R2 · admin-R6 · admin-R8
 */
@WebMvcTest(controllers = AdminArticleController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeArticleDirectory.Config.class, FakeFeedLabels.Config.class,
		FakeEvaluationReview.Config.class, FakeEventReview.Config.class, FakeEventDirectory.Config.class,
		FakeEventJudgementLog.Config.class })
class AdminArticleControllerTests {

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	private static final AssignmentCriteria USED = new AssignmentCriteria(Duration.ofHours(72), 0.8, 0.55, 0.4, 0.5);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeArticleDirectory articles;

	@Autowired
	FakeFeedLabels labels;

	@Autowired
	FakeEvaluationReview evaluations;

	@Autowired
	FakeEventReview review;

	@Autowired
	FakeEventDirectory events;

	@Autowired
	FakeEventJudgementLog judgements;

	@BeforeEach
	void reset() {
		this.articles.reset();
		this.labels.reset();
		this.evaluations.reset();
		this.review.reset();
		this.events.reset();
		this.judgements.reset();
		this.articles.contents.put(new ArticleId(41), new ArticleContent(new ArticleId(41), "국회 본회의 통과",
				"<b>요약</b> 원문", URI.create("https://news.example/a/41"), T,
				List.of(new ArticleEntryView(new SourceId(3), new FeedId(5), List.of("정치")),
						new ArticleEntryView(new SourceId(4), new FeedId(6), List.of()))));
		this.articles.contents.put(new ArticleId(43), new ArticleContent(new ArticleId(43), "기준금리 동결", null,
				URI.create("https://news.example/a/43"), T,
				List.of(new ArticleEntryView(new SourceId(3), new FeedId(5), List.of()))));
		this.labels.labels.put(new FeedId(5), new FeedLabel("한겨레", "정치"));
		this.labels.labels.put(new FeedId(6), new FeedLabel(null, null));
	}

	@Test
	void theDetailCarriesTheArticleItsEvaluationsItsEventMembershipAndItsKeyTerms() throws Exception {
		ArticleId article = new ArticleId(41);
		this.evaluations.ofArticle.put(article, List.of(
				new VersionedEvaluationRow(new TagSetId(2), 2,
						new EvaluationRow(new EvaluationId(19), article, Instant.parse("2026-11-06T00:00:00Z"),
								TopicStatus.LOW_CONFIDENCE, List.of(new NamedTag("politics.legislature", "국회·정당·선거", 0.4)),
								List.of(new TagName("politics", "정치")), List.of(),
								new NamedTag("straight", "스트레이트", 0.7), null)),
				new VersionedEvaluationRow(new TagSetId(1), 1,
						new EvaluationRow(new EvaluationId(9), article, T, TopicStatus.TAGGED,
								List.of(new NamedTag("politics.legislature", "국회·정당·선거", 0.8)),
								List.of(new TagName("politics", "정치")), List.of(new NamedTag("domestic", "국내", 0.9)),
								new NamedTag("straight", "스트레이트", 0.7), 3.25))));
		// 사건 3 은 배정 뒤 사건 7 로 합쳐졌다
		this.review.assignments.put(article, new ArticleAssignment(article, new EventId(7), T, 0.62,
				DecidedBy.EVALUATOR, false, USED, Instant.parse("2026-11-05T00:10:00Z"), new EventJudgementId(88),
				List.of(new AssignmentCandidate(new EventId(7), new EventId(7), 0.62, 0.81),
						new AssignmentCandidate(new EventId(3), new EventId(7), 0.6, 0.3),
						new AssignmentCandidate(new EventId(5), new EventId(5), 0.45, null))));
		this.events.events.put(new EventId(7), new EventView(new EventId(7), EventStatus.OPEN, 4, T,
				Instant.parse("2026-11-06T00:00:00Z"), T));
		this.judgements.judgements.put(new EventJudgementId(88),
				new EventJudgementView(new EventJudgementId(88), article,
						List.of(new JudgedEvent(7, List.of(new ArticleId(30)), 0.81),
								new JudgedEvent(3, List.of(new ArticleId(31)), 0.3)),
						"jev", "jev-1.14.0", "membership-1", 812, Instant.parse("2026-11-05T00:09:00Z")));
		this.review.keyTerms.put(article,
				List.of(new ArticleKeyTerms("e5-small-int8", List.of("국회", "본회의", "예산안"), T)));

		this.mockMvc.perform(authorized(get("/api/admin/articles/41")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"id":41,
					 "article":{"title":"국회 본회의 통과","summary":"<b>요약</b> 원문",
					  "canonicalUrl":"https://news.example/a/41","publishedAt":"2026-11-05T00:00:00Z",
					  "entries":[{"sourceName":"한겨레","section":"정치","categories":["정치"]},
					             {"sourceName":null,"section":null,"categories":[]}]},
					 "evaluations":[
					   {"id":19,"version":2,"versionNumber":2,"evaluatedAt":"2026-11-06T00:00:00Z",
					    "topicStatus":"LOW_CONFIDENCE",
					    "topics":[{"key":"politics.legislature","name":"국회·정당·선거","probability":0.4}],
					    "topicCategories":[{"key":"politics","name":"정치"}],"regions":[],
					    "format":{"key":"straight","name":"스트레이트","probability":0.7},"importance":null},
					   {"id":9,"version":1,"versionNumber":1,"evaluatedAt":"2026-11-05T00:00:00Z","topicStatus":"TAGGED",
					    "topics":[{"key":"politics.legislature","name":"국회·정당·선거","probability":0.8}],
					    "topicCategories":[{"key":"politics","name":"정치"}],
					    "regions":[{"key":"domestic","name":"국내","probability":0.9}],
					    "format":{"key":"straight","name":"스트레이트","probability":0.7},"importance":3.25}],
					 "assignment":{"eventId":7,
					  "event":{"status":"OPEN","articleCount":4,"firstArticleAt":"2026-11-05T00:00:00Z",
					           "lastArticleAt":"2026-11-06T00:00:00Z"},
					  "publishedAt":"2026-11-05T00:00:00Z","score":0.62,"decidedBy":"EVALUATOR","opened":false,
					  "criteria":{"windowSeconds":259200,"upper":0.8,"lower":0.55,"store":0.4,"probability":0.5},
					  "assignedAt":"2026-11-05T00:10:00Z",
					  "candidates":[{"eventId":7,"currentEventId":7,"score":0.62,"probability":0.81},
					                {"eventId":3,"currentEventId":7,"score":0.6,"probability":0.3},
					                {"eventId":5,"currentEventId":5,"score":0.45,"probability":null}],
					  "judgementId":88,
					  "judgement":{"id":88,"evaluator":"jev","modelVersion":"jev-1.14.0","wordingVersion":"membership-1",
					   "inputTokens":812,"judgedAt":"2026-11-05T00:09:00Z",
					   "candidates":[{"eventId":7,"probability":0.81},{"eventId":3,"probability":0.3}]}},
					 "keyTerms":[{"modelVersion":"e5-small-int8","terms":["국회","본회의","예산안"],
					              "createdAt":"2026-11-05T00:00:00Z"}]}
					""", JsonCompareMode.STRICT));
		assertThat(this.articles.calls).containsExactly("findAll [41]");
		assertThat(this.labels.calls).containsExactly("labels [5, 6]");
		assertThat(this.evaluations.calls).containsExactly("ofArticle 41");
		assertThat(this.review.calls).containsExactly("assignmentOf 41", "keyTermsOf 41");
		assertThat(this.events.calls).containsExactly("find 7");
		assertThat(this.judgements.calls).containsExactly("find 88");
	}

	@Test
	void anArticleWithoutEvaluationsAssignmentOrRepresentationHasEmptyParts() throws Exception {
		this.mockMvc.perform(authorized(get("/api/admin/articles/43")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"id":43,
					 "article":{"title":"기준금리 동결","summary":null,"canonicalUrl":"https://news.example/a/43",
					  "publishedAt":"2026-11-05T00:00:00Z",
					  "entries":[{"sourceName":"한겨레","section":"정치","categories":[]}]},
					 "evaluations":[],"assignment":null,"keyTerms":[]}
					""", JsonCompareMode.STRICT));
		// 배정이 없으면 사건 · 판정 원본을 묻지 않는다
		assertThat(this.events.calls).isEmpty();
		assertThat(this.judgements.calls).isEmpty();
	}

	@Test
	void aFormulaAssignmentHasNoJudgementAndAVanishedEventOrDeletedJudgementIsNull() throws Exception {
		this.review.assignments.put(new ArticleId(43), new ArticleAssignment(new ArticleId(43), new EventId(9), T, null,
				DecidedBy.FORMULA, true, USED, T, null, List.of()));

		this.mockMvc.perform(authorized(get("/api/admin/articles/43")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"assignment":{"eventId":9,"event":null,"score":null,"decidedBy":"FORMULA","opened":true,
					 "candidates":[],"judgementId":null,"judgement":null}}
					"""));
		assertThat(this.events.calls).containsExactly("find 9");
		assertThat(this.judgements.calls).isEmpty();

		// 판정 원본은 보존 기간이 지나 지웠다 — 배정의 원본 ID 는 남는다
		this.review.assignments.put(new ArticleId(43), new ArticleAssignment(new ArticleId(43), new EventId(9), T, 0.6,
				DecidedBy.EVALUATOR, false, USED, T, new EventJudgementId(77), List.of()));
		this.mockMvc.perform(authorized(get("/api/admin/articles/43")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"assignment":{"eventId":9,"decidedBy":"EVALUATOR","judgementId":77,"judgement":null}}
					"""));
		assertThat(this.judgements.calls).containsExactly("find 77");
	}

	@Test
	void aDeletedOrImpossibleArticleIsNotFoundWithoutAskingTheOtherModules() throws Exception {
		this.mockMvc.perform(authorized(get("/api/admin/articles/42")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"status":404,"detail":"없는 기사: 42","instance":"/api/admin/articles/42"}
					"""));
		assertThat(this.articles.calls).containsExactly("findAll [42]");
		this.articles.calls.clear();

		this.mockMvc.perform(authorized(get("/api/admin/articles/0")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"status\":404,\"detail\":\"없는 기사: 0\"}"));
		// 1 미만은 기사 조회도 부르지 않는다
		assertThat(this.articles.calls).isEmpty();
		assertThat(this.evaluations.calls).isEmpty();
		assertThat(this.review.calls).isEmpty();
	}

	@Test
	void theDetailNeedsTheOperatorToken() throws Exception {
		this.mockMvc.perform(get("/api/admin/articles/41")).andExpect(status().isUnauthorized());
		assertThat(this.articles.calls).isEmpty();
	}

}
