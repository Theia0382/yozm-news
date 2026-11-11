package net.yozmnews.event.domain.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 후보 점수 — 코사인 · 핵심 말 겹침 · 발행 시각 차의 감쇠, 창 밖은 후보가 아니다. 기대값은 손으로 셈한 리터럴이다.
 * <p>
 * 지키는 규칙: event-R10 · event-R13(구간 대 구간 — 병합)
 */
class CandidateScoringTests {

	private static final Duration WINDOW = Duration.ofHours(72);

	private static final Instant FIRST = Instant.parse("2026-11-05T00:00:00Z");

	private static final Instant LAST = Instant.parse("2026-11-05T06:00:00Z");

	private static final List<String> EVENT_TERMS = List.of("국회", "예산", "여당", "야당", "본회의");

	/** 기사 핵심 말 넷 가운데 둘(국회 · 예산)이 사건에 있다 — 겹침 2/4 */
	private static final List<String> ARTICLE_TERMS = List.of("국회", "예산", "통과", "정부");

	@Test
	void anOverlappingSpanIsNotDecayed() {
		// 구간 [04T00, 05T03] 이 사건 [05T00, 05T06] 과 겹친다 — 0.78
		assertThat(CandidateScoring.score(event(0.9), ARTICLE_TERMS, Instant.parse("2026-11-04T00:00:00Z"),
				Instant.parse("2026-11-05T03:00:00Z"), WINDOW))
			.hasValueSatisfying(candidate -> assertThat(candidate.score()).isCloseTo(0.78, within(1e-12)));
	}

	@Test
	void aSeparateSpanDecaysByTheGapBetweenTheSpans() {
		// 구간 [06T18, 07T06] 은 마지막 기사 36시간 뒤에 시작 — 0.78 × 2^(−36/36) = 0.39
		assertThat(CandidateScoring.score(event(0.9), ARTICLE_TERMS, Instant.parse("2026-11-06T18:00:00Z"),
				Instant.parse("2026-11-07T06:00:00Z"), WINDOW))
			.hasValueSatisfying(candidate -> assertThat(candidate.score()).isCloseTo(0.39, within(1e-12)));
		// 구간 [03T00, 04T06] 은 첫 기사 18시간 앞에서 끝난다 — 0.78 × 2^(−18/36)
		assertThat(CandidateScoring.score(event(0.9), ARTICLE_TERMS, Instant.parse("2026-11-03T00:00:00Z"),
				Instant.parse("2026-11-04T06:00:00Z"), WINDOW))
			.hasValueSatisfying(
					candidate -> assertThat(candidate.score()).isCloseTo(0.5515432893255071, within(1e-12)));
	}

	@Test
	void aSpanStartingAfterItsEndIsRejected() {
		org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
			.isThrownBy(() -> CandidateScoring.score(event(0.9), ARTICLE_TERMS, Instant.parse("2026-11-05T01:00:00Z"),
					Instant.parse("2026-11-05T00:00:00Z"), WINDOW));
	}

	@Test
	void anArticleInsideTheEventSpanIsNotDecayed() {
		// 0.7 × 0.9 + 0.3 × 0.5 = 0.63 + 0.15 = 0.78
		assertThat(score(0.9, ARTICLE_TERMS, Instant.parse("2026-11-05T03:00:00Z"))).isCloseTo(0.78, within(1e-12));
	}

	@Test
	void oneHalfLifeAfterTheLastArticleHalvesTheScore() {
		// 마지막 기사 36시간 뒤 — 0.78 × 2^(−36/36) = 0.39
		assertThat(score(0.9, ARTICLE_TERMS, Instant.parse("2026-11-06T18:00:00Z"))).isCloseTo(0.39, within(1e-12));
	}

	@Test
	void anArticleBeforeTheFirstArticleDecaysByTheGapToTheFirst() {
		// 첫 기사 18시간 앞 — 0.78 × 2^(−18/36) = 0.78 × 0.7071067811865476 = 0.5515432893255071
		assertThat(score(0.9, ARTICLE_TERMS, Instant.parse("2026-11-04T06:00:00Z"))).isCloseTo(0.5515432893255071,
				within(1e-12));
	}

	@Test
	void theWindowBoundaryIsInsideAndOneSecondPastIsNoCandidate() {
		EventSnapshot event = event(0.9);
		// 마지막 기사 + 72시간 — 0.78 × 2^(−72/36) = 0.195
		assertThat(CandidateScoring.score(event, ARTICLE_TERMS, Instant.parse("2026-11-08T06:00:00Z"), WINDOW))
			.hasValueSatisfying(candidate -> assertThat(candidate.score()).isCloseTo(0.195, within(1e-12)));
		assertThat(CandidateScoring.score(event, ARTICLE_TERMS, Instant.parse("2026-11-08T06:00:01Z"), WINDOW))
			.isEmpty();
		assertThat(CandidateScoring.score(event, ARTICLE_TERMS, Instant.parse("2026-11-02T00:00:00Z"), WINDOW))
			.isPresent();
		assertThat(CandidateScoring.score(event, ARTICLE_TERMS, Instant.parse("2026-11-01T23:59:59Z"), WINDOW))
			.isEmpty();
	}

	@Test
	void anArticleWithoutKeyTermsScoresOnSimilarityAlone() {
		// 0.7 × 0.9 = 0.63
		assertThat(score(0.9, List.of(), Instant.parse("2026-11-05T03:00:00Z"))).isCloseTo(0.63, within(1e-12));
	}

	@Test
	void aNegativeCosineCountsAsZero() {
		// 0.7 × 0 + 0.3 × 0.5 = 0.15
		assertThat(score(-0.4, ARTICLE_TERMS, Instant.parse("2026-11-05T03:00:00Z"))).isCloseTo(0.15, within(1e-12));
	}

	@Test
	void repeatedArticleTermsCountOnce() {
		// 서로 다른 말 셋(국회 · 통과 · 정부) 가운데 하나 — 0.7 × 0.9 + 0.3 × 1/3 = 0.63 + 0.1 = 0.73
		assertThat(score(0.9, List.of("국회", "국회", "통과", "정부"), Instant.parse("2026-11-05T03:00:00Z")))
			.isCloseTo(0.73, within(1e-12));
	}

	@Test
	void theCandidateCarriesTheEventId() {
		assertThat(CandidateScoring.score(event(0.9), ARTICLE_TERMS, LAST, WINDOW))
			.hasValueSatisfying(candidate -> assertThat(candidate.eventId()).isEqualTo(42));
	}

	private static double score(double cosine, List<String> articleTerms, Instant publishedAt) {
		return CandidateScoring.score(event(cosine), articleTerms, publishedAt, WINDOW).orElseThrow().score();
	}

	private static EventSnapshot event(double cosine) {
		return new EventSnapshot(42, cosine, EVENT_TERMS, FIRST, LAST, 3);
	}

}
