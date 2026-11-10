package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.yozmnews.event.EventId;
import net.yozmnews.event.EventKeyTerm;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventStatus;
import org.junit.jupiter.api.Test;

/**
 * 잇기 점수 — 코사인 · 핵심 말 겹침 · 두 시각 범위 사이 간격의 감쇠, 창 밖은 견주지 않는다, 코사인 하한. 기대값은 손으로 셈한 리터럴이다.
 * <p>
 * 지키는 규칙: storyline-R8
 */
class LinkingScoringTests {

	private static final Duration WINDOW = Duration.ofDays(14);

	private static final Instant FIRST = Instant.parse("2026-11-05T00:00:00Z");

	private static final Instant LAST = Instant.parse("2026-11-06T00:00:00Z");

	/** 판정할 사건 — 핵심 말 둘(금리 · 물가) */
	private static final Subject SUBJECT = new Subject(new float[] { 1, 0 }, FIRST, LAST, List.of("금리", "물가"));

	@Test
	void anOverlappingRangeIsNotDecayed() {
		// 겹침 1/2 — 0.7 × 0.8 + 0.3 × 0.5 = 0.71
		Compared other = new Compared(7, 0.8, LAST, LAST.plus(Duration.ofDays(1)), List.of("금리", "환율"));

		assertThat(LinkingScoring.score(SUBJECT, other, WINDOW)).hasValueSatisfying(score -> {
			assertThat(score.score()).isCloseTo(0.71, within(1e-12));
			assertThat(score.cosine()).isEqualTo(0.8);
			assertThat(score.overlap()).isEqualTo(0.5);
		});
	}

	@Test
	void aSeparateRangeDecaysByTheGapBetweenTheRanges() {
		// 판정할 사건의 마지막 기사 14일(반감기) 뒤에 시작 — 0.71 × 2^(−1) = 0.355
		Instant later = LAST.plus(Duration.ofDays(14));
		Compared after = new Compared(7, 0.8, later, later, List.of("금리", "환율"));
		// 판정할 사건의 첫 기사 14일 앞에서 끝남 — 같은 감쇠
		Instant earlier = FIRST.minus(Duration.ofDays(14));
		Compared before = new Compared(8, 0.8, earlier.minus(Duration.ofDays(3)), earlier, List.of("금리", "환율"));

		assertThat(LinkingScoring.score(SUBJECT, after, WINDOW))
			.hasValueSatisfying(score -> assertThat(score.score()).isCloseTo(0.355, within(1e-12)));
		assertThat(LinkingScoring.score(SUBJECT, before, WINDOW))
			.hasValueSatisfying(score -> assertThat(score.score()).isCloseTo(0.355, within(1e-12)));
	}

	@Test
	void beyondTheWindowIsNotCompared() {
		Instant edge = LAST.plus(WINDOW);
		Compared atTheWindow = new Compared(7, 0.8, edge, edge, List.of());
		Compared beyond = new Compared(8, 0.8, edge.plusSeconds(1), edge.plusSeconds(1), List.of());

		assertThat(LinkingScoring.score(SUBJECT, atTheWindow, WINDOW)).isPresent();
		assertThat(LinkingScoring.score(SUBJECT, beyond, WINDOW)).isEmpty();
	}

	@Test
	void aNegativeCosineCountsAsZeroAndANanCosineGivesANanScore() {
		// 겹침 1 — 0.7 × 0 + 0.3 × 1 = 0.3
		assertThat(LinkingScoring.score(SUBJECT, new Compared(7, -0.2, FIRST, LAST, List.of("금리", "물가")), WINDOW))
			.hasValueSatisfying(score -> assertThat(score.score()).isCloseTo(0.3, within(1e-12)));
		assertThat(LinkingScoring.score(SUBJECT, new Compared(7, Double.NaN, FIRST, LAST, List.of()), WINDOW))
			.hasValueSatisfying(score -> {
				assertThat(score.score()).isNaN();
				assertThat(score.reaches(0.01)).isFalse();
			});
	}

	@Test
	void theOverlapIsOverTheSubjectsTerms() {
		// 판정할 사건의 말 둘 가운데 하나가 겹친다 — 1/2(견줄 것의 말 하나 기준이면 1)
		Compared other = new Compared(7, 0.8, FIRST, LAST, List.of("금리"));

		assertThat(LinkingScoring.score(SUBJECT, other, WINDOW))
			.hasValueSatisfying(score -> assertThat(score.overlap()).isEqualTo(0.5));
	}

	@Test
	void aSubjectTakesTheTopTwentyTermsOfTheEvent() {
		List<EventKeyTerm> many = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			many.add(new EventKeyTerm("말%02d".formatted(i), 21 - i));
		}
		EventProfile profile = new EventProfile(new EventId(1), EventStatus.OPEN, "m", new float[] { 1, 0 }, 21, FIRST,
				LAST, many);

		Subject subject = Subject.of(profile);

		assertThat(subject.keyTerms()).hasSize(20).startsWith("말00").endsWith("말19");
		assertThat(subject.centroid()).containsExactly(1f, 0f);
		assertThat(subject.firstArticleAt()).isEqualTo(FIRST);
	}

	@Test
	void noSubjectTermsMeansNoOverlap() {
		Subject bare = new Subject(new float[] { 1, 0 }, FIRST, LAST, List.of());

		assertThat(LinkingScoring.score(bare, new Compared(7, 1.0, FIRST, LAST, List.of("금리")), WINDOW))
			.hasValueSatisfying(score -> {
				assertThat(score.overlap()).isZero();
				assertThat(score.score()).isCloseTo(0.7, within(1e-12));
			});
	}

	@Test
	void theCosineFloorIsTheLeastCosineThatCanReachTheThreshold() {
		// (0.65 − 0.3) / 0.7 − 0.01 = 0.49
		assertThat(LinkingScoring.cosineFloor(0.65)).isCloseTo(0.49, within(1e-12));
		assertThat(LinkingScoring.cosineFloor(0.3)).isEqualTo(-1);
		assertThat(LinkingScoring.cosineFloor(0.1)).isEqualTo(-1);
	}

	@Test
	void theWindowIsAroundTheSubjectsRange() {
		assertThat(LinkingScoring.windowFrom(FIRST, WINDOW)).isEqualTo(Instant.parse("2026-10-22T00:00:00Z"));
		assertThat(LinkingScoring.windowTo(LAST, WINDOW)).isEqualTo(Instant.parse("2026-11-20T00:00:00Z"));
	}

}
