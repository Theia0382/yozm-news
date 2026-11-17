package net.yozmnews.evaluation.domain.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 소속 판정의 질문 문구 — 사건 소속 · 흐름 소속의 지금 문구와 판, 질문 문장 조립.
 * <p>
 * 지키는 규칙: evaluation-R37 · evaluation-R51
 */
class MembershipWordingTests {

	@Test
	void theCurrentWordingAsksWhetherTheArticleReportsNoNewHappeningInTheSecondVersion() {
		assertThat(MembershipWording.CURRENT).isEqualTo(new MembershipWording("membership-2",
				"Do the following articles already report the happening this article reports, so that this article reports no new happening beyond them? A happening is one thing that occurred once: one act, occurrence, statement or decision. A response by someone else, another act in the same matter, or a similar occurrence elsewhere is a new happening. If neither this article nor the following articles report a particular happening, answer yes if they describe the same trend."));
	}

	@Test
	void theSameMatterWordingAsksWhetherTheArticlesUnfoldOneMatterInTheFirstVersion() {
		assertThat(MembershipWording.SAME_MATTER).isEqualTo(new MembershipWording("matter-1",
				"Do this article and the following articles cover the unfolding of one matter, rather than separate happenings that are only of a similar kind?"));
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
