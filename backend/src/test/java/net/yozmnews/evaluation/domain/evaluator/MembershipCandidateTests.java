package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 소속 판정의 후보 — 후보 ID · 서술 제목의 상한과 그 상한을 적용하는 팩터리, 후보 목록의 수 · 겹침 검사.
 * <p>
 * 지키는 규칙: evaluation-R37
 */
class MembershipCandidateTests {

	/** 한자 확장 B 의 첫 글자 — 코드 포인트 하나가 UTF-16 두 단위(서로게이트 쌍)다 */
	private static final String SUPPLEMENTARY = "𠀀";

	@Test
	void keepsTheSurrogatePairThatEndsTheFirstTwoHundredCodePoints() {
		String title = "a".repeat(199) + SUPPLEMENTARY + "b";

		assertThat(MembershipCandidate.limited(12, List.of(title)).orElseThrow().titles())
			.containsExactly("a".repeat(199) + SUPPLEMENTARY);
	}

	@Test
	void cutsBeforeASurrogatePairThatStartsAfterTwoHundredCodePoints() {
		String title = "a".repeat(200) + SUPPLEMENTARY;

		assertThat(MembershipCandidate.limited(12, List.of(title)).orElseThrow().titles()).containsExactly("a".repeat(200));
	}

	@Test
	void keepsATitleWithinTheLimitAsItIs() {
		assertThat(MembershipCandidate.limited(12, List.of("서울 시장 선거 개표", "a".repeat(200))).orElseThrow().titles())
			.containsExactly("서울 시장 선거 개표", "a".repeat(200));
	}

	@Test
	void stripsTrailingSpaceAfterCuttingAndDropsBlankTitles() {
		String title = "a".repeat(198) + "  b";

		assertThat(MembershipCandidate.limited(12, List.of(" ", title, "부산 항만 파업")).orElseThrow().titles())
			.containsExactly("a".repeat(198), "부산 항만 파업");
	}

	@Test
	void isEmptyWhenNoTitleIsLeft() {
		assertThat(MembershipCandidate.limited(12, List.of(" ", "\t"))).isEmpty();
	}

	@Test
	void acceptsFiveTitlesAndRefusesSix() {
		assertThat(new MembershipCandidate(12, List.of("가", "나", "다", "라", "마")).titles()).hasSize(5);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipCandidate(12, List.of("가", "나", "다", "라", "마", "바")));
	}

	@Test
	void refusesACandidateIdBelowOneNoTitlesABlankTitleOrATitleOverTheLimit() {
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipCandidate(0, List.of("제목")))
			.withMessage("후보 ID 는 1 이상이다: 0");
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipCandidate(12, List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipCandidate(12, List.of(" ")));
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipCandidate(12, List.of("a".repeat(201))));
		assertThat(new MembershipCandidate(12, List.of("a".repeat(199) + SUPPLEMENTARY)).titles()).hasSize(1);
	}

	@Test
	void theCandidateListHasOneToTenCandidatesWithoutARepeatedId() {
		MembershipCandidate.requireCandidates(List.of(12L), Long::longValue);
		MembershipCandidate.requireCandidates(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), Long::longValue);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> MembershipCandidate.requireCandidates(List.<Long>of(), Long::longValue))
			.withMessage("후보가 없다");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> MembershipCandidate.requireCandidates(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L),
					Long::longValue))
			.withMessage("후보는 10개까지다: 11");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> MembershipCandidate.requireCandidates(List.of(12L, 15L, 12L), Long::longValue))
			.withMessage("후보 ID 가 겹친다: 12");
	}

}
