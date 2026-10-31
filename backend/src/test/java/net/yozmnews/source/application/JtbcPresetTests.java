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
 * JTBC 프리셋 — 섹션 피드 구조의 합성 피드를 저장소의 프리셋으로 읽는다.
 * <p>
 * 지키는 규칙: source-R9 · source-R12 · source-R40
 */
class JtbcPresetTests {

	@Test
	void readsEveryValueFromCdata() {
		FeedNormalization.Normalized normalized = PresetFixtures.normalized("jtbc", "section");

		assertThat(normalized.skipped()).isEmpty();
		assertThat(normalized.articles()).extracting(FetchedArticle::title, FetchedArticle::link, FetchedArticle::guid,
				FetchedArticle::publishedAt, FetchedArticle::publishedAtEstimated)
			.containsExactly(
					tuple("합성 장관 \"합성 인용문\"…합성 기사 하나", URI.create("https://news.jtbc.example/article/NB00000001"),
							"https://news.jtbc.example/article/NB00000001", Instant.parse("2026-10-30T12:20:59Z"), false),
					tuple("합성 기사 둘", URI.create("https://news.jtbc.example/article/NB00000002"),
							"https://news.jtbc.example/article/NB00000002", Instant.parse("2026-10-30T00:05:00Z"), false));
		FetchedArticle first = normalized.articles().get(0);
		// 줄바꿈이 든 채 잘린 요약문 — 공백 하나로 정리한다
		assertThat(first.summary()).isEqualTo("합성 장관은 오늘 \"합성 인용문\"이라고 말했다. 이 합성 기사는 둘째 문단에서 잘");
		assertThat(first.author()).isNull();
		assertThat(first.categories()).isEqualTo(List.of());
		assertThat(first.thumbnail()).isNull();
	}

	@Test
	void passesTheActivationGate() {
		assertThat(PresetFixtures.preview("jtbc", "section")).isInstanceOf(PreviewOutcome.Passed.class);
	}

}
