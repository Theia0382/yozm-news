package net.yozmnews.report.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.OutputShape;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.content.ReportSection;
import net.yozmnews.report.domain.content.ReportSectionRepository;
import net.yozmnews.report.domain.generator.ReportGenerator;
import net.yozmnews.report.domain.generator.ReportOutcome;
import net.yozmnews.report.domain.generator.ReportRequest;
import net.yozmnews.report.domain.generator.ReportVerdict;
import net.yozmnews.report.domain.generator.TokenUsage;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.output.CitedReport;
import net.yozmnews.report.domain.output.ReportCitationCheck;
import net.yozmnews.report.domain.output.ReportOutputSchema;
import net.yozmnews.report.domain.prompt.FittedInput;
import net.yozmnews.report.domain.prompt.FittedInput.Trimmed;
import net.yozmnews.report.domain.prompt.ReportInputBudget;
import net.yozmnews.report.domain.prompt.ReportInputFit;
import net.yozmnews.report.domain.prompt.ReportPrompt;
import net.yozmnews.report.domain.prompt.ReportPrompts;
import net.yozmnews.report.domain.report.Completion;
import net.yozmnews.report.domain.report.Report;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.report.domain.report.ScheduledReport;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리포트 생성 유스케이스 — 예약과 생성 · docs/specs/report.md §4 · report-R13
 * <p>
 * 생성은 입력 맞추기(트랜잭션 밖) · 시작(트랜잭션 ①) · 프롬프트 · 생성기 호출과 판정 · 한 번 다시 묻기(트랜잭션 밖) · 대조 · 저장(트랜잭션 ②)으로
 * 나뉘고, 두 트랜잭션은 리포트 행을 먼저 잠근다. 트랜잭션 ① 은 생성을 기다리는 리포트만 시작한다 — 같은 리포트의 생성은 한 실행이 하고, 그 실행의
 * 생성기 호출은 다시 묻기를 더해 많아야 둘이다. 별칭 · 서술 · 프롬프트는 맞춘 입력으로, 사용자 · 기간 대조와 스냅숏은 맞추기 전 입력으로 한다.
 * 트랜잭션 ② 는 리포트의 버전이 트랜잭션 ① 뒤의 값과 다르거나 이미 끝났으면 아무것도 쓰지 않는다. 생성기는 생성자로 받는다 — profile
 * {@code local} 은 로컬 대체물, 그 밖은 실제 생성기다. 트랜잭션 안에서 부르면 거부한다.
 */
@Service
public class ReportGeneration {

	/** 트랜잭션 안에서 불렀을 때의 예외 메시지 */
	static final String IN_TRANSACTION = "리포트 생성을 트랜잭션 안에서 불렀다 — 생성기 호출은 트랜잭션 밖에서 한다";

	private static final Logger logger = LoggerFactory.getLogger(ReportGeneration.class);

	private final ReportRepository reports;

	private final ReportSectionRepository sections;

	private final ReportEndings endings;

	private final ReportGenerator generator;

	private final TransactionTemplate transaction;

	private final AppClock clock;

	ReportGeneration(ReportRepository reports, ReportSectionRepository sections, ReportEndings endings,
			ReportGenerator generator, TransactionTemplate transaction, AppClock clock) {
		this.reports = Objects.requireNonNull(reports, "reports");
		this.sections = Objects.requireNonNull(sections, "sections");
		this.endings = Objects.requireNonNull(endings, "endings");
		this.generator = Objects.requireNonNull(generator, "generator");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 생성을 기다리는 리포트를 새 등록 토큰으로 만든다 — 같은 사용자 · 종류 · 기간의 리포트는 하나다(저장소의 유일 제약이 막는다 — 어기면 예외).
	 * 생성 작업은 부르는 쪽이 커밋 뒤 그 토큰으로 등록한다.
	 */
	public ScheduledReport schedule(UserId user, ReportPeriod period) {
		UUID token = UUID.randomUUID();
		return new ScheduledReport(this.reports.save(Report.schedule(user, period, token, this.clock.instant())).id(),
				token);
	}

	/**
	 * 입력 맞추기 → 시작 → 갈래가 있으면 프롬프트 · 생성 · 판정(한 번 다시 묻기) → 대조 → 저장. 끝났거나 생성 중인 리포트면 아무것도
	 * 하지 않는다. 토큰은 공급자가 답한 호출의 합이고, 소요 시간은 첫 호출 시작부터 마지막 호출 끝까지다.
	 * @param model 쓸 모델 — 입력 예산 · 구조화 출력 여부를 함께 정한다
	 * @param effort 추론 강도 — 사용자 설정
	 * @return 이 실행 뒤 그 리포트의 상태 — 다른 실행이 생성 중이면 {@link ReportStatus#RUNNING}
	 * @throws IllegalArgumentException 없는 리포트일 때, 입력의 사용자 · 기간이 리포트 행과 다를 때 — 아무것도 쓰지 않는다
	 * @throws IllegalStateException 트랜잭션 안에서 불렀을 때 — 아무것도 쓰지 않는다
	 */
	public ReportStatus run(ReportId id, LlmModel model, ReasoningEffort effort, ReportInput input) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(effort, "effort");
		Objects.requireNonNull(input, "input");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
		int budget = ReportInputBudget.userContent(model, input.period().kind());
		FittedInput fitted = ReportInputFit.fit(input, budget);
		ReportInput sent = fitted.input();
		ReportAliases aliases = ReportAliases.of(sent);
		OutputShape.Obj root = ReportOutputSchema.root(aliases);
		Started started = Objects.requireNonNull(
				this.transaction.execute(status -> start(id, model, input, aliases)));
		if (started.finished() != null) {
			return started.finished();
		}
		if (fitted.trimmedAnything() || fitted.overBudget()) {
			Trimmed trimmed = fitted.trimmed();
			logger.info(
					"리포트 입력을 예산에 맞춰 덜어 냈다 — 리포트 {}, 예산 {}, 직전 글 자름 {}, 관심 줄의 정리 뺌 {}, 사건 줄 뺌 {}, 흐름 요약 뺌 {}, 사용자 스토리라인 뺌 {}, 예산을 넘긴 채 {}",
					id.value(), budget, trimmed.priorsCut(), trimmed.userStorylineSummariesDropped(), trimmed.eventLinesDropped(),
					trimmed.userStorylineOverviewsDropped(), trimmed.userStorylinesDropped(), fitted.overBudget());
		}
		ReportPrompt prompt = ReportPrompts.compose(sent, aliases, model);
		Instant asked = this.clock.instant();
		ReportOutcome first = this.generator.generate(new ReportRequest(started.user(), model, effort, aliases, prompt));
		ReportOutcome second = null;
		ReportVerdict.Settled settled = switch (ReportVerdict.judge(first, root, aliases)) {
			case ReportVerdict.Settled done -> done;
			case ReportVerdict.Retry retry -> {
				logger.info("리포트 생성을 다시 물었다 — 리포트 {}, 까닭 읽지 못함", id.value());
				second = this.generator.generate(new ReportRequest(started.user(), model, effort, aliases,
						ReportPrompts.retry(prompt, retry.diagnosis())));
				yield ReportVerdict.settle(second, root);
			}
			case ReportVerdict.RetryRewrite retry -> {
				logger.info("리포트 생성을 다시 물었다 — 리포트 {}, 까닭 빠진 갈래 {} · 남은 별칭 {}", id.value(), retry.shortfall().missing(),
						retry.shortfall().leftAliases());
				second = this.generator.generate(new ReportRequest(started.user(), model, effort, aliases,
						ReportPrompts.retry(prompt, retry.diagnosis())));
				yield ReportVerdict.settle(second, root, aliases, retry.first());
			}
		};
		TokenUsage usage = sum(usage(first), (second == null) ? null : usage(second));
		Duration took = this.clock.elapsedSince(asked);
		return finish(id, started.version(), settled, aliases, usage, took);
	}

	/** 공급자가 센 토큰 — 공급자가 답하지 못한 호출은 없다 */
	private static @Nullable TokenUsage usage(ReportOutcome outcome) {
		return switch (outcome) {
			case ReportOutcome.Generated generated -> generated.usage();
			case ReportOutcome.Failed failed -> null;
		};
	}

	/** 두 호출의 토큰 합 — 한쪽만 있으면 그쪽, 둘 다 없으면 없음 */
	private static @Nullable TokenUsage sum(@Nullable TokenUsage a, @Nullable TokenUsage b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return new TokenUsage(a.inputTokens() + b.inputTokens(), a.outputTokens() + b.outputTokens());
	}

	private Started start(ReportId id, LlmModel model, ReportInput input, ReportAliases aliases) {
		Report report = lock(id);
		if (!report.userId().equals(input.user()) || !report.period().equals(input.period())) {
			// 다른 사용자 · 기간의 재료로 이 행의 사용자 키를 부르지 않는다 — 아무것도 쓰지 않고 롤백한다(report-R13)
			throw new IllegalArgumentException("입력의 사용자 · 기간이 리포트 행과 다르다: 리포트 " + id.value());
		}
		if (report.status() != ReportStatus.SCHEDULED) {
			// 끝났거나 다른 실행이 생성 중이다 — 다시 시작하지 않는다
			return new Started(report.status(), report.userId(), 0);
		}
		this.reports.writeStart(id.value(), input.statsSnapshot(),
				(input.previous() == null) ? null : input.previous().value(), input.zone().getId());
		Report running = this.reports.save(report.start(model, this.clock.instant()));
		if (aliases.hasNoSections()) {
			Report empty = this.endings.save(running.complete(new Completion.Empty(), this.clock.instant()));
			return new Started(empty.status(), empty.userId(), 0);
		}
		return new Started(null, running.userId(), running.version().orElseThrow());
	}

	/** 리포트 행을 잠그고, 시작 뒤 그대로면 판정대로 끝낸다 */
	private ReportStatus finish(ReportId id, long version, ReportVerdict.Settled verdict, ReportAliases aliases,
			@Nullable TokenUsage usage, Duration took) {
		return Objects.requireNonNull(this.transaction.execute(status -> {
			Report report = lock(id);
			if (report.status().finished() || !report.version().equals(Optional.of(version))) {
				return report.status();
			}
			Completion completion = switch (verdict) {
				case ReportVerdict.Fail fail -> new Completion.Failed(fail.failure(), fail.message(), usage, took);
				case ReportVerdict.Read read -> {
					CitedReport cited = ReportCitationCheck.check(read.output().draft(), aliases);
					int leftAliases = ReportCitationCheck.leftAliases(read.output().draft(), aliases).size();
					if (cited.missingSections() > 0 || cited.unknownRefs() > 0 || read.output().overLimit() > 0 || leftAliases > 0) {
						logger.info("리포트 출력에서 버리거나 남은 것이 있다 — 리포트 {}, 빠진 섹션 {}, 모르는 별칭 {}, 상한으로 버린 수 {}, 글에 남은 별칭 {}",
								id.value(), cited.missingSections(), cited.unknownRefs(), read.output().overLimit(), leftAliases);
					}
					Optional<ReportVerdict.Fail> empty = ReportVerdict.withoutSections(cited);
					if (empty.isPresent()) {
						yield new Completion.Failed(empty.get().failure(), empty.get().message(), usage, took);
					}
					this.sections.saveAll(ReportSection.of(id, cited));
					yield new Completion.Ready(Objects.requireNonNull(usage, "읽은 응답은 공급자가 센 토큰이 있다"), took);
				}
			};
			return this.endings.save(report.complete(completion, this.clock.instant())).status();
		}));
	}

	private Report lock(ReportId id) {
		return this.reports.lock(id.value())
			.orElseThrow(() -> new IllegalArgumentException("없는 리포트다: " + id.value()));
	}

	/**
	 * 트랜잭션 ① 의 결과 — 끝났거나 다른 실행이 생성 중이면 그 상태, 아니면 생성기를 부를 사용자 · 시작 뒤 버전
	 */
	private record Started(@Nullable ReportStatus finished, UserId user, long version) {
	}

}
