package net.yozmnews.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.Optional;

import net.yozmnews.platform.BatchEnd;
import net.yozmnews.platform.RoundEnd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 반복 회차의 규칙 — 회차를 끝낼 까닭과 회차 이름 꼴.
 * <p>
 * 지키는 규칙: platform-R130 · platform-R131 · platform-R134
 */
class RoundRulesTests {

	@Test
	void aFullBatchContinuesUntilFiveMinutesHavePassed() {
		assertThat(RoundRules.endAfter(BatchEnd.FULL, Duration.ZERO)).isEqualTo(Optional.empty());
		assertThat(RoundRules.endAfter(BatchEnd.FULL, Duration.ofMinutes(5).minusMillis(1))).isEqualTo(Optional.empty());
	}

	@Test
	void aFullBatchEndsTheRoundOnceFiveMinutesHavePassed() {
		assertThat(RoundRules.endAfter(BatchEnd.FULL, Duration.ofMinutes(5))).contains(RoundEnd.BUDGET);
		assertThat(RoundRules.endAfter(BatchEnd.FULL, Duration.ofMinutes(6))).contains(RoundEnd.BUDGET);
	}

	@Test
	void anyOtherBatchEndsTheRoundAtOnceWithItsOwnReason() {
		assertThat(RoundRules.endAfter(BatchEnd.DRAINED, Duration.ZERO)).contains(RoundEnd.DRAINED);
		assertThat(RoundRules.endAfter(BatchEnd.BACKED_OFF, Duration.ZERO)).contains(RoundEnd.BACKED_OFF);
		assertThat(RoundRules.endAfter(BatchEnd.STOPPED, Duration.ZERO)).contains(RoundEnd.STOPPED);
	}

	@Test
	void theValuesAreTheTargets() {
		assertThat(RoundRules.LENGTH).isEqualTo(Duration.ofMinutes(5));
		assertThat(RoundRules.RECENT).isEqualTo(Duration.ofHours(1));
		assertThat(RoundRules.KEEP).isEqualTo(Duration.ofDays(2));
	}

	@ParameterizedTest
	@ValueSource(strings = { "a", "event-features", "storyline-linking", "tag-evaluation" })
	void lowercaseWordsJoinedByHyphensAreNames(String round) {
		assertThat(RoundRules.requireName(round)).isEqualTo(round);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "Event", "event_merge", "event--merge", "-event", "event-", "event1", "이벤트", "event\n" })
	void anythingElseIsNotAName(String round) {
		assertThatIllegalArgumentException().isThrownBy(() -> RoundRules.requireName(round));
	}

}
