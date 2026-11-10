package net.yozmnews.storyline.domain.linking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

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
 * 스토리라인 표현 — 기사 수 가중 평균 중심, 수 · 시각 범위 · 핵심 말 합, 대표 사건(기사 수가 가장 많은 것, 같으면 ID 가 작은 것), 한 사건을
 * 뺀 표현, 상위 핵심 말 순서. 기대값은 손으로 셈한 리터럴이다.
 * <p>
 * 지키는 규칙: storyline-R7 · storyline-R9
 */
class RepresentationTests {

	private static final EventProfile A = profile(1, "m", new float[] { 1, 0 }, 3, "2026-11-01T00:00:00Z",
			"2026-11-02T00:00:00Z", List.of(new EventKeyTerm("금리", 2)));

	private static final EventProfile B = profile(2, "m", new float[] { 0, 1 }, 1, "2026-11-03T00:00:00Z",
			"2026-11-04T00:00:00Z", List.of(new EventKeyTerm("금리", 1), new EventKeyTerm("물가", 1)));

	@Test
	void membersMakeAWeightedCentroidAndSummedTerms() {
		Representation representation = Representation.of(List.of(A, B));

		// ([1,0] × 3 + [0,1] × 1) / 4 = [0.75, 0.25]
		assertThat(representation.centroid()).containsExactly(0.75f, 0.25f);
		assertThat(representation.articleCount()).isEqualTo(4);
		assertThat(representation.firstArticleAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
		assertThat(representation.lastArticleAt()).isEqualTo(Instant.parse("2026-11-04T00:00:00Z"));
		assertThat(representation.keyTerms()).containsExactlyInAnyOrderEntriesOf(Map.of("금리", 3, "물가", 1));
		assertThat(representation.modelVersion()).isEqualTo("m");
		assertThat(representation.representativeEventId()).isEqualTo(1);
	}

	@Test
	void anEqualCountGoesToTheSmallerId() {
		EventProfile c = profile(9, "m", new float[] { 1, 0 }, 3, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				List.of());
		EventProfile d = profile(5, "m", new float[] { 0, 1 }, 3, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				List.of());

		assertThat(Representation.of(List.of(c, d)).representativeEventId()).isEqualTo(5);
	}

	@Test
	void theMemberWithTheMostArticlesRepresentsEvenWithALargerId() {
		EventProfile most = profile(8, "m", new float[] { 0, 1 }, 4, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				List.of());

		// 기사 수 3 인 사건 1 보다 기사 수 4 인 사건 8 — 앞에 있지도, ID 가 작지도 않다
		assertThat(Representation.of(List.of(A, most)).representativeEventId()).isEqualTo(8);
	}

	@Test
	void withoutOneMemberLeavesTheRest() {
		assertThat(Representation.without(List.of(A, B), new EventId(2))).hasValueSatisfying(rest -> {
			assertThat(rest.centroid()).containsExactly(1f, 0f);
			assertThat(rest.articleCount()).isEqualTo(3);
			assertThat(rest.keyTerms()).containsExactlyEntriesOf(Map.of("금리", 2));
		});
		assertThat(Representation.without(List.of(A), new EventId(1))).isEmpty();
	}

	@Test
	void onlyTheTopTwentyTermsOfEachMemberAreSummed() {
		List<EventKeyTerm> many = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			many.add(new EventKeyTerm("말%02d".formatted(i), 1));
		}
		EventProfile wide = profile(3, "m", new float[] { 1, 0 }, 1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				many);

		assertThat(Representation.of(List.of(wide)).keyTerms()).hasSize(20).containsKey("말19").doesNotContainKey("말20");
	}

	@Test
	void topTermsAreByCountThenByteOrder() {
		EventProfile e = profile(4, "m", new float[] { 1, 0 }, 1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				List.of(new EventKeyTerm("예산", 2), new EventKeyTerm("여당", 2), new EventKeyTerm("국회", 3),
						new EventKeyTerm("야당", 1)));

		// 여 U+C5EC < 예 U+C608
		assertThat(Representation.of(List.of(e)).topTerms(3)).containsExactly("국회", "여당", "예산");
	}

	@Test
	void comparedToGivesTheCosineAndRangeOfTheRepresentation() {
		Subject subject = new Subject(new float[] { 4, 3 }, Instant.parse("2026-11-10T00:00:00Z"),
				Instant.parse("2026-11-10T00:00:00Z"), List.of("금리"));

		Compared compared = Representation.of(List.of(A)).comparedTo(77, subject);

		assertThat(compared.id()).isEqualTo(77);
		assertThat(compared.cosine()).isCloseTo(0.8, within(1e-6));
		assertThat(compared.firstArticleAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
		assertThat(compared.keyTerms()).containsExactly("금리");
	}

	@Test
	void mixedModelsOrLengthsAndNoMembersAreRejected() {
		EventProfile other = profile(5, "n", new float[] { 1, 0 }, 1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				List.of());
		EventProfile shorter = profile(6, "m", new float[] { 1 }, 1, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
				List.of());

		assertThatIllegalArgumentException().isThrownBy(() -> Representation.of(List.of(A, other)));
		assertThatIllegalArgumentException().isThrownBy(() -> Representation.of(List.of(A, shorter)));
		assertThatIllegalArgumentException().isThrownBy(() -> Representation.of(List.of()));
	}

	private static EventProfile profile(long id, String model, float[] centroid, int count, String first, String last,
			List<EventKeyTerm> terms) {
		return new EventProfile(new EventId(id), EventStatus.OPEN, model, centroid, count, Instant.parse(first),
				Instant.parse(last), terms);
	}

}
