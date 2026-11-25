package net.yozmnews.report.domain.report;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.CodePoints;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.generator.TokenUsage;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 리포트 상태 aggregate — 상태 전이의 정본 · docs/specs/report.md §4 · report-R1
 * <p>
 * 바꾸는 메서드는 새 값을 돌려준다. 공급자 · 모델은 시작할 때 요청의 쓸 모델을 적는다. 통계 스냅숏(jsonb 칸) · 직전 리포트 · 사용자 시간대는
 * 이 aggregate 의 속성이 아니다 — 시작 저장 직전에 {@link ReportRepository#writeStart}, 시작하지 않고 끝내기 직전에
 * {@link ReportRepository#writeZone}, 다시 만들기 저장 직후에 {@link ReportRepository#clearStart} 가 쓴다.
 * <p>
 * 등록 토큰은 생성 작업의 ID 다 — 다시 등록 · 다시 만들기마다 새 토큰이다. 예약 시각은 생성을 기다림이 된 시각(만들 때 · 다시 만들기를 받을 때)이고
 * 거두기의 시작 기한을 이 시각으로 잰다.
 */
@Table("reports")
public final class Report {

	/** 오류 메시지의 최대 코드 포인트 — 넘으면 자른다 */
	public static final int MAX_ERROR_MESSAGE = 500;

	@Id
	private @Nullable Long id;

	private final long userId;

	private final ReportPeriodKind periodKind;

	private final Instant periodStart;

	private final Instant periodEnd;

	private final ReportStatus status;

	private final @Nullable String provider;

	private final @Nullable String model;

	private final @Nullable Integer inputTokens;

	private final @Nullable Integer outputTokens;

	private final @Nullable Long durationMs;

	private final @Nullable ReportFailure errorCode;

	private final @Nullable String errorMessage;

	private final Instant createdAt;

	private final UUID dispatchToken;

	private final Instant dispatchedAt;

	private final Instant scheduledAt;

	private final int regenerations;

	private final @Nullable Instant startedAt;

	/** 끝낸 시각 — 저장 정밀도(마이크로초)로 갖는다 */
	private final @Nullable Instant completedAt;

	@Version
	private @Nullable Long version;

	@PersistenceCreator
	Report(@Nullable Long id, long userId, ReportPeriodKind periodKind, Instant periodStart, Instant periodEnd,
			ReportStatus status, @Nullable String provider, @Nullable String model, @Nullable Integer inputTokens,
			@Nullable Integer outputTokens, @Nullable Long durationMs, @Nullable ReportFailure errorCode,
			@Nullable String errorMessage, Instant createdAt, UUID dispatchToken, Instant dispatchedAt,
			Instant scheduledAt, int regenerations, @Nullable Instant startedAt, @Nullable Instant completedAt,
			@Nullable Long version) {
		this.id = id;
		this.userId = userId;
		this.periodKind = Objects.requireNonNull(periodKind, "periodKind");
		this.periodStart = Objects.requireNonNull(periodStart, "periodStart");
		this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
		this.status = Objects.requireNonNull(status, "status");
		this.provider = provider;
		this.model = model;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.durationMs = durationMs;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.dispatchToken = Objects.requireNonNull(dispatchToken, "dispatchToken");
		this.dispatchedAt = Objects.requireNonNull(dispatchedAt, "dispatchedAt");
		this.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt");
		this.regenerations = regenerations;
		this.startedAt = startedAt;
		this.completedAt = (completedAt == null) ? null : completedAt.truncatedTo(ChronoUnit.MICROS);
		this.version = version;
	}

	/**
	 * 생성을 기다리는 리포트를 만든다 — 만든 시각 · 등록 시각 · 예약 시각이 모두 {@code now} 다.
	 * @param dispatchToken 생성 작업의 ID
	 */
	public static Report schedule(UserId user, ReportPeriod period, UUID dispatchToken, Instant now) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(dispatchToken, "dispatchToken");
		Objects.requireNonNull(now, "now");
		return new Report(null, user.value(), period.kind(), period.start(), period.end(), ReportStatus.SCHEDULED, null,
				null, null, null, null, null, null, now, dispatchToken, now, now, 0, null, null, null);
	}

	/**
	 * 생성 작업을 새 토큰으로 다시 등록한다 — 생성을 기다리는 리포트만. 예약 시각은 그대로다.
	 * @throws IllegalStateException 생성을 기다리는 리포트가 아닐 때
	 */
	public Report redispatch(UUID token, Instant now) {
		Objects.requireNonNull(token, "token");
		Objects.requireNonNull(now, "now");
		if (this.status != ReportStatus.SCHEDULED) {
			throw new IllegalStateException("생성을 기다리는 리포트만 다시 등록할 수 있다: " + this.status);
		}
		return new Report(this.id, this.userId, this.periodKind, this.periodStart, this.periodEnd, this.status, null,
				null, null, null, null, null, null, this.createdAt, token, now, this.scheduledAt, this.regenerations,
				null, null, this.version);
	}

	/**
	 * 생성을 시작한다 — 생성을 기다리는 리포트만 시작한다. 생성 중인 리포트는 다시 시작하지 않는다.
	 * @throws IllegalStateException 생성을 기다리는 리포트가 아닐 때
	 */
	public Report start(LlmModel model, Instant now) {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(now, "now");
		if (this.status != ReportStatus.SCHEDULED) {
			throw new IllegalStateException("생성을 기다리는 리포트만 시작할 수 있다: " + this.status);
		}
		return new Report(this.id, this.userId, this.periodKind, this.periodStart, this.periodEnd, ReportStatus.RUNNING,
				model.provider().key(), model.id(), null, null, null, null, null, this.createdAt, this.dispatchToken,
				this.dispatchedAt, this.scheduledAt, this.regenerations, now, null, this.version);
	}

	/**
	 * 생성을 끝낸다.
	 * @throws IllegalStateException 생성 중이 아닐 때
	 */
	public Report complete(Completion completion, Instant now) {
		Objects.requireNonNull(completion, "completion");
		Objects.requireNonNull(now, "now");
		if (this.status != ReportStatus.RUNNING) {
			throw new IllegalStateException("생성 중이 아닌 리포트는 끝낼 수 없다: " + this.status);
		}
		return switch (completion) {
			case Completion.Ready ready -> finish(ReportStatus.READY, ready.usage(), ready.took(), null, null, now);
			case Completion.Empty empty -> finish(ReportStatus.EMPTY, null, null, null, null, now);
			case Completion.Failed failed -> finish(ReportStatus.FAILED, failed.usage(), failed.took(), failed.failure(),
					CodePoints.prefix(failed.message(), MAX_ERROR_MESSAGE), now);
		};
	}

	/**
	 * 끝나지 않은 리포트를 실패로 끝낸다 — 생성기를 부르지 않았거나(생성을 기다림) 생성이 멈췄다(생성 중). 토큰 사용량 · 소요 시간이 없다. 생성을
	 * 기다리던 리포트는 시작하지 않은 채 끝난다(공급자 · 모델 · 시작 시각이 없다).
	 * @throws IllegalStateException 끝난 리포트일 때
	 */
	public Report abandon(ReportFailure failure, String message, Instant now) {
		Objects.requireNonNull(failure, "failure");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(now, "now");
		if (this.status.finished()) {
			throw new IllegalStateException("끝난 리포트는 실패로 끝낼 수 없다: " + this.status);
		}
		return finish(ReportStatus.FAILED, null, null, failure, CodePoints.prefix(message, MAX_ERROR_MESSAGE), now);
	}

	/**
	 * 실패한 리포트를 다시 생성을 기다리게 한다 — 시작 · 결과 칸을 비우고 새 토큰 · 등록 시각 · 예약 시각으로, 다시 만든 횟수를 하나 올린다.
	 * 횟수 상한은 다시 만들기를 받는 쪽이 정한다.
	 * @throws IllegalStateException 실패한 리포트가 아닐 때
	 */
	public Report regenerate(UUID token, Instant now) {
		Objects.requireNonNull(token, "token");
		Objects.requireNonNull(now, "now");
		if (this.status != ReportStatus.FAILED) {
			throw new IllegalStateException("실패한 리포트만 다시 만들 수 있다: " + this.status);
		}
		return new Report(this.id, this.userId, this.periodKind, this.periodStart, this.periodEnd,
				ReportStatus.SCHEDULED, null, null, null, null, null, null, null, this.createdAt, token, now, now,
				this.regenerations + 1, null, null, this.version);
	}

	private Report finish(ReportStatus finished, @Nullable TokenUsage usage, @Nullable Duration took,
			@Nullable ReportFailure failure, @Nullable String message, Instant now) {
		return new Report(this.id, this.userId, this.periodKind, this.periodStart, this.periodEnd, finished,
				this.provider, this.model, usage == null ? null : usage.inputTokens(),
				usage == null ? null : usage.outputTokens(), took == null ? null : took.toMillis(), failure, message,
				this.createdAt, this.dispatchToken, this.dispatchedAt, this.scheduledAt, this.regenerations,
				this.startedAt, now, this.version);
	}

	/**
	 * @throws IllegalStateException 아직 저장하지 않아 식별자가 없을 때
	 */
	public ReportId id() {
		if (this.id == null) {
			throw new IllegalStateException("저장하지 않은 리포트다");
		}
		return new ReportId(this.id);
	}

	public UserId userId() {
		return new UserId(this.userId);
	}

	public ReportPeriod period() {
		return new ReportPeriod(this.periodKind, this.periodStart, this.periodEnd);
	}

	public ReportStatus status() {
		return this.status;
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
	 * @return 공급자가 센 토큰 — 공급자가 답한 생성으로 끝났을 때만
	 */
	public Optional<TokenUsage> usage() {
		return (this.inputTokens == null || this.outputTokens == null) ? Optional.empty()
				: Optional.of(new TokenUsage(this.inputTokens, this.outputTokens));
	}

	/**
	 * @return 생성기 호출의 소요 시간 — 생성기를 불러 끝냈을 때만
	 */
	public Optional<Duration> took() {
		return Optional.ofNullable(this.durationMs).map(Duration::ofMillis);
	}

	/**
	 * @return 실패 코드 — {@code FAILED} 일 때만
	 */
	public Optional<ReportFailure> failure() {
		return Optional.ofNullable(this.errorCode);
	}

	/**
	 * @return 실패 메시지(앞 {@value #MAX_ERROR_MESSAGE} 코드 포인트까지) — {@code FAILED} 일 때만
	 */
	public Optional<String> errorMessage() {
		return Optional.ofNullable(this.errorMessage);
	}

	public Instant createdAt() {
		return this.createdAt;
	}

	/** 생성 작업의 ID — 다시 등록 · 다시 만들기마다 새 값 */
	public UUID dispatchToken() {
		return this.dispatchToken;
	}

	/** 생성 작업을 마지막으로 등록한 시각 */
	public Instant dispatchedAt() {
		return this.dispatchedAt;
	}

	/** 생성을 기다림이 된 시각 — 만들 때 · 다시 만들기를 받을 때. 시작 기한을 이 시각으로 잰다 */
	public Instant scheduledAt() {
		return this.scheduledAt;
	}

	/** 다시 만든 횟수 */
	public int regenerations() {
		return this.regenerations;
	}

	/**
	 * @return 시작 시각 — 시작 전이면 빈 값
	 */
	public Optional<Instant> startedAt() {
		return Optional.ofNullable(this.startedAt);
	}

	/**
	 * @return 끝낸 시각(마이크로초) — 끝나기 전이면 빈 값
	 */
	public Optional<Instant> completedAt() {
		return Optional.ofNullable(this.completedAt);
	}

	/**
	 * @return 낙관적 잠금 버전 — 저장 전이면 빈 값
	 */
	public Optional<Long> version() {
		return Optional.ofNullable(this.version);
	}

}
