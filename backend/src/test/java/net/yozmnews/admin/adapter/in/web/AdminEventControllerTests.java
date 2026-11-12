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

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleEntryView;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.DecidedBy;
import net.yozmnews.event.EventArticleView;
import net.yozmnews.event.EventCursor;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPage;
import net.yozmnews.event.EventStatus;
import net.yozmnews.event.EventSummary;
import net.yozmnews.event.EventTally;
import net.yozmnews.event.EventView;
import net.yozmnews.event.MergedEvent;
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
 * 운영자 API 의 사건 목록 · 상세 — JSON 모양, 대표 기사 · 기사 정보 맞붙이기, 쪽 크기 상수, 기간 · 커서의 400, 합쳐진 사건 ID, 없는 사건의
 * 404, 운영자 토큰.
 * <p>
 * 기간은 {@code Asia/Seoul} 11-01 ~ 11-07 → {@code [2026-10-31T15:00Z, 2026-11-07T15:00Z)} 로 공개 조회에 간다. 기사 41 은 피드 5(한겨레 ·
 * 정치)에 실렸고, 기사 42 는 그새 지워 기사 조회에 없다.
 * <p>
 * 지키는 규칙: admin-R2 · admin-R3 · admin-R6 · admin-R7
 */
@WebMvcTest(controllers = AdminEventController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeEventDirectory.Config.class, FakeEventReview.Config.class,
		FakeArticleDirectory.Config.class, FakeFeedLabels.Config.class })
class AdminEventControllerTests {

	private static final String PERIOD = "from=2026-11-01&to=2026-11-07&zone=Asia/Seoul";

	private static final Instant T = Instant.parse("2026-11-05T00:00:00Z");

	private static final EventView SEVEN = new EventView(new EventId(7), EventStatus.CLOSED, 5, T,
			Instant.parse("2026-11-06T00:00:00Z"), T);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeEventDirectory directory;

	@Autowired
	FakeEventReview review;

	@Autowired
	FakeArticleDirectory articles;

	@Autowired
	FakeFeedLabels labels;

	@BeforeEach
	void reset() {
		this.directory.reset();
		this.review.reset();
		this.articles.reset();
		this.labels.reset();
		this.articles.contents.put(new ArticleId(41), new ArticleContent(new ArticleId(41), "국회 본회의 통과", null,
				URI.create("https://news.example/41"), T,
				List.of(new ArticleEntryView(new SourceId(3), new FeedId(5), List.of()))));
		this.labels.labels.put(new FeedId(5), new FeedLabel("한겨레", "정치"));
	}

	@Test
	void theListCarriesEachEventWithItsPeriodTallyAndRepresentative() throws Exception {
		this.directory.page = new EventPage(List.of(
				new EventSummary(SEVEN, new EventTally(3, 2, new BigDecimal("3.5")), new ArticleId(41)),
				new EventSummary(new EventView(new EventId(5), EventStatus.OPEN, 1, T, T, T), new EventTally(1, 1, null),
						new ArticleId(42))),
				new EventCursor(T, new EventId(5)));

		this.mockMvc.perform(authorized(get("/api/admin/events?" + PERIOD)))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"events":[
					  {"id":7,"status":"CLOSED","articleCount":5,"firstArticleAt":"2026-11-05T00:00:00Z",
					   "lastArticleAt":"2026-11-06T00:00:00Z","periodArticles":3,"periodSources":2,"importance":3.5,
					   "representativeId":41,"representative":{"title":"국회 본회의 통과","sources":["한겨레"]}},
					  {"id":5,"status":"OPEN","articleCount":1,"firstArticleAt":"2026-11-05T00:00:00Z",
					   "lastArticleAt":"2026-11-05T00:00:00Z","periodArticles":1,"periodSources":1,"importance":null,
					   "representativeId":42,"representative":null}],
					 "next":{"lastArticleAt":"2026-11-05T00:00:00Z","id":5},"maxDays":31}
					""", JsonCompareMode.STRICT));
		assertThat(this.directory.calls)
			.containsExactly("list 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z after null size 50");
		assertThat(this.articles.calls).containsExactly("findAll [41, 42]");
	}

	@Test
	void theCursorIsPassedOnAndTheLastPageHasNoNext() throws Exception {
		this.directory.page = new EventPage(List.of(), null);

		this.mockMvc
			.perform(authorized(
					get("/api/admin/events?" + PERIOD + "&afterLastArticleAt=2026-11-05T10:00:00Z&afterId=7")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"events":[],"next":null,"maxDays":31}
					""", JsonCompareMode.STRICT));
		assertThat(this.directory.calls)
			.containsExactly("list 2026-10-31T15:00:00Z ~ 2026-11-07T15:00:00Z after 2026-11-05T10:00:00Z/7 size 50");
	}

	@Test
	void thePageSizeIsWithinTheDirectoryLimit() {
		assertThat(AdminEventController.PAGE_SIZE).isBetween(1, EventDirectory.MAX_PAGE_SIZE);
	}

	@Test
	void thirtyOneDatesAcrossATransitionAreAccepted() throws Exception {
		this.directory.page = new EventPage(List.of(), null);

		this.mockMvc
			.perform(authorized(get("/api/admin/events?from=2026-10-15&to=2026-11-14&zone=America/New_York")))
			.andExpect(status().isOk());
		assertThat(this.directory.calls)
			.containsExactly("list 2026-10-15T04:00:00Z ~ 2026-11-15T05:00:00Z after null size 50");
	}

	@Test
	void periodsAndCursorsOutOfShapeAreBadRequests() throws Exception {
		badRequest("/api/admin/events?from=2026-11-01&to=2026-12-02&zone=Asia/Seoul", "기간은 1일에서 31일까지다");
		badRequest("/api/admin/events?from=2026-11-01&to=2026-11-07&zone=Mars/Base", "없는 시간대다: Mars/Base");
		badRequest("/api/admin/events?" + PERIOD + "&afterId=7", "afterLastArticleAt 과 afterId 는 함께 준다");
		badRequest("/api/admin/events?" + PERIOD + "&afterLastArticleAt=yesterday&afterId=7",
				"시각은 ISO-8601 형식이다: yesterday");
		badRequest("/api/admin/events?" + PERIOD + "&afterLastArticleAt=2026-11-05T10:00:00Z&afterId=0",
				"사건 ID 는 1 이상이다: 0");
		assertThat(this.directory.calls).isEmpty();
	}

	@Test
	void theDetailCarriesItsArticlesRepresentativeAndMergedEvents() throws Exception {
		this.directory.events.put(new EventId(7), SEVEN);
		this.directory.articles.put(new EventId(7),
				List.of(new EventArticleView(new ArticleId(41), T, 0.91, DecidedBy.FORMULA, T),
						new EventArticleView(new ArticleId(42), Instant.parse("2026-11-06T00:00:00Z"), null,
								DecidedBy.JUDGEMENT_FAILED, Instant.parse("2026-11-06T01:00:00Z"))));
		this.directory.representatives.put(new EventId(7), new ArticleId(41));
		// 합쳐진 사건의 대표 43 은 재판정으로 이 사건을 떠나 사건의 기사에 없다 — 따로 물어 맞붙인다. 4 는 대표를 남기지 않았다
		this.articles.contents.put(new ArticleId(43), new ArticleContent(new ArticleId(43), "기준금리 동결", null,
				URI.create("https://news.example/43"), T,
				List.of(new ArticleEntryView(new SourceId(3), new FeedId(5), List.of()))));
		this.review.merged.put(new EventId(7),
				List.of(new MergedEvent(new EventId(3), 2, T, T, T, Instant.parse("2026-11-06T06:00:00Z"), new ArticleId(43),
						0.74),
						new MergedEvent(new EventId(4), 1, T, T, T, Instant.parse("2026-11-06T07:00:00Z"), null, null)));

		this.mockMvc.perform(authorized(get("/api/admin/events/7")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"requestedId":7,"id":7,"status":"CLOSED","articleCount":5,"firstArticleAt":"2026-11-05T00:00:00Z",
					 "lastArticleAt":"2026-11-06T00:00:00Z","createdAt":"2026-11-05T00:00:00Z","representativeId":41,
					 "representative":{"title":"국회 본회의 통과","sources":["한겨레"]},
					 "articles":[
					   {"articleId":41,"article":{"title":"국회 본회의 통과","sources":["한겨레"]},
					    "publishedAt":"2026-11-05T00:00:00Z","score":0.91,"decidedBy":"FORMULA",
					    "assignedAt":"2026-11-05T00:00:00Z"},
					   {"articleId":42,"article":null,"publishedAt":"2026-11-06T00:00:00Z","score":null,
					    "decidedBy":"JUDGEMENT_FAILED","assignedAt":"2026-11-06T01:00:00Z"}],
					 "mergedEvents":[
					   {"id":3,"articleCount":2,"firstArticleAt":"2026-11-05T00:00:00Z",
					    "lastArticleAt":"2026-11-05T00:00:00Z","createdAt":"2026-11-05T00:00:00Z",
					    "mergedAt":"2026-11-06T06:00:00Z","representativeId":43,
					    "representative":{"title":"기준금리 동결","sources":["한겨레"]},"probability":0.74},
					   {"id":4,"articleCount":1,"firstArticleAt":"2026-11-05T00:00:00Z",
					    "lastArticleAt":"2026-11-05T00:00:00Z","createdAt":"2026-11-05T00:00:00Z",
					    "mergedAt":"2026-11-06T07:00:00Z","representativeId":null,"representative":null,
					    "probability":null}]}
					""", JsonCompareMode.STRICT));
		assertThat(this.directory.calls).containsExactly("find 7", "articles 7", "representativesOfAll [7]");
		assertThat(this.review.calls).containsExactly("mergedInto 7");
		assertThat(this.articles.calls).containsExactly("findAll [41, 42, 43]");
	}

	@Test
	void aMergedEventIdOpensItsSurvivor() throws Exception {
		// 찾기가 합쳐진 사건 3 의 남는 사건 7 을 낸다
		this.directory.events.put(new EventId(3), SEVEN);

		this.mockMvc.perform(authorized(get("/api/admin/events/3")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"requestedId":3,"id":7,"representativeId":null,"representative":null,"articles":[],
					 "mergedEvents":[]}
					"""));
		assertThat(this.directory.calls).containsExactly("find 3", "articles 7", "representativesOfAll [7]");
	}

	@Test
	void anUnknownEventIsNotFound() throws Exception {
		this.mockMvc.perform(authorized(get("/api/admin/events/99")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"status\":404,\"detail\":\"없는 사건: 99\"}"));
		this.mockMvc.perform(authorized(get("/api/admin/events/0")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("{\"status\":404,\"detail\":\"없는 사건: 0\"}"));
		assertThat(this.directory.calls).containsExactly("find 99");
	}

	@Test
	void theEventsNeedTheOperatorToken() throws Exception {
		this.mockMvc.perform(get("/api/admin/events?" + PERIOD)).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/events/7")).andExpect(status().isUnauthorized());
		assertThat(this.directory.calls).isEmpty();
	}

	private void badRequest(String url, String detail) throws Exception {
		this.mockMvc.perform(authorized(get(url)))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json("{\"status\":400,\"detail\":\"" + detail + "\"}"));
	}

}
