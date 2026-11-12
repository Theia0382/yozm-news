package net.yozmnews.admin.adapter.in.web;

import static net.yozmnews.admin.adapter.in.web.AdminRequests.authorized;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.EntryBucket;
import net.yozmnews.article.EntryCounts;
import net.yozmnews.article.EntryStatistics;
import net.yozmnews.user.application.WebSliceSecurity;
import net.yozmnews.source.FailurePermanence;
import net.yozmnews.source.FeedHealthView;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedStatus;
import net.yozmnews.source.FeedView;
import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.LastFailureView;
import net.yozmnews.source.ProfileState;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.SourceView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 운영자 API 의 수집 현황 — 소스 · 피드 행의 수집 상태와 지표, 소스 행이 피드 행의 칸별 합인지, 엔트리가 없는 비율의 null 키, 기준값이
 * 공개 상수에서 오는지, 토큰 없음 401. 비율 · 합은 손으로 셈한 리터럴이다.
 * <p>
 * 지키는 규칙: admin-R1 · source-R26 · source-R47 · article-R13 · article-R14
 */
@WebMvcTest(controllers = AdminCollectionController.class, properties = AdminRequests.TOKEN_PROPERTY)
@Import({ WebSliceSecurity.class, FakeSourceAdministration.Config.class, FakeArticleAdministration.Config.class })
class AdminCollectionControllerTests {

	private static final Instant FROM = Instant.parse("2026-10-30T03:00:00Z");

	private static final Instant MIDDLE = Instant.parse("2026-10-31T03:00:00Z");

	private static final Instant TO = Instant.parse("2026-11-01T03:00:00Z");

	private static final String NO_ENTRIES = """
			{"entries":0,"duplicates":0,"urlMatches":0,"titleMatches":0,"estimated":0,"guidReuses":0,
			 "duplicateRate":null,"estimatedRate":null,"titleMatchRate":null}""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeSourceAdministration sources;

	@Autowired
	FakeArticleAdministration articles;

	@BeforeEach
	void reset() {
		this.sources.reset();
		this.articles.reset();
	}

	@Test
	void eachFeedCarriesItsCollectionStateAndCountsAndEachSourceTheirBucketSums() throws Exception {
		FeedView failing = new FeedView(new FeedId(11), "one", true, null, true,
				URI.create("https://multi.example/rss/one.xml"), "하나", 30,
				new FeedHealthView(FeedStatus.DEGRADED, Instant.parse("2026-10-31T04:30:00Z"),
						Instant.parse("2026-10-30T22:00:00Z"), 6, 2,
						new LastFailureView(FetchFailureReason.ACCESS_DENIED, FailurePermanence.PERMANENT, "접근 거부",
								403, Instant.parse("2026-10-31T04:00:00Z")),
						Instant.parse("2026-10-30T21:00:00Z"), Instant.parse("2026-10-30T20:30:00Z")));
		FeedView gone = new FeedView(new FeedId(12), "gone", false, 45, false, null, null, null,
				new FeedHealthView(FeedStatus.ACTIVE, null, null, 0, 0, null, null, null));
		this.sources.sources = List.of(
				new SourceView(new SourceId(3), "multi", true, null, ProfileState.AVAILABLE, "멀티일보",
						URI.create("https://multi.example"), LocalDate.parse("2026-10-20"), List.of(failing, gone),
						List.of()),
				new SourceView(new SourceId(4), null, false, null, ProfileState.INVALID, null, null, null, List.of(),
						List.of()));
		this.articles.statistics = new EntryStatistics(FROM, TO,
				List.of(new EntryBucket(FROM, MIDDLE), new EntryBucket(MIDDLE, TO)),
				Map.of(new FeedId(11), List.of(new EntryCounts(10, 1, 1, 0, 0, 0), EntryCounts.NONE), new FeedId(12),
						List.of(EntryCounts.NONE, new EntryCounts(90, 45, 30, 9, 27, 2))));

		this.mockMvc.perform(authorized(get("/api/admin/collection-status")))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					{"from":"2026-10-30T03:00:00Z","to":"2026-11-01T03:00:00Z",
					 "criteria":{"degradedAfterFailures":5,"degradedAfterPermanentFailures":2,"staleAfterHours":168,
					  "recheckIntervalMinutes":60},
					 "sources":[
					  {"id":3,"name":"멀티일보","presetKey":"multi","enabled":true,"profileState":"AVAILABLE",
					   "total":{"entries":100,"duplicates":46,"urlMatches":31,"titleMatches":9,"estimated":27,"guidReuses":2,
					    "duplicateRate":0.46,"estimatedRate":0.27,"titleMatchRate":0.09},
					   "buckets":[
					    {"from":"2026-10-30T03:00:00Z","to":"2026-10-31T03:00:00Z",
					     "metrics":{"entries":10,"duplicates":1,"urlMatches":1,"titleMatches":0,"estimated":0,"guidReuses":0,
					      "duplicateRate":0.1,"estimatedRate":0.0,"titleMatchRate":0.0}},
					    {"from":"2026-10-31T03:00:00Z","to":"2026-11-01T03:00:00Z",
					     "metrics":{"entries":90,"duplicates":45,"urlMatches":30,"titleMatches":9,"estimated":27,"guidReuses":2,
					      "duplicateRate":0.5,"estimatedRate":0.3,"titleMatchRate":0.1}}],
					   "feeds":[
					    {"id":11,"feedKey":"one","section":"하나","enabled":true,"inProfile":true,"status":"DEGRADED",
					     "statusChangedAt":"2026-10-31T04:30:00Z","lastSuccessAt":"2026-10-30T22:00:00Z",
					     "consecutiveFailures":6,"consecutivePermanentFailures":2,
					     "lastFailure":{"reason":"ACCESS_DENIED","permanence":"PERMANENT","message":"접근 거부",
					      "httpStatus":403,"at":"2026-10-31T04:00:00Z"},
					     "lastNewItemAt":"2026-10-30T21:00:00Z","latestPublishedAt":"2026-10-30T20:30:00Z",
					     "total":{"entries":10,"duplicates":1,"urlMatches":1,"titleMatches":0,"estimated":0,"guidReuses":0,
					      "duplicateRate":0.1,"estimatedRate":0.0,"titleMatchRate":0.0},
					     "buckets":[
					      {"from":"2026-10-30T03:00:00Z","to":"2026-10-31T03:00:00Z",
					       "metrics":{"entries":10,"duplicates":1,"urlMatches":1,"titleMatches":0,"estimated":0,"guidReuses":0,
					        "duplicateRate":0.1,"estimatedRate":0.0,"titleMatchRate":0.0}},
					      {"from":"2026-10-31T03:00:00Z","to":"2026-11-01T03:00:00Z","metrics":%1$s}]},
					    {"id":12,"feedKey":"gone","section":null,"enabled":false,"inProfile":false,"status":"ACTIVE",
					     "statusChangedAt":null,"lastSuccessAt":null,"consecutiveFailures":0,
					     "consecutivePermanentFailures":0,"lastFailure":null,"lastNewItemAt":null,"latestPublishedAt":null,
					     "total":{"entries":90,"duplicates":45,"urlMatches":30,"titleMatches":9,"estimated":27,"guidReuses":2,
					      "duplicateRate":0.5,"estimatedRate":0.3,"titleMatchRate":0.1},
					     "buckets":[
					      {"from":"2026-10-30T03:00:00Z","to":"2026-10-31T03:00:00Z","metrics":%1$s},
					      {"from":"2026-10-31T03:00:00Z","to":"2026-11-01T03:00:00Z",
					       "metrics":{"entries":90,"duplicates":45,"urlMatches":30,"titleMatches":9,"estimated":27,"guidReuses":2,
					        "duplicateRate":0.5,"estimatedRate":0.3,"titleMatchRate":0.1}}]}]},
					  {"id":4,"name":null,"presetKey":null,"enabled":false,"profileState":"INVALID","total":%1$s,
					   "buckets":[
					    {"from":"2026-10-30T03:00:00Z","to":"2026-10-31T03:00:00Z","metrics":%1$s},
					    {"from":"2026-10-31T03:00:00Z","to":"2026-11-01T03:00:00Z","metrics":%1$s}],
					   "feeds":[]}]}
					""".formatted(NO_ENTRIES), JsonCompareMode.STRICT));

		assertThat(this.sources.calls).containsExactly("sources");
		assertThat(this.articles.calls).containsExactly("entryStatistics");
	}

	@Test
	void requestWithoutTheTokenIsUnauthorizedAndCallsNothing() throws Exception {
		this.mockMvc.perform(get("/api/admin/collection-status")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/api/admin/collection-status").header("Authorization", "Bearer wrong"))
			.andExpect(status().isUnauthorized());

		assertThat(this.sources.calls).isEmpty();
		assertThat(this.articles.calls).isEmpty();
	}

}
