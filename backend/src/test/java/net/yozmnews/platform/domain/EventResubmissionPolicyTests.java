package net.yozmnews.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import net.yozmnews.platform.StrandedReason;
import net.yozmnews.platform.domain.EventResubmissionPolicy.Decision;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.modulith.events.EventPublication.Status;

/**
 * 재처리 판정과 설정 조건 — 스프링 없이 정책을 직접 만든다.
 * <p>
 * 지키는 규칙: platform-R1 · platform-R3 · platform-R16
 */
class EventResubmissionPolicyTests {

	// 판정은 두 시간에 기대지 않는다 — 조건 경계에서 먼 값을 써서 조건 확인의 변화가 판정 시험에 번지지 않게 한다
	private final EventResubmissionPolicy policy = new EventResubmissionPolicy(Duration.ofMinutes(1),
			Duration.ofMinutes(30));

	@ParameterizedTest
	@CsvSource({ "FAILED, 1, RESUBMIT", "FAILED, 5, RESUBMIT", "FAILED, 6, EXHAUSTED", "FAILED, 7, EXHAUSTED",
			"PUBLISHED, 1, SKIP", "PUBLISHED, 6, SKIP", "PROCESSING, 1, SKIP", "PROCESSING, 6, SKIP",
			"RESUBMITTED, 1, SKIP", "RESUBMITTED, 6, SKIP", "COMPLETED, 1, SKIP", "COMPLETED, 6, SKIP" })
	void decidesReadablePublicationsByStatusAndAttempts(Status status, int attempts, Decision expected) {
		assertThat(this.policy.decide(status, attempts, () -> true)).isEqualTo(expected);
	}

	// 실패 발행은 읽을 수 없으면 처리 시도와 상관없이 다시 처리하지 않는다
	@ParameterizedTest
	@CsvSource({ "FAILED, 1, UNREADABLE", "FAILED, 6, UNREADABLE" })
	void decidesUnreadableFailuresAsUnreadable(Status status, int attempts, Decision expected) {
		assertThat(this.policy.decide(status, attempts, () -> false)).isEqualTo(expected);
	}

	// 실패하지 않은 발행은 읽지 않는다 — 읽으면 실패하는 공급자로 본다
	@ParameterizedTest
	@CsvSource({ "PUBLISHED", "PROCESSING", "RESUBMITTED", "COMPLETED" })
	void doesNotReadPublicationsThatHaveNotFailed(Status status) {
		assertThat(this.policy.decide(status, 1, () -> {
			throw new AssertionError("실패하지 않은 발행을 읽었다");
		})).isEqualTo(Decision.SKIP);
	}

	// 이벤트를 읽을 수 있는 행은 상한에 이른 실패만 남겨진 발행이다 — 상한 바로 아래(5)가 대조 leg
	@ParameterizedTest
	@CsvSource({ "FAILED, 5, ", "FAILED, 6, EXHAUSTED", "FAILED, 7, EXHAUSTED", "RESUBMITTED, 6, ", "PUBLISHED, 6, ",
			"PROCESSING, 6, ", "COMPLETED, 6, " })
	void strandsExhaustedFailuresOfReadableEvents(Status status, int attempts, @Nullable StrandedReason expected) {
		assertThat(this.policy.stranded(status, attempts, () -> true)).isEqualTo(Optional.ofNullable(expected));
	}

	// 이벤트를 읽을 수 없는 행은 완료되지 않았으면 처리 시도와 상관없이 남겨진 발행이다
	@ParameterizedTest
	@CsvSource({ "PUBLISHED, 1, UNREADABLE", "PROCESSING, 1, UNREADABLE", "FAILED, 1, UNREADABLE",
			"RESUBMITTED, 1, UNREADABLE", "FAILED, 6, UNREADABLE", "COMPLETED, 1, " })
	void strandsIncompletePublicationsOfUnreadableEvents(Status status, int attempts,
			@Nullable StrandedReason expected) {
		assertThat(this.policy.stranded(status, attempts, () -> false)).isEqualTo(Optional.ofNullable(expected));
	}

	// 남겨진 발행 판정은 실패 발행이라도 읽기를 한 번만 묻는다
	@Test
	void asksOnceWhetherAFailedPublicationIsReadable() {
		AtomicInteger asked = new AtomicInteger();

		assertThat(this.policy.stranded(Status.FAILED, 6, () -> asked.incrementAndGet() > 0))
			.isEqualTo(Optional.of(StrandedReason.EXHAUSTED));
		assertThat(asked).hasValue(1);
	}

	@ParameterizedTest
	@CsvSource({ "PT5M, PT30M", "PT5M59S, PT30M" })
	void acceptsIntervalsWhoseLastResubmissionComesBeforeStaleness(Duration interval, Duration staleAfter) {
		new EventResubmissionPolicy(interval, staleAfter);
	}

	@ParameterizedTest
	@CsvSource({ "PT5M, PT25M", "PT6M, PT30M", "PT0S, PT30M", "-PT5M, PT30M", "PT5M, PT0S", "PT5M, -PT30M" })
	void rejectsIntervalsThatReachStaleness(Duration interval, Duration staleAfter) {
		assertThatIllegalArgumentException().isThrownBy(() -> new EventResubmissionPolicy(interval, staleAfter));
	}

	@Test
	void rejectsMissingDurations() {
		assertThatNullPointerException().isThrownBy(() -> new EventResubmissionPolicy(null, Duration.ofMinutes(30)));
		assertThatNullPointerException().isThrownBy(() -> new EventResubmissionPolicy(Duration.ofMinutes(5), null));
	}

}
