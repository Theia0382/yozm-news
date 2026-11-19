package net.yozmnews.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import net.yozmnews.digest.domain.selection.EventSample;
import net.yozmnews.feedback.application.FeedbackStates;
import net.yozmnews.user.domain.interest.DigestVolume;
import org.junit.jupiter.api.Test;

/**
 * 봉인 — 화면의 현재 상태 조회 상한(750)은 다이제스트 하나의 인용 기사 수 위쪽(750)을 덮게 고른 값이다. 인용은 사건마다 표본 기사이고(사건당
 * 상한 {@code digest} 의 {@link EventSample#ARTICLES_PER_EVENT} 5), 사건 수는 최대 후보 수 이하다(직접 입력 상한 {@code user} 의
 * {@link DigestVolume#MAX_COUNT} 150). {@code feedback} 은 두 모듈의 상수를 읽지 못해 세 값이 따로 움직인다 — 어느 쪽이 바뀌면 이 시험이
 * 실패해 다시 맞추게 한다. 기대값은 손으로 적은 리터럴이다.
 * <p>
 * 지키는 규칙: feedback-R7
 */
class FeedbackStatesLimitTests {

	@Test
	void theStatesLimitCoversTheCitationsOfOneDigest() {
		int citations = EventSample.ARTICLES_PER_EVENT * DigestVolume.MAX_COUNT;

		assertThat(citations).isEqualTo(750);
		assertThat(FeedbackStates.MAX_ARTICLES).isEqualTo(750);
		assertThat(FeedbackStates.MAX_ARTICLES).isGreaterThanOrEqualTo(citations);
	}

}
