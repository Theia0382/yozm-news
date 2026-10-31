package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.time.Instant;

import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.domain.feed.FeedNormalization;
import net.yozmnews.source.domain.gate.PreviewOutcome;
import org.junit.jupiter.api.Test;

/**
 * 동아일보 프리셋 — 섹션 피드 구조의 합성 피드를 저장소의 프리셋으로 읽는다.
 * <p>
 * 지키는 규칙: source-R9 · source-R12 · source-R40
 */
class DongaPresetTests {

	@Test
	void readsItemsWithoutGuidAndKeepsTheLinkWithASpace() {
		FeedNormalization.Normalized normalized = PresetFixtures.normalized("donga", "section");

		assertThat(normalized.skipped()).isEmpty();
		assertThat(normalized.articles()).extracting(FetchedArticle::link, FetchedArticle::guid,
				FetchedArticle::publishedAt)
			.containsExactly(
					tuple(URI.create("https://www.donga.example/news/Politics/article/all/20261030/100000001/1"), null,
							Instant.parse("2026-10-30T05:30:00Z")),
					tuple(URI.create(
							"https://www.donga.example/news/K-TECH%20Global%20Leaders/article/all/20261030/100000002/1"),
							null, Instant.parse("2026-10-30T03:00:00Z")),
					tuple(URI.create("https://www.donga.example/news/Economy/article/all/20261030/100000003/1"), null,
							Instant.parse("2026-10-30T01:15:00Z")));
	}

	@Test
	void dropsTheImageFromTheSummary() {
		FeedNormalization.Normalized normalized = PresetFixtures.normalized("donga", "section");

		assertThat(normalized.articles()).extracting(FetchedArticle::summary)
			.containsExactly("합성 본문의 앞부분이 요약문으로 실리고 여기서 잘린다", "합성 연재의 요약문", null);
		// media:content 는 표준 썸네일 요소가 아니라 읽지 않는다
		assertThat(normalized.articles()).extracting(FetchedArticle::thumbnail).containsOnlyNulls();
	}

	@Test
	void passesTheActivationGate() {
		assertThat(PresetFixtures.preview("donga", "section")).isInstanceOf(PreviewOutcome.Passed.class);
	}

}
