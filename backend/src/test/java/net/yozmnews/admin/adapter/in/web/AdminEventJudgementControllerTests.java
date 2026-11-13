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

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EventJudgementCursor;
import net.yozmnews.evaluation.EventJudgementId;
import net.yozmnews.evaluation.EventJudgementLog;
import net.yozmnews.evaluation.EventJudgementPage;
import net.yozmnews.evaluation.EventJudgementView;
import net.yozmnews.evaluation.JudgedEvent;
import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.EventId;
import net.yozmnews.event.JudgementUse;
import net.yozmnews.event.MergePair;
import net.yozmnews.user.application.WebSliceSecurity;
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
 * 운영자 API 의 판정 기록 — 판정 원본과 쓰임(배정 · 병합 · 반영 안 됨)의 평평한 행, 기사 정보, 지금 배정 기준, 쪽 크기 상수, 기간 · 커서의 400,
 * 운영자 토큰.
 * <p>
 * 기간은 {@code Asia/Seoul} 11-01 ~ 11-07 → {@code [2026-10-31T15:00Z, 2026-11-07T15:00Z)} 로 판정 원본 조회에 간다. 기사 41 은 피드 5(한겨레)에
 * 실렸고, 기사 42 · 43 은 그새 지워 기사 조회에 없다.
 * <p>
 * 지키는 규칙: admin-R3 · admin-R6 · admin-R7
 */
@WebMvcTest(controllers = AdminEventJudgementController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeEventJudgementLog.Config.class, FakeEventReview.Config.class,
		FakeArticleDirectory.Config.class, FakeFeedLabels.Config.class })
class AdminEventJudgementControllerTests {

	private static final String PERIOD = "from=2026-11-01&to=2026-11-07&zone=Asia/Seoul";

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeEventJudgementLog log;

	@Autowired
	FakeEventReview review;

	@Autowired
	FakeArticleDirectory articles;

	@Autowired
	FakeFeedLabels labels;

	@BeforeEach
	void reset() {
		this.log.reset();
		this.review.reset();
		this.articles.reset();
		this.labels.reset();
		this.articles.contents.put(new ArticleId(41), new ArticleContent(new ArticleId(41), "국회 본회의 통과", null,
				URI.create("https://news.example/41"), T,
				List.of(new ArticleEntryView(new SourceId(3), new FeedId(5), List.of()))));
		this.labels.labels.put(new FeedId(5), new FeedLabel("한겨레", "정치"));
	}

	@Test
	void eachJudgementCarriesItsUseAsAFlatRowWithTheCriteria() throws Exception {
		this.log.page = new EventJudgementPage(List.of(view(103, 41, 12, 0.83), view(102, 42, 15, 0.66),
				view(101, 43, 12, 0.2)), new EventJudgementCursor(T, new EventJudgementId(101)));
		this.review.criteria = new AssignmentCriteria(Duration.ofHours(48), 0.85, 0.5, 0.35, 0.6);
		this.review.uses.put(new EventJudgementId(103),
				new JudgementUse.Assignment(new ArticleId(41), new EventId(12), DecidedBy.EVALUATOR));
		this.review.uses.put(new EventJudgementId(102), new JudgementUse.Merge(
				List.of(new MergePair(new EventId(11), new EventId(15), 0.66, new EventId(11)))));

		this.mockMvc.perform(authorized(get("/api/admin/event-judgements?" + PERIOD)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"judgements":[
					   {"id":103,"judgedAt":"2026-11-05T00:00:00Z","articleId":41,
					    "article":{"title":"국회 본회의 통과","sources":["한겨레"]},
					    "candidates":[{"eventId":12,"probability":0.83}],"inputTokens":812,"modelVersion":"jev-1.14.0",
					    "use":"ASSIGNMENT","assignedEventId":12,"decidedBy":"EVALUATOR","mergePairs":[]},
					   {"id":102,"judgedAt":"2026-11-05T00:00:00Z","articleId":42,"article":null,
					    "candidates":[{"eventId":15,"probability":0.66}],"inputTokens":812,"modelVersion":"jev-1.14.0",
					    "use":"MERGE","assignedEventId":null,"decidedBy":null,
					    "mergePairs":[{"low":11,"high":15,"probability":0.66,"survivor":11}]},
					   {"id":101,"judgedAt":"2026-11-05T00:00:00Z","articleId":43,"article":null,
					    "candidates":[{"eventId":12,"probability":0.2}],"inputTokens":812,"modelVersion":"jev-1.14.0",
					    "use":"UNUSED","assignedEventId":null,"decidedBy":null,"mergePairs":[]}],
					 "next":{"judgedAt":"2026-11-05T00:00:00Z","id":101},
					 "criteria":{"windowSeconds":172800,"upper":0.85,"lower":0.5,"store":0.35,"probability":0.6},
					 "maxDays":31}
					""", JsonCompareMode.STRICT));
		assertThat(this.log.calls)
			.containsExactly("list 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z after null size 50");
		assertThat(this.review.calls).containsExactly("judgementUses [103, 102, 101]", "assignmentCriteria");
		assertThat(this.articles.calls).containsExactly("findAll [41, 42, 43]");
	}

	@Test
	void theCursorIsPassedOn() throws Exception {
		this.log.page = new EventJudgementPage(List.of(), null);

		this.mockMvc
			.perform(authorized(get("/api/admin/event-judgements?" + PERIOD
					+ "&afterJudgedAt=2026-11-05T00:00:00Z&afterId=101")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"judgements":[],"next":null}
					"""));
		assertThat(this.log.calls).containsExactly(
				"list 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z after 2026-11-05T00:00:00Z/101 size 50");
	}

	@Test
	void thePageSizeIsWithinTheLogLimit() {
		assertThat(AdminEventJudgementController.PAGE_SIZE).isBetween(1, EventJudgementLog.MAX_PAGE_SIZE);
	}

	@Test
	void thirtyOneDatesAreAccepted() throws Exception {
		this.log.page = new EventJudgementPage(List.of(), null);

		this.mockMvc.perform(authorized(get("/api/admin/event-judgements?from=2026-11-01&to=2026-12-01&zone=Asia/Seoul")))
			.andExpect(status().isOk());
		assertThat(this.log.calls)
			.containsExactly("list 2026-10-31T15:00:00Z ~ 2026-12-01T15:00:00Z after null size 50");
	}

	@Test
	void periodsAndCursorsOutOfShapeAreBadRequests() throws Exception {
		badRequest("/api/admin/event-judgements?from=2026-11-01&to=2026-12-02&zone=Asia/Seoul",
				"기간은 1일에서 31일까지다");
		badRequest("/api/admin/event-judgements?" + PERIOD + "&afterJudgedAt=2026-11-05T00:00:00Z",
				"afterJudgedAt 과 afterId 는 함께 준다");
		badRequest("/api/admin/event-judgements?" + PERIOD + "&afterJudgedAt=2026-11-05T00:00:00Z&afterId=0",
				"판정 원본 ID 는 1 이상이다: 0");
		assertThat(this.log.calls).isEmpty();
	}

	@Test
	void theJudgementsNeedTheOperatorToken() throws Exception {
		this.mockMvc.perform(get("/api/admin/event-judgements?" + PERIOD)).andExpect(status().isUnauthorized());
		assertThat(this.log.calls).isEmpty();
	}

	private static EventJudgementView view(long id, long article, long event, double probability) {
		return new EventJudgementView(new EventJudgementId(id), new ArticleId(article),
				List.of(new JudgedEvent(event, List.of(new ArticleId(3)), probability)), "jev", "jev-1.14.0",
				"membership-1", 812, T);
	}

	private void badRequest(String url, String detail) throws Exception {
		this.mockMvc.perform(authorized(get(url)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json("{\"status\":400,\"detail\":\"" + detail + "\"}"));
	}

}
