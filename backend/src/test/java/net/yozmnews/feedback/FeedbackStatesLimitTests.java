package net.yozmnews.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.digest.domain.output.DigestOutputSchema;
import net.yozmnews.feedback.application.FeedbackStates;
import org.junit.jupiter.api.Test;

/**
 * 봉인 — 화면의 현재 상태 조회 상한(500)은 다이제스트 하나의 인용 상한(460)을 덮게 고른 값이다. {@code feedback} 은 {@code digest} 의
 * 상수를 읽지 못해 두 값이 따로 움직인다 — 어느 쪽이 바뀌면 이 시험이 실패해 다시 맞추게 한다. 두 값은 손으로 적은 리터럴이다.
 * <p>
 * 지키는 규칙: feedback-R7
 */
class FeedbackStatesLimitTests {

	@Test
	void theStatesLimitCoversTheCitationsOfOneDigest() {
		int citations = DigestOutputSchema.MAX_TOP_STORIES * DigestOutputSchema.MAX_REFS_PER_STORY
				+ DigestOutputSchema.MAX_MORE_HEADLINES;

		assertThat(citations).isEqualTo(460);
		assertThat(FeedbackStates.MAX_ARTICLES).isEqualTo(500);
		assertThat(FeedbackStates.MAX_ARTICLES).isGreaterThanOrEqualTo(citations);
	}

}
