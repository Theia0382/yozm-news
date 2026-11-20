package net.yozmnews.feedback.domain.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.yozmnews.event.EventId;
import org.junit.jupiter.api.Test;

/**
 * 받을 수 있는 항목의 판정 — 요청의 사건이 든 항목을 찾았을 때만.
 * <p>
 * 지키는 규칙: feedback-R2
 */
class FeedbackAcceptanceTests {

	@Test
	void acceptsOnlyAFoundItem() {
		assertThat(FeedbackAcceptance.accepts(List.of(new EventId(1)))).isTrue();
		assertThat(FeedbackAcceptance.accepts(List.of(new EventId(1), new EventId(2)))).isTrue();
		assertThat(FeedbackAcceptance.accepts(List.of())).isFalse();
	}

}
