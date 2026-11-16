package net.yozmnews.digest.domain.digest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.CodePoints;
import net.yozmnews.platform.UserId;

/**
 * 다이제스트 상태 aggregate — 상태 전이의 정본 · docs/specs/digest.md §4 · digest-R1
 * <p>
 * 바꾸는 메서드는 새 값을 돌려준다. 공급자 · 모델은 시작할 때 요청의 쓸 모델을 적는다.
 */
public final class Digest {

	/** 오류 메시지의 최대 코드 포인트 — 넘으면 자른다 */
	public static final int MAX_ERROR_MESSAGE = 500;

	private final @Nullable Long id;

	private final long userId;

	private final LocalDate digestDate;

	private final Instant periodStart;

	private final Instant periodEnd;

	private final DigestStatus status;

	private final DigestTrigger trigger;

	private final @Nullable String provider;

	private final @Nullable String model;

	private final @Nullable Integer candidateCount;

	private final @Nullable Integer inputTokens;

	private final @Nullable Integer outputTokens;

	private final @Nullable Long durationMs;

	private final @Nullable DigestFailure errorCode;

	private final @Nullable String errorMessage;

	private final Instant createdAt;

	private final @Nullable Instant startedAt;

	private final @Nullable Instant completedAt;

	private final @Nullable Long version;

	Digest(@Nullable Long id, long userId, LocalDate digestDate, Instant periodStart, Instant periodEnd,
			DigestStatus status, DigestTrigger trigger, @Nullable String provider, @Nullable String model,
			@Nullable Integer candidateCount, @Nullable Integer inputTokens, @Nullable Integer outputTokens,
			@Nullable Long durationMs, @Nullable DigestFailure errorCode, @Nullable String errorMessage,
			Instant createdAt, @Nullable Instant startedAt, @Nullable Instant completedAt, @Nullable Long version) {
		this.id = id;
		this.userId = userId;
		this.digestDate = Objects.requireNonNull(digestDate, "digestDate");
		this.periodStart = Objects.requireNonNull(periodStart, "periodStart");
		this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
		this.status = Objects.requireNonNull(status, "status");
		this.trigger = Objects.requireNonNull(trigger, "trigger");
		this.provider = provider;
		this.model = model;
		this.candidateCount = candidateCount;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.durationMs = durationMs;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.version = version;
	}

	/** 생성을 기다리는 다이제스트를 만든다 */
	public static Digest schedule(UserId user, LocalDate digestDate, DigestPeriod period, DigestTrigger trigger,
			Instant now) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(period, "period");
		return new Digest(null, user.value(), digestDate, period.start(), period.end(), DigestStatus.SCHEDULED, trigger,
				null, null, null, null, null, null, null, null, now, null, null, null);
	}

	/**
	 * 생성을 시작한다 — 이미 시작했으면 처음부터 다시 시작한다.
	 * @throws IllegalStateException 끝난 다이제스트일 때
	 */
	public Digest start(LlmModel model, int candidateCount, Instant now) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(now, "now");
		if (candidateCount < 0) {
			throw new IllegalArgumentException("후보 수가 음수다: " + candidateCount);
		}
		if (this.status.finished()) {
			throw new IllegalStateException("끝난 다이제스트는 시작할 수 없다: " + this.status);
		}
		return new Digest(this.id, this.userId, this.digestDate, this.periodStart, this.periodEnd,
				DigestStatus.RUNNING, this.trigger, model.provider().key(), model.id(), candidateCount, null, null,
				null, null, null, this.createdAt, now, null, this.version);
	}

	/**
	 * 생성을 끝낸다.
	 * @throws IllegalStateException 생성 중이 아닐 때
	 */
	public Digest complete(Completion completion, Instant now) {
		Objects.requireNonNull(completion, "completion");
		Objects.requireNonNull(now, "now");
		if (this.status != DigestStatus.RUNNING) {
			throw new IllegalStateException("생성 중이 아닌 다이제스트는 끝낼 수 없다: " + this.status);
		}
		return switch (completion) {
			case Completion.Ready ready -> finish(DigestStatus.READY, ready.usage(), ready.took(), null, null, now);
			case Completion.Empty empty -> finish(DigestStatus.EMPTY, null, null, null, null, now);
			case Completion.Failed failed -> finish(DigestStatus.FAILED, failed.usage(), failed.took(),
					failed.failure(), CodePoints.prefix(failed.message(), MAX_ERROR_MESSAGE), now);
		};
	}

	private Digest finish(DigestStatus finished, @Nullable TokenUsage usage, @Nullable Duration took,
			@Nullable DigestFailure failure, @Nullable String message, Instant now) {
		return new Digest(this.id, this.userId, this.digestDate, this.periodStart, this.periodEnd, finished,
				this.trigger, this.provider, this.model, this.candidateCount,
				usage == null ? null : usage.inputTokens(), usage == null ? null : usage.outputTokens(),
				took == null ? null : took.toMillis(), failure, message, this.createdAt, this.startedAt, now,
				this.version);
	}

	/**
	 * @throws IllegalStateException 아직 저장하지 않아 식별자가 없을 때
	 */
	public DigestId id() {
		if (this.id == null) {
			throw new IllegalStateException("저장하지 않은 다이제스트다");
		}
		return new DigestId(this.id);
	}

	public UserId userId() {
		return new UserId(this.userId);
	}

	public LocalDate digestDate() {
		return this.digestDate;
	}

	public DigestPeriod period() {
		return new DigestPeriod(this.periodStart, this.periodEnd);
	}

	public DigestStatus status() {
		return this.status;
	}

	public DigestTrigger trigger() {
		return this.trigger;
	}

	/**
	 * @return 시작할 때 적은 공급자 키 — 시작 전이면 빈 값
	 */
	public Optional<String> provider() {
		return Optional.ofNullable(this.provider);
	}

	/**
	 * @return 시작할 때 적은 모델 ID — 시작 전이면 빈 값
	 */
	public Optional<String> model() {
		return Optional.ofNullable(this.model);
	}

	/**
	 * @return 시작할 때 받은 후보 수 — 시작 전이면 빈 값
	 */
	public Optional<Integer> candidateCount() {
		return Optional.ofNullable(this.candidateCount);
	}

	/**
	 * @return 생성기를 부른 사용량 — 부르지 않았거나 끝나기 전이면 빈 값
	 */
	public Optional<TokenUsage> usage() {
		if (this.inputTokens == null || this.outputTokens == null) {
			return Optional.empty();
		}
		return Optional.of(new TokenUsage(this.inputTokens, this.outputTokens));
	}

	/**
	 * @return 생성기 호출의 소요 시간(밀리초) — 부르지 않았거나 끝나기 전이면 빈 값
	 */
	public Optional<Long> durationMs() {
		return Optional.ofNullable(this.durationMs);
	}

	/**
	 * @return 실패 코드 — 실패가 아니면 빈 값
	 */
	public Optional<DigestFailure> failure() {
		return Optional.ofNullable(this.errorCode);
	}

	/**
	 * @return 실패 메시지 — 실패가 아니면 빈 값
	 */
	public Optional<String> errorMessage() {
		return Optional.ofNullable(this.errorMessage);
	}

	public Instant createdAt() {
		return this.createdAt;
	}

	/**
	 * @return 마지막으로 시작한 시각 — 시작 전이면 빈 값
	 */
	public Optional<Instant> startedAt() {
		return Optional.ofNullable(this.startedAt);
	}

	/**
	 * @return 끝난 시각 — 끝나기 전이면 빈 값
	 */
	public Optional<Instant> completedAt() {
		return Optional.ofNullable(this.completedAt);
	}

	/**
	 * @return 저장소가 매기는 버전 — 저장 전이면 빈 값
	 */
	public Optional<Long> version() {
		return Optional.ofNullable(this.version);
	}

}
