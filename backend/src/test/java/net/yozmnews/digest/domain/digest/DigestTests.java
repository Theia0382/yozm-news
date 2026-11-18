package net.yozmnews.digest.domain.digest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.DigestStatus;
import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;

/**
 * 다이제스트 상태 전이 — 예약 → 시작 → 끝(READY · EMPTY · FAILED), 시작하지 않고 · 생성 중에 실패로 끝내기, 생성 중 · 끝난 뒤 · 시작 전의 거부. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R1
 */
class DigestTests {

	private static final Instant T0 = Instant.parse("2026-11-15T22:45:00Z");

	private static final Instant T1 = Instant.parse("2026-11-15T22:46:00Z");

	private static final Instant T2 = Instant.parse("2026-11-15T22:47:00Z");

	private static final UUID TOKEN = UUID.fromString("5f0c9a2e-3b1d-4c7e-9a40-2d6f8e1b7c35");

	private static final UUID NEW_TOKEN = UUID.fromString("0b7e4c1a-9d2f-4e83-b6a5-71c3f0d9e248");

	private static final LlmModel MODEL = new LlmModel(Provider.ANTHROPIC, "claude-sonnet-5-5", 100_000, true);

	private static Digest scheduled() {
		return Digest.schedule(new UserId(42), LocalDate.parse("2026-11-16"),
				new DigestPeriod(Instant.parse("2026-11-14T22:00:00Z"), Instant.parse("2026-11-15T22:00:00Z")),
				DigestTrigger.SCHEDULED, TOKEN, T0);
	}

	@Test
	void schedulesWithoutModelOrResult() {
		Digest digest = scheduled();

		assertThat(digest.status()).isEqualTo(DigestStatus.SCHEDULED);
		assertThat(digest.userId()).isEqualTo(new UserId(42));
		assertThat(digest.trigger()).isEqualTo(DigestTrigger.SCHEDULED);
		assertThat(digest.createdAt()).isEqualTo(T0);
		assertThat(digest.dispatchToken()).isEqualTo(TOKEN);
		assertThat(digest.dispatchedAt()).isEqualTo(T0);
		assertThat(digest.provider()).isEmpty();
		assertThat(digest.startedAt()).isEmpty();
		assertThat(digest.completedAt()).isEmpty();
	}

	@Test
	void startsWithTheRequestedModelAndDoesNotStartAgain() {
		Digest started = scheduled().start(MODEL, 5, T1);

		assertThat(started.status()).isEqualTo(DigestStatus.RUNNING);
		assertThat(started.provider()).contains("anthropic");
		assertThat(started.model()).contains("claude-sonnet-5-5");
		assertThat(started.candidateCount()).contains(5);
		assertThat(started.startedAt()).contains(T1);

		// 생성 중인 다이제스트는 다시 시작하지 않는다 — 같은 다이제스트의 생성기 호출은 하나다
		assertThatThrownBy(() -> started.start(new LlmModel(Provider.OPENAI, "gpt-5.5-mini", 100_000, true), 3, T2))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void abandonsAScheduledDigestWithoutModelTokensOrTime() {
		Digest abandoned = scheduled().abandon(DigestFailure.NO_USABLE_MODEL, "쓸 모델이 없다", T1);

		assertThat(abandoned.status()).isEqualTo(DigestStatus.FAILED);
		assertThat(abandoned.failure()).contains(DigestFailure.NO_USABLE_MODEL);
		assertThat(abandoned.errorMessage()).contains("쓸 모델이 없다");
		assertThat(abandoned.completedAt()).contains(T1);
		assertThat(abandoned.provider()).isEmpty();
		assertThat(abandoned.model()).isEmpty();
		assertThat(abandoned.usage()).isEmpty();
		assertThat(abandoned.durationMs()).isEmpty();
	}

	@Test
	void abandonsARunningDigestAndKeepsItsModel() {
		Digest abandoned = scheduled().start(MODEL, 5, T1).abandon(DigestFailure.INTERRUPTED, "끝나지 않았다", T2);

		assertThat(abandoned.status()).isEqualTo(DigestStatus.FAILED);
		assertThat(abandoned.failure()).contains(DigestFailure.INTERRUPTED);
		assertThat(abandoned.completedAt()).contains(T2);
		assertThat(abandoned.provider()).contains("anthropic");
		assertThat(abandoned.model()).contains("claude-sonnet-5-5");
		assertThat(abandoned.candidateCount()).contains(5);
		assertThat(abandoned.usage()).isEmpty();
		assertThat(abandoned.durationMs()).isEmpty();
	}

	@Test
	void refusesToAbandonAFinishedDigest() {
		Digest empty = scheduled().start(MODEL, 0, T1).complete(new Completion.Empty(), T2);

		assertThatThrownBy(() -> empty.abandon(DigestFailure.INTERRUPTED, "끝나지 않았다", T2))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void redispatchesAScheduledDigestWithANewTokenOnly() {
		Digest again = scheduled().redispatch(NEW_TOKEN, T1);

		assertThat(again.status()).isEqualTo(DigestStatus.SCHEDULED);
		assertThat(again.dispatchToken()).isEqualTo(NEW_TOKEN);
		assertThat(again.dispatchedAt()).isEqualTo(T1);
		assertThat(again.createdAt()).isEqualTo(T0);
		// 생성 중이거나 끝난 다이제스트는 다시 등록하지 않는다 — 시작하고 끝내도 토큰은 그대로다
		Digest running = scheduled().start(MODEL, 5, T1);
		assertThatThrownBy(() -> running.redispatch(NEW_TOKEN, T2)).isInstanceOf(IllegalStateException.class);
		Digest failed = running.abandon(DigestFailure.INTERRUPTED, "끝나지 않았다", T2);
		assertThat(failed.dispatchToken()).isEqualTo(TOKEN);
		assertThat(failed.dispatchedAt()).isEqualTo(T0);
		assertThatThrownBy(() -> failed.redispatch(NEW_TOKEN, T2)).isInstanceOf(IllegalStateException.class);
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
