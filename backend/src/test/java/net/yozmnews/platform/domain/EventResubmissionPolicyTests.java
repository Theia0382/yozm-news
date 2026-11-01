package net.yozmnews.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.platform.StrandedReason;
import net.yozmnews.platform.domain.EventResubmissionPolicy.Decision;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.modulith.events.EventPublication;
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
	void decidesByStatusAndAttempts(Status status, int attempts, Decision expected) {
		assertThat(this.policy.decide(new Publication(status, attempts))).isEqualTo(expected);
		assertThat(this.policy.decide(status, attempts)).isEqualTo(expected);
	}

	// 이벤트 클래스를 로드할 수 있는 행은 상한에 이른 실패만 남겨진 발행이다 — 상한 바로 아래(5)가 대조 leg
	@ParameterizedTest
	@CsvSource({ "FAILED, 5, ", "FAILED, 6, EXHAUSTED", "FAILED, 7, EXHAUSTED", "RESUBMITTED, 6, ", "PUBLISHED, 6, ",
			"PROCESSING, 6, ", "COMPLETED, 6, " })
	void strandsExhaustedFailuresOfLoadableEvents(Status status, int attempts, @Nullable StrandedReason expected) {
		assertThat(this.policy.stranded(status, attempts, true)).isEqualTo(Optional.ofNullable(expected));
	}

	// 이벤트 클래스를 로드할 수 없는 행은 완료되지 않았으면 처리 시도와 상관없이 남겨진 발행이다
	@ParameterizedTest
	@CsvSource({ "PUBLISHED, 1, UNREADABLE", "PROCESSING, 1, UNREADABLE", "FAILED, 1, UNREADABLE",
			"RESUBMITTED, 1, UNREADABLE", "FAILED, 6, UNREADABLE", "COMPLETED, 1, " })
	void strandsIncompletePublicationsOfUnloadableEvents(Status status, int attempts,
			@Nullable StrandedReason expected) {
		assertThat(this.policy.stranded(status, attempts, false)).isEqualTo(Optional.ofNullable(expected));
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

	private record Publication(Status status, int attempts) implements EventPublication {

		@Override
		public UUID getIdentifier() {
			return UUID.fromString("00000000-0000-0000-0000-000000000001");
		}

		@Override
		public Object getEvent() {
			return "event";
		}

		@Override
		public Instant getPublicationDate() {
			return Instant.parse("2026-10-27T00:00:00Z");
		}

		@Override
		public Optional<Instant> getCompletionDate() {
			return Optional.empty();
		}

		@Override
		public Status getStatus() {
			return this.status;
		}

		@Override
		public @Nullable Instant getLastResubmissionDate() {
			return null;
		}

		@Override
		public int getCompletionAttempts() {
			return this.attempts;
		}

	}

}
