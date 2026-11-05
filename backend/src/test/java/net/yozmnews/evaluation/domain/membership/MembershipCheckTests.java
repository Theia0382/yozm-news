package net.yozmnews.evaluation.domain.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import net.yozmnews.evaluation.domain.evaluator.Membership;
import org.junit.jupiter.api.Test;

/**
 * 판정 결과와 물은 후보의 대조 — 사건 키 집합, 원본 응답의 {@code jsonb} 저장 가능성.
 * <p>
 * 지키는 규칙: evaluation-R38
 */
class MembershipCheckTests {

	@Test
	void acceptsAResultWithExactlyTheAskedEvents() {
		assertThat(MembershipCheck.mismatch(membership(Map.of(12L, 0.83, 15L, 0.07), "{\"ok\":true}"), Set.of(12L, 15L)))
			.isEmpty();
	}

	@Test
	void namesTheMissingAndUnknownEvents() {
		assertThat(MembershipCheck.mismatch(membership(Map.of(12L, 0.83, 99L, 0.5), "{}"), Set.of(12L, 15L)))
			.contains("사건 키가 물은 후보와 다르다 — 빠진 사건 [15], 모르는 사건 [99]");
	}

	@Test
	void refusesARawResponseThatJsonbDoesNotTake() {
		assertThat(MembershipCheck.mismatch(membership(Map.of(12L, 0.83), "{\"a\":\"\\u0000\"}"), Set.of(12L)))
			.contains("원본 응답에 문자 U+0000 이 있다");
		assertThat(MembershipCheck.mismatch(membership(Map.of(12L, 0.83), "<html>"), Set.of(12L)))
			.contains("원본 응답이 JSON 이 아니다");
	}

	private static Membership membership(Map<Long, Double> probabilities, String raw) {
		return new Membership(probabilities, "jev", "jev-1.14.0", 812, raw);
	}

}
