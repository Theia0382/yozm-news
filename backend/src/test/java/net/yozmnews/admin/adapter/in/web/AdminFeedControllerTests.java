package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static net.yozmnews.admin.adapter.in.web.AdminRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import net.yozmnews.platform.application.WebSliceSecurity;
import net.yozmnews.source.ActivationRefusal;
import net.yozmnews.source.ArticleSampleView;
import net.yozmnews.source.Criterion;
import net.yozmnews.source.CriterionShortfall;
import net.yozmnews.source.DocumentProblemKind;
import net.yozmnews.source.DocumentProblemView;
import net.yozmnews.source.FailurePermanence;
import net.yozmnews.source.FeedEnableResult;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.FetchFailureView;
import net.yozmnews.source.PreviewReport;
import net.yozmnews.source.SkipReason;
import net.yozmnews.source.SkippedItemView;
import net.yozmnews.source.SummaryLengthsView;
import net.yozmnews.source.UnknownFeedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 피드 켜기 · 끄기 · 수집 주기 — 결과 종류마다 판별자와 null 키까지의 응답, 없는 피드 404 · 하한 미만 400 · 동시 수정
 * 409 · 토큰 없음 401.
 * <p>
 * 지키는 규칙: admin-R1 · admin-R2 · admin-R3 · source-R5 · source-R9 · source-R14
 */
@WebMvcTest(controllers = AdminFeedController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeSourceAdministration.Config.class })
class AdminFeedControllerTests {

	private static final PreviewReport PREVIEW = new PreviewReport(3, 2, List.of(new SkippedItemView(2, SkipReason.MISSING_LINK)),
			2, false, new SummaryLengthsView(1, 1, 40, 40, 40), 0, 900, Instant.parse("2026-10-28T00:00:00Z"),
			URI.create("https://news.example/rss-new.xml"),
			List.of(new ArticleSampleView("제목 하나", Instant.parse("2026-10-28T00:00:00Z"), false)));

	private static final String PREVIEW_JSON = """
			{"items":3,"articles":2,"skipped":[{"position":2,"reason":"MISSING_LINK"}],"datedArticles":2,
			 "publishedAtUnread":false,"summaries":{"present":1,"absent":1,"min":40,"median":40,"max":40},
			 "replacedCharacters":0,"decodedCharacters":900,"latestPublishedAt":"2026-10-28T00:00:00Z",
			 "movedTo":"https://news.example/rss-new.xml",
			 "samples":[{"title":"제목 하나","publishedAt":"2026-10-28T00:00:00Z","publishedAtEstimated":false}]}
			""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeSourceAdministration administration;

	@BeforeEach
	void reset() {
		this.administration.reset();
	}

	@Test
	void enabledFeedCarriesItsPreview() throws Exception {
		this.administration.feedEnable = new FeedEnableResult.Enabled(PREVIEW);

		this.mockMvc.perform(authorized(post("/api/admin/feeds/7/enable")))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(content().json("""
					{"result":"ENABLED","preview":%s,"shortfalls":[],"refusal":null,"failure":null}
					""".formatted(PREVIEW_JSON), JsonCompareMode.STRICT));
		assertThat(this.administration.calls).containsExactly("enableFeed 7");
	}

	@Test
	void alreadyEnabledFeedHasNoPreview() throws Exception {
		this.administration.feedEnable = new FeedEnableResult.Enabled(null);

		this.mockMvc.perform(authorized(post("/api/admin/feeds/7/enable")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"ENABLED","preview":null,"shortfalls":[],"refusal":null,"failure":null}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void refusedFeedCarriesTheReason() throws Exception {
		this.administration.feedEnable = new FeedEnableResult.Refused(ActivationRefusal.FEED_NOT_IN_PROFILE);

		this.mockMvc.perform(authorized(post("/api/admin/feeds/7/enable")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"REFUSED","preview":null,"shortfalls":[],"refusal":"FEED_NOT_IN_PROFILE","failure":null}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void feedBelowTheCriteriaCarriesThePreviewAndEachShortfall() throws Exception {
		this.administration.feedEnable = new FeedEnableResult.BelowCriteria(PREVIEW,
				List.of(new CriterionShortfall(Criterion.DATED_ARTICLES, 0.5, 0.9)));

		this.mockMvc.perform(authorized(post("/api/admin/feeds/7/enable")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"BELOW_CRITERIA","preview":%s,
					 "shortfalls":[{"criterion":"DATED_ARTICLES","actual":0.5,"threshold":0.9}],"refusal":null,"failure":null}
					""".formatted(PREVIEW_JSON), JsonCompareMode.STRICT));
	}

	@Test
	void unfetchedFeedCarriesTheFailure() throws Exception {
		this.administration.feedEnable = new FeedEnableResult.Unfetched(new FetchFailureView(
				FetchFailureReason.UNREADABLE, FailurePermanence.PERMANENT, "XML 형식이 틀렸다", null,
				new DocumentProblemView(DocumentProblemKind.MALFORMED_XML, "XML 형식이 틀렸다", 3, 14)));

		this.mockMvc.perform(authorized(post("/api/admin/feeds/7/enable")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"UNFETCHED","preview":null,"shortfalls":[],"refusal":null,
					 "failure":{"reason":"UNREADABLE","permanence":"PERMANENT","message":"XML 형식이 틀렸다","httpStatus":null,
					            "problem":{"kind":"MALFORMED_XML","message":"XML 형식이 틀렸다","line":3,"column":14}}}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void feedWhoseReadingChangedHasNoValues() throws Exception {
		this.administration.feedEnable = new FeedEnableResult.ReadingChanged();

		this.mockMvc.perform(authorized(post("/api/admin/feeds/7/enable")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"result":"READING_CHANGED","preview":null,"shortfalls":[],"refusal":null,"failure":null}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void unknownFeedIsNotFound() throws Exception {
		this.administration.failure = new UnknownFeedException(new FeedId(9));

		this.mockMvc.perform(authorized(post("/api/admin/feeds/9/enable")))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"없는 피드: 9",
					 "instance":"/api/admin/feeds/9/enable"}
					""", JsonCompareMode.STRICT));
	}

	@Test
	void anIdBelowOneIsNotFoundWithoutCalling() throws Exception {
		this.mockMvc.perform(authorized(post("/api/admin/feeds/0/disable")))
			.andExpect(status().isNotFound())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Not Found","status":404,"detail":"없는 피드: 0",
					 "instance":"/api/admin/feeds/0/disable"}
					""", JsonCompareMode.STRICT));
		assertThat(this.administration.calls).isEmpty();
	}

	@Test
	void disablingAnswersNoContent() throws Exception {
		this.mockMvc.perform(authorized(post("/api/admin/feeds/7/disable"))).andExpect(status().isNoContent());
		assertThat(this.administration.calls).containsExactly("disableFeed 7");
	}

	@Test
	void concurrentChangeIsAConflict() throws Exception {
		this.administration.failure = new OptimisticLockingFailureException("version");

		this.mockMvc.perform(authorized(post("/api/admin/feeds/7/disable")))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void pollIntervalIsSetClearedOrRefusedBelowTheMinimum() throws Exception {
		this.mockMvc.perform(json(put("/api/admin/feeds/7/poll-interval"), "{\"minutes\":30}"))
			.andExpect(status().isNoContent());
		this.mockMvc.perform(json(put("/api/admin/feeds/7/poll-interval"), "{\"minutes\":null}"))
			.andExpect(status().isNoContent());
		this.administration.intervalAccepted = false;
		this.mockMvc.perform(json(put("/api/admin/feeds/7/poll-interval"), "{\"minutes\":10}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().json("""
					{"type":"about:blank","title":"Bad Request","status":400,"detail":"수집 주기는 15분 이상이다",
					 "instance":"/api/admin/feeds/7/poll-interval"}
					""", JsonCompareMode.STRICT));

		assertThat(this.administration.calls).containsExactly("changeFeedPollInterval 7 30",
				"changeFeedPollInterval 7 null", "changeFeedPollInterval 7 10");
	}

	@Test
	void requestWithoutTheTokenIsUnauthorizedAndCallsNothing() throws Exception {
		this.mockMvc.perform(post("/api/admin/feeds/7/enable")).andExpect(status().isUnauthorized());
		assertThat(this.administration.calls).isEmpty();
	}

}
