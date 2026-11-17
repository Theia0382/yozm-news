package net.yozmnews.feedback.domain.record;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 받을 수 있는 기사의 판정 — 기사가 있고 인용했을 때만.
 * <p>
 * 지키는 규칙: feedback-R2
 */
class FeedbackAcceptanceTests {

	@Test
	void acceptsOnlyAnExistingCitedArticle() {
		assertThat(FeedbackAcceptance.accepts(true, true)).isTrue();
		assertThat(FeedbackAcceptance.accepts(true, false)).isFalse();
		assertThat(FeedbackAcceptance.accepts(false, true)).isFalse();
		assertThat(FeedbackAcceptance.accepts(false, false)).isFalse();
	}

}
