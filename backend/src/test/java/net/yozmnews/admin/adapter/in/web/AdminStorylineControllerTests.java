package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.evaluation.RelationJudgementLog;
import net.yozmnews.evaluation.RelationJudgementView;
import net.yozmnews.evaluation.RelationReading;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventTally;
import net.yozmnews.event.EventView;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.SourceId;
import net.yozmnews.storyline.LinkScore;
import net.yozmnews.storyline.LinkedBy;
import net.yozmnews.storyline.RelationOutcome;
import net.yozmnews.storyline.StorylineCursor;
import net.yozmnews.storyline.StorylineEvent;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineMembership;
import net.yozmnews.storyline.StorylinePage;
import net.yozmnews.storyline.StorylineRelationAsked;
import net.yozmnews.storyline.StorylineRelationDetail;
import net.yozmnews.storyline.StorylineRelationKind;
import net.yozmnews.storyline.StorylineRelationView;
import net.yozmnews.storyline.StorylineState;
import net.yozmnews.storyline.StorylineSummary;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.storyline.UnlinkedEvents;
import net.yozmnews.user.application.WebSliceSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 스토리라인 — 기간의 목록(값 · 기간 집계 · 큰 여부 · 대표 기사 · 다음 커서 · 소속 없는 사건 · 지금 기준), 스토리라인 하나의
 * 상세(합쳐진 ID · 타임라인과 소속 근거 · 관계 · 물은 쌍과 판정 원본), 기간 · 커서의 400, 없는 스토리라인의 404.
 * <p>
 * 기간은 {@code Asia/Seoul} 11-01 ~ 11-07 → {@code [2026-10-31T15:00Z, 2026-11-07T15:00Z)}. 사건 70 의 대표 기사는 41(피드 5 —
 * 한겨레)이고, 다른 사건은 대표가 없다.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R2 · admin-R3 · admin-R6 · admin-R7
 */
@WebMvcTest(controllers = AdminStorylineController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeStorylineDirectory.Config.class, FakeStorylineReview.Config.class,
		FakeRelationJudgementLog.Config.class, FakeEventDirectory.Config.class, FakeArticleDirectory.Config.class,
		FakeFeedLabels.Config.class })
class AdminStorylineControllerTests {

	private static final String PERIOD = "from=2026-11-01&to=2026-11-07&zone=Asia/Seoul";

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	private static final Instant FIRST = Instant.parse("2026-11-01T00:00:00Z");

	/** 관계 판정 원본을 한 번에 찾는 상한 */
	private static final int MAX_IDS = RelationJudgementLog.MAX_IDS;

	private static final StorylineView SEVEN = new StorylineView(new StorylineId(7), StorylineState.ACTIVE, 3, 12,
			FIRST, T, new EventId(70), Instant.parse("2026-11-01T01:00:00Z"), null);

	private static final StorylineView EIGHT = new StorylineView(new StorylineId(8), StorylineState.QUIET, 2, 4, FIRST,
			Instant.parse("2026-11-03T00:00:00Z"), new EventId(80), FIRST, new StorylineId(7));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeStorylineDirectory directory;

	@Autowired
	FakeStorylineReview review;

	@Autowired
	FakeRelationJudgementLog judgements;

	@Autowired
	FakeEventDirectory events;

	@Autowired
	FakeArticleDirectory articles;

	@Autowired
	FakeFeedLabels labels;

	@BeforeEach
	void reset() {
		this.directory.reset();
		this.review.reset();
		this.judgements.reset();
		this.events.reset();
		this.articles.reset();
		this.labels.reset();
		this.articles.contents.put(new ArticleId(41), new ArticleContent(new ArticleId(41), "국회 본회의 통과", null,
				URI.create("https://news.example/41"), T,
				List.of(new ArticleEntryView(new SourceId(3), new FeedId(5), List.of()))));
		this.labels.labels.put(new FeedId(5), new FeedLabel("한겨레", "정치"));
		this.events.representatives.put(new EventId(70), new ArticleId(41));
	}

	@Test
	void theListCarriesEachStorylineWithItsTallyBigFlagRepresentativeAndTheCurrentCriteria() throws Exception {
		this.directory.page = new StorylinePage(List.of(
				new StorylineSummary(SEVEN, new EventTally(4, 3, new BigDecimal("3.75"), new BigDecimal("15.00")), true),
				new StorylineSummary(EIGHT, new EventTally(1, 1, null, BigDecimal.ZERO), false)),
				new StorylineCursor(Instant.parse("2026-11-03T00:00:00Z"), new StorylineId(8)));
		this.directory.unlinked = new UnlinkedEvents(2, 5);

		this.mockMvc.perform(authorized(get("/api/admin/storylines?" + PERIOD)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"storylines":[
					   {"id":7,"state":"ACTIVE","parentId":null,"eventCount":3,"articleCount":12,
					    "firstArticleAt":"2026-11-01T00:00:00Z","lastArticleAt":"2026-11-05T00:00:00Z","periodArticles":4,
					    "periodSources":3,"importance":3.75,"big":true,"representativeArticleId":41,
					    "representative":{"title":"국회 본회의 통과","sources":["한겨레"]}},
					   {"id":8,"state":"QUIET","parentId":7,"eventCount":2,"articleCount":4,
					    "firstArticleAt":"2026-11-01T00:00:00Z","lastArticleAt":"2026-11-03T00:00:00Z","periodArticles":1,
					    "periodSources":1,"importance":null,"big":false,"representativeArticleId":null,
					    "representative":null}],
					 "next":{"lastArticleAt":"2026-11-03T00:00:00Z","id":8},
					 "unlinked":{"events":2,"articles":5},
					 "linkingCriteria":{"windowSeconds":259200,"threshold":0.62},
					 "relationCriteria":{"probability":0.6,"oneMatter":0.2,"minEvents":3,"candidateScore":0.88,
					                     "windowSeconds":1209600},
					 "maxDays":31}
					""", JsonCompareMode.STRICT));
		assertThat(this.directory.calls).containsExactly(
				"list 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z - 50",
				"unlinked 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z");
		assertThat(this.events.calls).containsExactly("representativesOfAll [70, 80]");
		assertThat(this.articles.calls).containsExactly("findAll [41]");
	}

	@Test
	void theNextPageFollowsTheCursor() throws Exception {
		this.mockMvc
			.perform(authorized(get(
					"/api/admin/storylines?" + PERIOD + "&afterLastArticleAt=2026-11-03T00:00:00Z&afterId=8")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.storylines").isEmpty())
			.andExpect(jsonPath("$.next").isEmpty());
		assertThat(this.directory.calls).first()
			.isEqualTo("list 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z 2026-11-03T00:00:00Z/8 50");
		// 빈 쪽이면 대표를 묻지 않는다
		assertThat(this.events.calls).isEmpty();
	}

	@Test
	void aBadPeriodOrCursorIsABadRequest() throws Exception {
		badRequest("/api/admin/storylines?" + PERIOD + "&afterId=8", "afterLastArticleAt 과 afterId 는 함께 준다");
		badRequest("/api/admin/storylines?" + PERIOD + "&afterLastArticleAt=2026-11-03T00:00:00Z&afterId=0",
				"스토리라인 ID 는 1 이상이다: 0");
		badRequest("/api/admin/storylines?" + PERIOD + "&afterLastArticleAt=yesterday&afterId=8",
				"시각은 ISO-8601 형식이다: yesterday");
		badRequest("/api/admin/storylines?from=2026-10-01&to=2026-11-07&zone=Asia/Seoul", "기간은 1일에서 31일까지다");
		assertThat(this.directory.calls).isEmpty();
	}

	@Test
	void theDetailCarriesTheTimelineWithJudgementsRelationsAndAskedPairs() throws Exception {
		// 경로의 5 는 합쳐진 스토리라인 — 남는 7 을 낸다
		this.directory.storylines.put(5L, SEVEN);
		this.directory.timelines.put(7L, List.of(new StorylineEvent(event(70, EventStatus.OPEN, 4), true),
				new StorylineEvent(event(80, EventStatus.CLOSED, 2), true),
				new StorylineEvent(event(85, EventStatus.CLOSED, 1), false)));
		this.review.memberships.put(7L, List.of(
				new StorylineMembership(new StorylineId(7), new EventId(70), LinkedBy.RULE, new LinkScore(0.81, 0.77, 0.4),
						FakeStorylineReview.LINKING, T),
				new StorylineMembership(new StorylineId(7), new EventId(80), LinkedBy.MERGE, null, null, T),
				new StorylineMembership(new StorylineId(8), new EventId(80), LinkedBy.PAIR, new LinkScore(0.7, 0.66, 0.2),
						FakeStorylineReview.LINKING, T),
				new StorylineMembership(new StorylineId(8), new EventId(85), LinkedBy.RULE, new LinkScore(0.9, 0.88, 0.5),
						FakeStorylineReview.LINKING, T),
				// 타임라인에 없는 사건(그새 합쳐짐)의 근거는 싣지 않는다
				new StorylineMembership(new StorylineId(8), new EventId(99), LinkedBy.RULE, new LinkScore(0.6, 0.6, 0.1),
						FakeStorylineReview.LINKING, T)));
		this.review.relations.put(7L, List.of(
				new StorylineRelationDetail(new StorylineRelationView(StorylineRelationKind.CONTAINS, new StorylineId(7),
						new StorylineId(8), T), 0.7, new RelationJudgementId(41), FakeStorylineReview.RELATION),
				new StorylineRelationDetail(new StorylineRelationView(StorylineRelationKind.FOLLOWS, new StorylineId(7),
						new StorylineId(11), T), 0.66, new RelationJudgementId(42), FakeStorylineReview.RELATION)));
		this.review.asked.put(7L, List.of(
				asked(11, 3, RelationOutcome.LINKED, 42L), asked(12, 4, RelationOutcome.NOTHING_TO_ASK, null),
				asked(13, 5, RelationOutcome.LOW_PROBABILITY, 43L), asked(14, 6, RelationOutcome.NOT_ONE_MATTER, 44L),
				asked(15, 3, RelationOutcome.NOT_RELATED, 45L)));
		// 원본 42 는 지워졌다
		judgement(41, 7, 8, new RelationReading.Read(Map.of(RelationAnswer.NONE, 0.1,
				RelationAnswer.SECOND_PART_OF_FIRST, 0.7, RelationAnswer.FIRST_PART_OF_SECOND, 0.05,
				RelationAnswer.FIRST_LED_TO_SECOND, 0.1, RelationAnswer.SECOND_LED_TO_FIRST, 0.05), 0.8, 0.6));
		judgement(43, 13, 7, new RelationReading.Unread(RelationReading.Unread.Reason.OTHER_EVALUATOR, "다른 평가기"));
		judgement(44, 7, 14, new RelationReading.Unread(RelationReading.Unread.Reason.NO_EVALUATOR, "평가기 없음"));
		judgement(45, 15, 7, new RelationReading.Unread(RelationReading.Unread.Reason.UNREADABLE, "읽을 수 없다"));

		this.mockMvc.perform(authorized(get("/api/admin/storylines/5")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"requestedId":5,"id":7,"state":"ACTIVE","parentId":null,"eventCount":3,"articleCount":12,
					 "firstArticleAt":"2026-11-01T00:00:00Z","lastArticleAt":"2026-11-05T00:00:00Z",
					 "createdAt":"2026-11-01T01:00:00Z","representativeArticleId":41,
					 "representative":{"title":"국회 본회의 통과","sources":["한겨레"]},
					 "events":[
					   {"id":70,"status":"OPEN","articleCount":4,"firstArticleAt":"2026-11-05T00:00:00Z",
					    "lastArticleAt":"2026-11-05T00:00:00Z","direct":true,"representativeArticleId":41,
					    "representative":{"title":"국회 본회의 통과","sources":["한겨레"]},
					    "memberships":[
					      {"storylineId":7,"linkedBy":"RULE","score":0.81,"cosine":0.77,"keyTermOverlap":0.4,
					       "criteria":{"windowSeconds":259200,"threshold":0.62},"linkedAt":"2026-11-05T00:00:00Z"}]},
					   {"id":80,"status":"CLOSED","articleCount":2,"firstArticleAt":"2026-11-05T00:00:00Z",
					    "lastArticleAt":"2026-11-05T00:00:00Z","direct":true,"representativeArticleId":null,
					    "representative":null,
					    "memberships":[
					      {"storylineId":7,"linkedBy":"MERGE","score":null,"cosine":null,"keyTermOverlap":null,
					       "criteria":null,"linkedAt":"2026-11-05T00:00:00Z"},
					      {"storylineId":8,"linkedBy":"PAIR","score":0.7,"cosine":0.66,"keyTermOverlap":0.2,
					       "criteria":{"windowSeconds":259200,"threshold":0.62},"linkedAt":"2026-11-05T00:00:00Z"}]},
					   {"id":85,"status":"CLOSED","articleCount":1,"firstArticleAt":"2026-11-05T00:00:00Z",
					    "lastArticleAt":"2026-11-05T00:00:00Z","direct":false,"representativeArticleId":null,
					    "representative":null,
					    "memberships":[
					      {"storylineId":8,"linkedBy":"RULE","score":0.9,"cosine":0.88,"keyTermOverlap":0.5,
					       "criteria":{"windowSeconds":259200,"threshold":0.62},"linkedAt":"2026-11-05T00:00:00Z"}]}],
					 "relations":[
					   {"kind":"CONTAINS","sourceId":7,"targetId":8,"probability":0.7,"linkedAt":"2026-11-05T00:00:00Z",
					    "criteria":{"probability":0.6,"oneMatter":0.2,"minEvents":3,"candidateScore":0.88,
					                "windowSeconds":1209600},
					    "judgementId":41,
					    "judgement":{"id":41,"firstId":7,"secondId":8,"modelVersion":"jev-1.14.0",
					                 "wordingVersion":"relation-1","inputTokens":1924,"judgedAt":"2026-11-05T00:00:00Z",
					                 "reading":"READ","none":0.1,"secondPartOfFirst":0.7,"firstPartOfSecond":0.05,
					                 "firstLedToSecond":0.1,"secondLedToFirst":0.05,"firstOneMatter":0.8,
					                 "secondOneMatter":0.6,"message":null}},
					   {"kind":"FOLLOWS","sourceId":7,"targetId":11,"probability":0.66,"linkedAt":"2026-11-05T00:00:00Z",
					    "criteria":{"probability":0.6,"oneMatter":0.2,"minEvents":3,"candidateScore":0.88,
					                "windowSeconds":1209600},
					    "judgementId":42,"judgement":null}],
					 "asked":[
					   {"otherId":11,"smallerEvents":3,"outcome":"LINKED","askedAt":"2026-11-05T00:00:00Z",
					    "criteria":{"probability":0.6,"oneMatter":0.2,"minEvents":3,"candidateScore":0.88,
					                "windowSeconds":1209600},
					    "judgementId":42,"judgement":null},
					   {"otherId":12,"smallerEvents":4,"outcome":"NOTHING_TO_ASK","askedAt":"2026-11-05T00:00:00Z",
					    "criteria":{"probability":0.6,"oneMatter":0.2,"minEvents":3,"candidateScore":0.88,
					                "windowSeconds":1209600},
					    "judgementId":null,"judgement":null},
					   {"otherId":13,"smallerEvents":5,"outcome":"LOW_PROBABILITY","askedAt":"2026-11-05T00:00:00Z",
					    "criteria":{"probability":0.6,"oneMatter":0.2,"minEvents":3,"candidateScore":0.88,
					                "windowSeconds":1209600},
					    "judgementId":43,
					    "judgement":{"id":43,"firstId":13,"secondId":7,"modelVersion":"jev-1.14.0",
					                 "wordingVersion":"relation-1","inputTokens":1924,"judgedAt":"2026-11-05T00:00:00Z",
					                 "reading":"OTHER_EVALUATOR","none":null,"secondPartOfFirst":null,
					                 "firstPartOfSecond":null,"firstLedToSecond":null,"secondLedToFirst":null,
					                 "firstOneMatter":null,"secondOneMatter":null,"message":"다른 평가기"}},
					   {"otherId":14,"smallerEvents":6,"outcome":"NOT_ONE_MATTER","askedAt":"2026-11-05T00:00:00Z",
					    "criteria":{"probability":0.6,"oneMatter":0.2,"minEvents":3,"candidateScore":0.88,
					                "windowSeconds":1209600},
					    "judgementId":44,
					    "judgement":{"id":44,"firstId":7,"secondId":14,"modelVersion":"jev-1.14.0",
					                 "wordingVersion":"relation-1","inputTokens":1924,"judgedAt":"2026-11-05T00:00:00Z",
					                 "reading":"NO_EVALUATOR","none":null,"secondPartOfFirst":null,
					                 "firstPartOfSecond":null,"firstLedToSecond":null,"secondLedToFirst":null,
					                 "firstOneMatter":null,"secondOneMatter":null,"message":"평가기 없음"}},
					   {"otherId":15,"smallerEvents":3,"outcome":"NOT_RELATED","askedAt":"2026-11-05T00:00:00Z",
					    "criteria":{"probability":0.6,"oneMatter":0.2,"minEvents":3,"candidateScore":0.88,
					                "windowSeconds":1209600},
					    "judgementId":45,
					    "judgement":{"id":45,"firstId":15,"secondId":7,"modelVersion":"jev-1.14.0",
					                 "wordingVersion":"relation-1","inputTokens":1924,"judgedAt":"2026-11-05T00:00:00Z",
					                 "reading":"UNREADABLE","none":null,"secondPartOfFirst":null,
					                 "firstPartOfSecond":null,"firstLedToSecond":null,"secondLedToFirst":null,
					                 "firstOneMatter":null,"secondOneMatter":null,"message":"읽을 수 없다"}}]}
					""", JsonCompareMode.STRICT));
		assertThat(this.directory.calls).containsExactly("find 5", "events 7");
		// 운영자 조회는 남는 스토리라인 ID 로 부른다
		assertThat(this.review.calls).containsExactly("memberships 7", "relations 7", "asked 7");
		// 원본은 관계 · 물은 쌍의 것을 한 번씩, 한 번에 찾는다
		assertThat(this.judgements.calls).containsExactly(List.of(41L, 42L, 43L, 44L, 45L));
		// 대표는 스토리라인의 대표 사건과 타임라인 사건을 한 번에
		assertThat(this.events.calls).containsExactly("representativesOfAll [70, 80, 85]");
	}

	@Test
	void judgementsBeyondTheLimitAreFoundInChunks() throws Exception {
		this.directory.storylines.put(7L, SEVEN);
		List<StorylineRelationDetail> relations = LongStream.rangeClosed(1, MAX_IDS + 1)
			.mapToObj(id -> new StorylineRelationDetail(new StorylineRelationView(StorylineRelationKind.FOLLOWS,
					new StorylineId(7), new StorylineId(1000 + id), T), 0.7, new RelationJudgementId(id),
					FakeStorylineReview.RELATION))
			.toList();
		this.review.relations.put(7L, relations);
		LongStream.rangeClosed(1, MAX_IDS + 1)
			.forEach(id -> judgement(id, 7, 1000 + id,
					new RelationReading.Unread(RelationReading.Unread.Reason.NO_EVALUATOR, "평가기 없음")));

		this.mockMvc.perform(authorized(get("/api/admin/storylines/7")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.relations.length()").value(501))
			.andExpect(jsonPath("$.relations[500].judgementId").value(501))
			.andExpect(jsonPath("$.relations[500].judgement.id").value(501));
		// 500 개, 1 개로 나눠 찾는다 — 지워지지 않은 원본은 모두 실린다
		assertThat(this.judgements.calls).extracting(List::size).containsExactly(500, 1);
	}

	@Test
	void anUnknownStorylineIsNotFound() throws Exception {
		this.mockMvc.perform(authorized(get("/api/admin/storylines/99")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"status\":404,\"detail\":\"없는 스토리라인: 99\"}"));
		this.mockMvc.perform(authorized(get("/api/admin/storylines/0")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"status\":404,\"detail\":\"없는 스토리라인: 0\"}"));
		assertThat(this.directory.calls).containsExactly("find 99");
		assertThat(this.review.calls).isEmpty();
	}

	@Test
	void theOperatorTokenIsRequired() throws Exception {
		this.mockMvc.perform(get("/api/admin/storylines?" + PERIOD)).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/storylines/7")).andExpect(status().isUnauthorized());
	}

	private static EventView event(long id, EventStatus status, int articles) {
		return new EventView(new EventId(id), status, articles, T, T, T);
	}

	private static StorylineRelationAsked asked(long other, int smaller, RelationOutcome outcome, Long judgement) {
		return new StorylineRelationAsked(new StorylineId(other), smaller, outcome,
				(judgement == null) ? null : new RelationJudgementId(judgement), FakeStorylineReview.RELATION, T);
	}

	private void judgement(long id, long first, long second, RelationReading reading) {
		this.judgements.views.put(new RelationJudgementId(id), new RelationJudgementView(new RelationJudgementId(id),
				first, second, "jev", "jev-1.14.0", "relation-1", 1924, T, reading));
	}

	private void badRequest(String url, String detail) throws Exception {
		this.mockMvc.perform(authorized(get(url)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json("{\"status\":400,\"detail\":\"" + detail + "\"}"));
	}

}
