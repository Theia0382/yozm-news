package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 사건 소속 판정의 후보 사건 — 서술 제목의 상한과 그 상한을 적용하는 팩터리.
 * <p>
 * 지키는 규칙: evaluation-R37
 */
class CandidateEventTests {

	/** 한자 확장 B 의 첫 글자 — 코드 포인트 하나가 UTF-16 두 단위(서로게이트 쌍)다 */
	private static final String SUPPLEMENTARY = "𠀀";

	@Test
	void keepsTheSurrogatePairThatEndsTheFirstTwoHundredCodePoints() {
		String title = "a".repeat(199) + SUPPLEMENTARY + "b";

		assertThat(CandidateEvent.limited(12, List.of(title)).orElseThrow().titles())
			.containsExactly("a".repeat(199) + SUPPLEMENTARY);
	}

	@Test
	void cutsBeforeASurrogatePairThatStartsAfterTwoHundredCodePoints() {
		String title = "a".repeat(200) + SUPPLEMENTARY;

		assertThat(CandidateEvent.limited(12, List.of(title)).orElseThrow().titles()).containsExactly("a".repeat(200));
	}

	@Test
	void keepsATitleWithinTheLimitAsItIs() {
		assertThat(CandidateEvent.limited(12, List.of("서울 시장 선거 개표", "a".repeat(200))).orElseThrow().titles())
			.containsExactly("서울 시장 선거 개표", "a".repeat(200));
	}

	@Test
	void stripsTrailingSpaceAfterCuttingAndDropsBlankTitles() {
		String title = "a".repeat(198) + "  b";

		assertThat(CandidateEvent.limited(12, List.of(" ", title, "부산 항만 파업")).orElseThrow().titles())
			.containsExactly("a".repeat(198), "부산 항만 파업");
	}

	@Test
	void isEmptyWhenNoTitleIsLeft() {
		assertThat(CandidateEvent.limited(12, List.of(" ", "\t"))).isEmpty();
	}

	@Test
	void acceptsFiveTitlesAndRefusesSix() {
		assertThat(new CandidateEvent(12, List.of("가", "나", "다", "라", "마")).titles()).hasSize(5);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new CandidateEvent(12, List.of("가", "나", "다", "라", "마", "바")));
	}

	@Test
	void refusesAnEventIdBelowOneNoTitlesABlankTitleOrATitleOverTheLimit() {
		assertThatIllegalArgumentException().isThrownBy(() -> new CandidateEvent(0, List.of("제목")));
		assertThatIllegalArgumentException().isThrownBy(() -> new CandidateEvent(12, List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new CandidateEvent(12, List.of(" ")));
		assertThatIllegalArgumentException().isThrownBy(() -> new CandidateEvent(12, List.of("a".repeat(201))));
		assertThat(new CandidateEvent(12, List.of("a".repeat(199) + SUPPLEMENTARY)).titles()).hasSize(1);
	}

}
