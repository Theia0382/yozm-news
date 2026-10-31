package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import net.yozmnews.source.Criterion;
import net.yozmnews.source.FetchedArticle;
import net.yozmnews.source.domain.feed.FeedFixtures;
import net.yozmnews.source.domain.feed.FeedNormalization;
import net.yozmnews.source.domain.gate.PreviewOutcome;
import net.yozmnews.source.domain.gate.Shortfall;
import net.yozmnews.source.domain.profile.CollectionProfile;
import org.junit.jupiter.api.Test;

/**
 * 경향신문 프리셋 — 섹션 피드 구조의 합성 피드를 저장소의 프리셋으로 읽는다. 발행 시각과 기자가 표준 밖 요소(dc:date · dc:creator)에 있다.
 * <p>
 * 지키는 규칙: source-R9 · source-R12 · source-R39 · source-R41
 */
class KyunghyangPresetTests {

	@Test
	void readsThePublishedAtAndAuthorFromDublinCore() {
		FeedNormalization.Normalized normalized = PresetFixtures.normalized("kyunghyang", "section");

		assertThat(normalized.skipped()).isEmpty();
		assertThat(normalized.articles()).extracting(FetchedArticle::publishedAt, FetchedArticle::publishedAtEstimated,
				FetchedArticle::author)
			.containsExactly(tuple(Instant.parse("2026-10-30T06:32:00Z"), false, "합성기자 기자"),
					tuple(Instant.parse("2026-10-30T09:33:00Z"), false, null));
	}

	@Test
	void keepsTheTrackingQueryOfTheLink() {
		FetchedArticle first = PresetFixtures.normalized("kyunghyang", "section").articles().get(0);

		// 정규 URL 은 article 이 만든다 — source 는 추적 질의를 지우지 않는다
		assertThat(first.link()).isEqualTo(URI.create(
				"https://www.khan.example/article/202610301532001?utm_source=khan_rss&utm_medium=rss&utm_campaign=total_news"));
		assertThat(first.guid()).isEqualTo("https://www.khan.example/article/202610301532001");
		assertThat(first.summary()).isEqualTo("합성 본문 앞 이백 자 남짓이 요약문으로 실리고 ···");
		assertThat(first.categories()).isEqualTo(List.of("정치일반"));
	}

	@Test
	void passesTheActivationGateOnlyWithThePresetMapping() {
		assertThat(PresetFixtures.preview("kyunghyang", "section")).isInstanceOf(PreviewOutcome.Passed.class);
		// 대조 — 같은 문서 · 같은 받은 시각을 매핑 없는 프로필로 읽으면 날짜 기준에 못 미친다
		CollectionProfile unmapped = FeedFixtures.profile("");
		PreviewOutcome outcome = PresetFixtures
			.preview(PresetFixtures.normalized(PresetFixtures.bytes("kyunghyang", "section"), unmapped), unmapped);
		assertThat(outcome).isInstanceOfSatisfying(PreviewOutcome.BelowCriteria.class,
				below -> assertThat(below.shortfalls()).extracting(Shortfall::criterion)
					.containsExactly(Criterion.DATED_ARTICLES));
	}

}
