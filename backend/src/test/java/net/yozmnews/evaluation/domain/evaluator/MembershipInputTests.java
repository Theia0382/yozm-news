package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 사건 소속 판정의 포트 입력 · 결과 — 제목 · 요약 · 후보, 확률 · 이름 · 토큰의 값 규칙.
 * <p>
 * 지키는 규칙: evaluation-R37
 */
class MembershipInputTests {

	private static final MembershipWording WORDING = new MembershipWording("w-test", "같은 사건인가");

	private static final CandidateEvent TWELVE = new CandidateEvent(12, List.of("서울 시장 선거 개표"));

	private static final CandidateEvent FIFTEEN = new CandidateEvent(15, List.of("부산 항만 파업"));

	@Test
	void keepsTheCandidatesInTheGivenOrder() {
		MembershipInput input = new MembershipInput("서울 시장 선거 결과", null, List.of(FIFTEEN, TWELVE), WORDING);

		assertThat(input.candidates()).containsExactly(FIFTEEN, TWELVE);
		assertThat(input.summary()).isNull();
	}

	@Test
	void refusesABlankTitleABlankSummaryNoCandidatesOrARepeatedEvent() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipInput(" ", null, List.of(TWELVE), WORDING));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipInput("제목", " ", List.of(TWELVE), WORDING));
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipInput("제목", null, List.of(), WORDING));
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipInput("제목", null,
				List.of(TWELVE, new CandidateEvent(12, List.of("다른 제목"))), WORDING));
	}

	@Test
	void refusesProbabilitiesOutsideZeroToOneAndBlankNames() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Membership(Map.of(12L, 1.2), "jev", "jev-1.14.0", 0, "{}"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Membership(Map.of(12L, Double.NaN), "jev", "jev-1.14.0", 0, "{}"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Membership(Map.of(12L, 0.5), " ", "jev-1.14.0", 0, "{}"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new Membership(Map.of(12L, 0.5), "jev", "jev-1.14.0", -1, "{}"));
		assertThat(new Membership(Map.of(), "jev", "jev-1.14.0", 0, "{}").probabilities()).isEmpty();
	}

}
