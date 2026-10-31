package net.yozmnews.source.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import net.yozmnews.source.ActivationRefusal;
import net.yozmnews.source.Criterion;
import net.yozmnews.source.FetchFailureReason;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.feed.FeedNormalization;
import net.yozmnews.source.domain.fetch.FetchFailure;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.PresetKey;
import net.yozmnews.source.domain.registry.Feed;
import net.yozmnews.source.domain.registry.ProfileResolution;
import net.yozmnews.source.domain.registry.RegistryFixtures;
import org.junit.jupiter.api.Test;

/**
 * 피드 켜기 관문의 판정 순서 — 요청 전 설정 판정, 받은 결과의 판정, 저장 직전 재판정. 피드와 프로필은 합성 프로필(피드 one · two)이다.
 * <p>
 * 지키는 규칙: source-R5 · source-R9
 */
class FeedGateTests {

	private static final Instant AT = Instant.parse("2026-10-28T09:00:00Z");

	private static final URI ONE = URI.create("https://synthetic.example/rss/one.xml");

	private static final CollectionProfile PROFILE = RegistryFixtures.profile(RegistryFixtures.REVIEWED_TEXT);

	private static final ProfileResolution RESOLVED = new ProfileResolution.Resolved(PROFILE);

	@Test
	void refusesWithoutRequestingWhenTheProfileIsUnavailable() {
		assertThat(FeedGate.precheck(RegistryFixtures.feed("one", false),
				new ProfileResolution.PresetMissing(new PresetKey("gone"))))
			.isEqualTo(new FeedGate.Precheck.Decided(new FeedActivation.Refused(ActivationRefusal.PROFILE_UNAVAILABLE)));
	}

	@Test
	void refusesAFeedMissingFromTheProfileEvenIfEnabled() {
		FeedGate.Precheck expected = new FeedGate.Precheck.Decided(
				new FeedActivation.Refused(ActivationRefusal.FEED_NOT_IN_PROFILE));

		assertThat(FeedGate.precheck(RegistryFixtures.feed("three", false), RESOLVED)).isEqualTo(expected);
		assertThat(FeedGate.precheck(RegistryFixtures.feed("three", true), RESOLVED)).isEqualTo(expected);
	}

	@Test
	void activatesAnEnabledFeedWithoutRequesting() {
		assertThat(FeedGate.precheck(RegistryFixtures.feed("one", true), RESOLVED))
			.isEqualTo(new FeedGate.Precheck.Decided(new FeedActivation.Activated(null)));
	}

	@Test
	void fetchesADisabledFeedFromItsProfileUrl() {
		FeedGate.Precheck precheck = FeedGate.precheck(RegistryFixtures.feed("one", false), RESOLVED);

		assertThat(precheck).isInstanceOf(FeedGate.Precheck.Fetch.class);
		FeedGate.Precheck.Fetch fetch = (FeedGate.Precheck.Fetch) precheck;
		assertThat(fetch.source()).isEqualTo(new SourceId(7));
		assertThat(fetch.descriptor().url()).isEqualTo(ONE);
		assertThat(fetch.descriptor().profile()).isSameAs(PROFILE);
	}

	@Test
	void carriesAFetchFailure() {
		FetchFailure failure = new FetchFailure(FetchFailureReason.NOT_FOUND, "없음 (HTTP 404)", 404, null);

		assertThat(FeedGate.preview(new FetchResult.Failed(failure, AT), PROFILE))
			.isEqualTo(new PreviewOutcome.Unfetched(failure));
	}

	@Test
	void treatsANotModifiedAnswerAsAnUnexpectedStatus() {
		PreviewOutcome outcome = FeedGate.preview(new FetchResult.NotModified(FetchState.NONE, AT, null), PROFILE);

		assertThat(outcome).isInstanceOf(PreviewOutcome.Unfetched.class);
		FetchFailure failure = ((PreviewOutcome.Unfetched) outcome).failure();
		assertThat(failure.reason()).isEqualTo(FetchFailureReason.UNEXPECTED_STATUS);
		assertThat(failure.httpStatus()).isEqualTo(304);
	}

	@Test
	void passesOrRejectsAFetchedFeedByTheCriteria() {
		assertThat(FeedGate.preview(fetched(true), PROFILE)).isInstanceOf(PreviewOutcome.Passed.class);
		assertThat(FeedGate.preview(fetched(false), PROFILE)).isInstanceOfSatisfying(
				PreviewOutcome.BelowCriteria.class, below -> assertThat(below.shortfalls())
					.containsExactly(new Shortfall(Criterion.DATED_ARTICLES, 0.0, 0.9)));
	}

	@Test
	void enablesAPassedFeedWhoseUrlIsUnchanged() {
		Feed feed = RegistryFixtures.feed("one", false);
		PreviewOutcome.Passed passed = (PreviewOutcome.Passed) FeedGate.preview(fetched(true), PROFILE);

		assertThat(FeedGate.enable(feed, RESOLVED, ONE, passed))
			.isEqualTo(new FeedActivation.Activated(passed.preview()));
		assertThat(feed.enabled()).isTrue();
	}

	@Test
	void keepsTheFeedOffWhenItsUrlChangedAfterThePreview() {
		Feed feed = RegistryFixtures.feed("one", false);
		URI old = URI.create("https://synthetic.example/rss/old.xml");

		assertThat(FeedGate.enable(feed, RESOLVED, old, FeedGate.preview(fetched(true), PROFILE)))
			.isEqualTo(new FeedActivation.TargetChanged(old, ONE));
		assertThat(feed.enabled()).isFalse();
	}

	@Test
	void keepsTheFeedOffWhenTheProfileNoLongerHasIt() {
		Feed feed = RegistryFixtures.feed("one", false);
		ProfileResolution withoutOne = new ProfileResolution.Resolved(RegistryFixtures.profile("""
				source:
				  name: 합성일보
				  homepage: https://synthetic.example
				  language: ko
				feeds:
				  - key: two
				    url: https://synthetic.example/rss/two.xml
				    section: 둘
				"""));

		assertThat(FeedGate.enable(feed, withoutOne, ONE, FeedGate.preview(fetched(true), PROFILE)))
			.isEqualTo(new FeedActivation.Refused(ActivationRefusal.FEED_NOT_IN_PROFILE));
		assertThat(feed.enabled()).isFalse();
	}

	@Test
	void keepsTheFeedOffWhenThePreviewFailed() {
		Feed feed = RegistryFixtures.feed("one", false);
		PreviewOutcome.BelowCriteria below = (PreviewOutcome.BelowCriteria) FeedGate.preview(fetched(false), PROFILE);
		FetchFailure failure = new FetchFailure(FetchFailureReason.NOT_FOUND, "없음 (HTTP 404)", 404, null);

		assertThat(FeedGate.enable(feed, RESOLVED, ONE, below))
			.isEqualTo(new FeedActivation.BelowCriteria(below.preview(), below.shortfalls()));
		assertThat(FeedGate.enable(feed, RESOLVED, ONE, new PreviewOutcome.Unfetched(failure)))
			.isEqualTo(new FeedActivation.Unfetched(failure));
		assertThat(feed.enabled()).isFalse();
	}

	/** 기사 하나 — dated 면 발행 시각을 해석했고, 아니면 추정이다 */
	private static FetchResult.Fetched fetched(boolean dated) {
		FetchedArticle article = new FetchedArticle("제목", URI.create("https://synthetic.example/news/1"), null, null,
				AT, !dated, null, List.of(), null);
		return new FetchResult.Fetched(new FeedNormalization.Normalized(List.of(article), List.of(), 0, 1_000),
				FetchState.NONE, AT, null);
	}

}
