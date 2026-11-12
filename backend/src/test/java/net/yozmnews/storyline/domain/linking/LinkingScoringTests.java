package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.event.EventKeyTerm;
import net.yozmnews.event.EventProfile;
import net.yozmnews.event.EventStatus;
import org.junit.jupiter.api.Test;

/**
 * 잇기 점수 — 코사인과 말의 무게를 준 핵심 말 겹침, 창 안의 시각 차는 점수를 바꾸지 않고 창 밖은 견주지 않는다, 코사인 하한. 기대값은 손으로
 * 셈한 리터럴이다.
 * <p>
 * 지키는 규칙: storyline-R8
 */
class LinkingScoringTests {

	private static final Duration WINDOW = Duration.ofDays(14);

	private static final Instant FIRST = Instant.parse("2026-11-05T00:00:00Z");

	private static final Instant LAST = Instant.parse("2026-11-06T00:00:00Z");

	/** 판정할 사건 — 핵심 말 둘(가 · 나) */
	private static final Subject SUBJECT = new Subject(new float[] { 1, 0 }, FIRST, LAST, List.of("가", "나"));

	/** 사건 99건 가운데 "가"는 9건에, "나"는 어느 사건에도 없다 — 무게 ln(100 ÷ 10) = 2.302585 · ln(100 ÷ 1) = 4.605170 */
	private static final TermWeights WEIGHTS = TermWeights.of(99, Map.of("가", 9L, "나", 0L));

	/** 두 말이 같은 수의 사건에 있다 — 무게가 같다 */
	private static final TermWeights EVEN = TermWeights.of(99, Map.of("가", 9L, "나", 9L));

	@Test
	void theOverlapWeighsEachSharedTermByHowRareItIs() {
		// 흔한 쪽 "가"만 겹친다 — 겹침 2.302585 ÷ (2.302585 + 4.605170) = 0.333333, 점수 0.89 × 0.9 + 0.11 × 0.333333 = 0.837667
		Compared common = new Compared(7, 0.9, FIRST, LAST, List.of("가"));
		// 드문 쪽 "나"만 겹친다 — 겹침 4.605170 ÷ 6.907755 = 0.666667, 점수 0.801 + 0.11 × 0.666667 = 0.874333
		Compared rare = new Compared(8, 0.9, FIRST, LAST, List.of("나", "다"));

		assertThat(LinkingScoring.score(SUBJECT, common, WINDOW, WEIGHTS)).hasValueSatisfying(score -> {
			assertThat(score.score()).isCloseTo(0.837667, within(1e-6));
			assertThat(score.cosine()).isEqualTo(0.9);
			assertThat(score.overlap()).isCloseTo(0.333333, within(1e-6));
		});
		assertThat(LinkingScoring.score(SUBJECT, rare, WINDOW, WEIGHTS)).hasValueSatisfying(score -> {
			assertThat(score.score()).isCloseTo(0.874333, within(1e-6));
			assertThat(score.overlap()).isCloseTo(0.666667, within(1e-6));
		});
	}

	@Test
	void withEvenWeightsTheOverlapIsTheShareOfTheSubjectsTerms() {
		// 판정할 사건의 말 둘 가운데 하나가 겹친다 — 1/2(견줄 것의 말 하나 기준이면 1). 0.89 × 0.9 + 0.11 × 0.5 = 0.856
		Compared other = new Compared(7, 0.9, FIRST, LAST, List.of("가"));

		assertThat(LinkingScoring.score(SUBJECT, other, WINDOW, EVEN)).hasValueSatisfying(score -> {
			assertThat(score.overlap()).isCloseTo(0.5, within(1e-12));
			assertThat(score.score()).isCloseTo(0.856, within(1e-12));
		});
	}

	@Test
	void aGapWithinTheWindowDoesNotChangeTheScore() {
		// 판정할 사건의 마지막 기사 7일 뒤에 시작 — 범위가 겹칠 때와 같은 0.837667
		Instant later = LAST.plus(Duration.ofDays(7));
		Compared after = new Compared(7, 0.9, later, later, List.of("가"));
		// 판정할 사건의 첫 기사 7일 앞에서 끝남
		Instant earlier = FIRST.minus(Duration.ofDays(7));
		Compared before = new Compared(8, 0.9, earlier.minus(Duration.ofDays(3)), earlier, List.of("가"));

		assertThat(LinkingScoring.score(SUBJECT, after, WINDOW, WEIGHTS))
			.hasValueSatisfying(score -> assertThat(score.score()).isCloseTo(0.837667, within(1e-6)));
		assertThat(LinkingScoring.score(SUBJECT, before, WINDOW, WEIGHTS))
			.hasValueSatisfying(score -> assertThat(score.score()).isCloseTo(0.837667, within(1e-6)));
	}

	@Test
	void beyondTheWindowIsNotCompared() {
		Instant edge = LAST.plus(WINDOW);
		Compared atTheWindow = new Compared(7, 0.8, edge, edge, List.of());
		Compared beyond = new Compared(8, 0.8, edge.plusSeconds(1), edge.plusSeconds(1), List.of());

		// 창과 같으면 견준다 — 0.89 × 0.8 = 0.712
		assertThat(LinkingScoring.score(SUBJECT, atTheWindow, WINDOW, WEIGHTS))
			.hasValueSatisfying(score -> assertThat(score.score()).isCloseTo(0.712, within(1e-12)));
		assertThat(LinkingScoring.score(SUBJECT, beyond, WINDOW, WEIGHTS)).isEmpty();
	}

	@Test
	void aNegativeCosineCountsAsZeroAndANanCosineGivesANanScore() {
		// 겹침 1 — 0.89 × 0 + 0.11 × 1 = 0.11
		assertThat(
				LinkingScoring.score(SUBJECT, new Compared(7, -0.2, FIRST, LAST, List.of("가", "나")), WINDOW, WEIGHTS))
			.hasValueSatisfying(score -> assertThat(score.score()).isCloseTo(0.11, within(1e-12)));
		assertThat(LinkingScoring.score(SUBJECT, new Compared(7, Double.NaN, FIRST, LAST, List.of()), WINDOW, WEIGHTS))
			.hasValueSatisfying(score -> {
				assertThat(score.score()).isNaN();
				assertThat(score.reaches(0.01)).isFalse();
			});
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
	void noSubjectTermsOrOnlyWeightlessTermsMeanNoOverlap() {
		Subject bare = new Subject(new float[] { 1, 0 }, FIRST, LAST, List.of());
		// 판정할 사건의 말이 모든 사건에 있다 — 무게가 모두 0 이라 겹쳐도 겹침은 0
		TermWeights weightless = TermWeights.of(9, Map.of("가", 9L, "나", 9L));

		// 코사인만 남는다 — 0.89 × 1.0 = 0.89
		assertThat(LinkingScoring.score(bare, new Compared(7, 1.0, FIRST, LAST, List.of("가")), WINDOW, WEIGHTS))
			.hasValueSatisfying(score -> {
				assertThat(score.overlap()).isZero();
				assertThat(score.score()).isCloseTo(0.89, within(1e-12));
			});
		assertThat(LinkingScoring.score(SUBJECT, new Compared(7, 1.0, FIRST, LAST, List.of("가", "나")), WINDOW, weightless))
			.hasValueSatisfying(score -> {
				assertThat(score.overlap()).isZero();
				assertThat(score.score()).isCloseTo(0.89, within(1e-12));
			});
	}

	@Test
	void theCosineFloorIsTheLeastCosineThatCanReachTheThreshold() {
		// (0.85 − 0.11) ÷ 0.89 − 0.01 = 0.821461
		assertThat(LinkingScoring.cosineFloor(0.85)).isCloseTo(0.821461, within(1e-6));
		assertThat(LinkingScoring.cosineFloor(0.11)).isEqualTo(-1);
		assertThat(LinkingScoring.cosineFloor(0.05)).isEqualTo(-1);
	}

	@Test
	void theWindowIsAroundTheSubjectsRange() {
		assertThat(LinkingScoring.windowFrom(FIRST, WINDOW)).isEqualTo(Instant.parse("2026-10-22T00:00:00Z"));
		assertThat(LinkingScoring.windowTo(LAST, WINDOW)).isEqualTo(Instant.parse("2026-11-20T00:00:00Z"));
	}

}
