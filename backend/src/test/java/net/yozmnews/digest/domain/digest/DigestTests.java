package net.yozmnews.digest.domain.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;

/**
 * 다이제스트 상태 전이 — 예약 → 시작(다시 시작) → 끝(READY · EMPTY · FAILED), 끝난 뒤 · 시작 전의 거부. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R1
 */
class DigestTests {

	private static final Instant T0 = Instant.parse("2026-11-15T22:45:00Z");

	private static final Instant T1 = Instant.parse("2026-11-15T22:46:00Z");

	private static final Instant T2 = Instant.parse("2026-11-15T22:47:00Z");

	private static final LlmModel MODEL = new LlmModel(Provider.ANTHROPIC, "claude-sonnet-5-5", 100_000, true);

	private static Digest scheduled() {
		return Digest.schedule(new UserId(42), LocalDate.parse("2026-11-16"),
				new DigestPeriod(Instant.parse("2026-11-14T22:00:00Z"), Instant.parse("2026-11-15T22:00:00Z")),
				DigestTrigger.SCHEDULED, T0);
	}

	@Test
	void schedulesWithoutModelOrResult() {
		Digest digest = scheduled();

		assertThat(digest.status()).isEqualTo(DigestStatus.SCHEDULED);
		assertThat(digest.userId()).isEqualTo(new UserId(42));
		assertThat(digest.trigger()).isEqualTo(DigestTrigger.SCHEDULED);
		assertThat(digest.createdAt()).isEqualTo(T0);
		assertThat(digest.provider()).isEmpty();
		assertThat(digest.startedAt()).isEmpty();
		assertThat(digest.completedAt()).isEmpty();
	}

	@Test
	void startsWithTheRequestedModelAndStartsAgain() {
		Digest started = scheduled().start(MODEL, 5, T1);

		assertThat(started.status()).isEqualTo(DigestStatus.RUNNING);
		assertThat(started.provider()).contains("anthropic");
		assertThat(started.model()).contains("claude-sonnet-5-5");
		assertThat(started.candidateCount()).contains(5);
		assertThat(started.startedAt()).contains(T1);

		Digest again = started.start(new LlmModel(Provider.OPENAI, "gpt-5.5-mini", 100_000, true), 3, T2);

		assertThat(again.status()).isEqualTo(DigestStatus.RUNNING);
		assertThat(again.provider()).contains("openai");
		assertThat(again.candidateCount()).contains(3);
		assertThat(again.startedAt()).contains(T2);
	}

	@Test
	void completesReadyWithUsageAndDuration() {
		Digest ready = scheduled().start(MODEL, 5, T1)
			.complete(new Completion.Ready(new TokenUsage(1_200, 340), Duration.ofMillis(2_000)), T2);

		assertThat(ready.status()).isEqualTo(DigestStatus.READY);
		assertThat(ready.usage()).contains(new TokenUsage(1_200, 340));
		assertThat(ready.durationMs()).contains(2_000L);
		assertThat(ready.completedAt()).contains(T2);
		assertThat(ready.failure()).isEmpty();
		assertThat(ready.provider()).contains("anthropic");
	}

	@Test
	void completesEmptyWithoutUsage() {
		Digest empty = scheduled().start(MODEL, 0, T1).complete(new Completion.Empty(), T2);

		assertThat(empty.status()).isEqualTo(DigestStatus.EMPTY);
		assertThat(empty.usage()).isEmpty();
		assertThat(empty.durationMs()).isEmpty();
		assertThat(empty.completedAt()).contains(T2);
	}

	@Test
	void completesFailedAndCutsALongMessage() {
		String message = "가".repeat(499) + "𝐀" + "넘침";

		Digest failed = scheduled().start(MODEL, 5, T1)
			.complete(new Completion.Failed(DigestFailure.NO_CITATION, message, new TokenUsage(10, 20),
					Duration.ofMillis(5)), T2);

		assertThat(failed.status()).isEqualTo(DigestStatus.FAILED);
		assertThat(failed.failure()).contains(DigestFailure.NO_CITATION);
		assertThat(failed.errorMessage()).contains("가".repeat(499) + "𝐀");
		assertThat(failed.usage()).contains(new TokenUsage(10, 20));
	}

	@Test
	void keepsAShortMessageWhole() {
		Digest failed = scheduled().start(MODEL, 5, T1)
			.complete(new Completion.Failed(DigestFailure.KEY_MISSING, "키가 없다", null, null), T2);

		assertThat(failed.errorMessage()).contains("키가 없다");
		assertThat(failed.usage()).isEmpty();
		assertThat(failed.durationMs()).isEmpty();
	}

	@Test
	void aFailureOfTheGatewayHasTheTimeButNoTokens() {
		Digest failed = scheduled().start(MODEL, 5, T1)
			.complete(new Completion.Failed(DigestFailure.KEY_INVALID, "OpenAI 401", null, Duration.ofMillis(1_500)),
					T2);

		assertThat(failed.failure()).contains(DigestFailure.KEY_INVALID);
		assertThat(failed.usage()).isEmpty();
		assertThat(failed.durationMs()).contains(1_500L);
	}

	@Test
	void tokensWithoutTimeAreRefused() {
		assertThatThrownBy(() -> new Completion.Failed(DigestFailure.NO_CITATION, "x", new TokenUsage(1, 1), null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void refusesToStartAFinishedDigest() {
		Digest ready = scheduled().start(MODEL, 5, T1)
			.complete(new Completion.Ready(new TokenUsage(1, 1), Duration.ZERO), T2);

		assertThatThrownBy(() -> ready.start(MODEL, 5, T2)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void refusesToCompleteADigestThatHasNotStartedOrHasFinished() {
		assertThatThrownBy(() -> scheduled().complete(new Completion.Empty(), T1))
			.isInstanceOf(IllegalStateException.class);

		Digest empty = scheduled().start(MODEL, 0, T1).complete(new Completion.Empty(), T2);

		assertThatThrownBy(() -> empty.complete(new Completion.Empty(), T2))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void knowsWhichStatusesAreFinished() {
		assertThat(DigestStatus.SCHEDULED.finished()).isFalse();
		assertThat(DigestStatus.RUNNING.finished()).isFalse();
		assertThat(DigestStatus.READY.finished()).isTrue();
		assertThat(DigestStatus.EMPTY.finished()).isTrue();
		assertThat(DigestStatus.FAILED.finished()).isTrue();
	}

	@Test
	void hasNoIdBeforeSaving() {
		assertThatThrownBy(() -> scheduled().id()).isInstanceOf(IllegalStateException.class);
	}

}
