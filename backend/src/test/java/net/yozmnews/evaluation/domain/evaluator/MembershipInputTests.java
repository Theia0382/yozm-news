package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 소속 판정의 포트 입력 · 결과 — 제목 · 요약 · 후보, 확률 · 이름 · 토큰의 값 규칙.
 * <p>
 * 지키는 규칙: evaluation-R37
 */
class MembershipInputTests {

	private static final MembershipWording WORDING = new MembershipWording("w-test", "같은 사건인가");

	private static final MembershipCandidate TWELVE = new MembershipCandidate(12, List.of("서울 시장 선거 개표"));

	private static final MembershipCandidate FIFTEEN = new MembershipCandidate(15, List.of("부산 항만 파업"));

	@Test
	void keepsTheCandidatesInTheGivenOrder() {
		MembershipInput input = new MembershipInput("서울 시장 선거 결과", null, List.of(FIFTEEN, TWELVE), WORDING);

		assertThat(input.candidates()).containsExactly(FIFTEEN, TWELVE);
		assertThat(input.summary()).isNull();
	}

	@Test
	void refusesABlankTitleABlankSummaryNoCandidatesOrARepeatedCandidate() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipInput(" ", null, List.of(TWELVE), WORDING));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipInput("제목", " ", List.of(TWELVE), WORDING));
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipInput("제목", null, List.of(), WORDING))
			.withMessage("후보가 없다");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipInput("제목", null,
					List.of(TWELVE, new MembershipCandidate(12, List.of("다른 제목"))), WORDING))
			.withMessage("후보 ID 가 겹친다: 12");
	}

	@Test
	void acceptsTenCandidatesAndRefusesEleven() {
		assertThat(new MembershipInput("제목", null, candidates(10), WORDING).candidates()).hasSize(10);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipInput("제목", null, candidates(11), WORDING))
			.withMessage("후보는 10개까지다: 11");
	}

	@Test
	void refusesProbabilitiesOutsideZeroToOneAndBlankNames() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Membership(Map.of(12L, 1.2), "jev", "jev-1.14.0", 0, "{}"))
			.withMessage("확률은 0 ~ 1 의 유한값이다 — Membership.probabilities: 1.2");
		assertThat(new Membership(Map.of(12L, 0.0, 15L, 1.0), "jev", "jev-1.14.0", 0, "{}").probabilities())
			.containsEntry(12L, 0.0)
			.containsEntry(15L, 1.0);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Membership(Map.of(12L, Double.NaN), "jev", "jev-1.14.0", 0, "{}"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Membership(Map.of(12L, 0.5), " ", "jev-1.14.0", 0, "{}"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Membership(Map.of(12L, 0.5), "jev", "jev-1.14.0", -1, "{}"));
		assertThat(new Membership(Map.of(), "jev", "jev-1.14.0", 0, "{}").probabilities()).isEmpty();
	}

	private static List<MembershipCandidate> candidates(int count) {
		List<MembershipCandidate> candidates = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			candidates.add(new MembershipCandidate(12 + i, List.of("제목 " + i)));
		}
		return candidates;
	}

}
