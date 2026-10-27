package net.yozmnews.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.platform.domain.EventResubmissionPolicy.Decision;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.EventPublication.Status;

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
