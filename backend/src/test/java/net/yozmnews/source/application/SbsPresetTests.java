package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.domain.feed.FeedNormalization;
import net.yozmnews.source.domain.gate.PreviewOutcome;
import org.junit.jupiter.api.Test;

/**
 * SBS 프리셋 — 섹션 피드 구조의 합성 피드를 저장소의 프리셋으로 읽는다.
 * <p>
 * 지키는 규칙: source-R9 · source-R12 · source-R40 · source-R41
 */
class SbsPresetTests {

	@Test
	void readsTheShortDescriptionAsTheSummaryAndNotTheBody() {
		FeedNormalization.Normalized normalized = PresetFixtures.normalized("sbs", "section");

		// CDATA 안의 HTML 이름 참조 &sim; 은 정리 때 U+223C 로 풀린다
		assertThat(normalized.articles()).extracting(FetchedArticle::summary)
			.containsExactly("합성 리드 한 문장이 요약문이다. 10∼20일 이어지는 …", "합성 요약문 둘");
		assertThat(normalized.articles()).extracting(FetchedArticle::summary)
			.noneMatch(summary -> summary.contains("합성 본문"));
	}

	@Test
	void readsTheMetadata() {
		FeedNormalization.Normalized normalized = PresetFixtures.normalized("sbs", "section");

		assertThat(normalized.skipped()).isEmpty();
		assertThat(normalized.articles()).extracting(FetchedArticle::link, FetchedArticle::guid,
				FetchedArticle::publishedAt, FetchedArticle::author)
			.containsExactly(
					tuple(URI.create(
							"https://news.sbs.example/news/endPage.do?news_id=N1000000001&plink=RSSREADER&cooper=RSSREADER"),
							"https://news.sbs.example/news/endPage.do?news_id=N1000000001",
							Instant.parse("2026-10-30T06:49:00Z"), "reporter@sbs.example (합성기자)"),
					tuple(URI.create(
							"https://news.sbs.example/news/endPage.do?news_id=N1000000002&plink=RSSREADER&cooper=RSSREADER"),
							"https://news.sbs.example/news/endPage.do?news_id=N1000000002",
							Instant.parse("2026-10-30T02:00:00Z"), "news@sbs.example(합성)"));
		FetchedArticle first = normalized.articles().get(0);
		assertThat(first.categories()).isEqualTo(List.of("정치", "POLITICS", "합성 뉴스", "일반기사", "합성"));
		assertThat(first.thumbnail())
			.isEqualTo(URI.create("https://img.sbs.example/newimg/news/20261030/100000001_1280.jpg"));
	}

	@Test
	void passesTheActivationGate() {
		assertThat(PresetFixtures.preview("sbs", "section")).isInstanceOf(PreviewOutcome.Passed.class);
	}

}
