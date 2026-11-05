package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 사건 소속 질문 문구 — 지금 문구와 판, 질문 문장 조립.
 * <p>
 * 지키는 규칙: evaluation-R37
 */
class MembershipWordingTests {

	@Test
	void theCurrentWordingIsTheEnglishQuestionOfTheFirstVersion() {
		assertThat(MembershipWording.CURRENT).isEqualTo(new MembershipWording("membership-1",
				"Does this article report on the same news event (the same incident, announcement or decision, or its direct follow-up) as the following articles?"));
	}

	@Test
	void appendsEachTitleOnItsOwnDashedLine() {
		assertThat(new MembershipWording("w-test", "같은 사건인가").question(List.of("가", "나")))
			.isEqualTo("같은 사건인가\n- 가\n- 나");
	}

	@Test
	void refusesBlankWordingAndNoTitles() {
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipWording(" ", "같은 사건인가"));
		assertThatIllegalArgumentException().isThrownBy(() -> new MembershipWording("w-test", ""));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new MembershipWording("w-test", "같은 사건인가").question(List.of()));
	}

}
