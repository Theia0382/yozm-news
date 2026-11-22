package net.yozmnews.report.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.OutputShape;
import net.yozmnews.platform.StructuredOutputSchema;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFailure;
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
import net.yozmnews.report.domain.report.Completion;
import net.yozmnews.report.domain.report.Report;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.report.ReportRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리포트 생성 유스케이스 — 예약과 생성 · docs/specs/report.md §4 · report-R13
 * <p>
 * 생성은 시작(트랜잭션 ①) · 생성기 호출과 판정(트랜잭션 밖) · 대조 · 저장(트랜잭션 ②)으로 나뉘고, 두 트랜잭션은 리포트 행을 먼저 잠근다. 트랜잭션 ①
 * 은 생성을 기다리는 리포트만 시작한다 — 같은 리포트의 생성기 호출은 하나다. 트랜잭션 ② 는 리포트의 버전이 트랜잭션 ① 뒤의 값과 다르거나 이미
 * 끝났으면 아무것도 쓰지 않는다. 생성기는 생성할 때 찾는다 — profile {@code local} 은 로컬 대체물이 있고, 그 밖은 실제 생성기가 서기 전까지
 * 없다. 트랜잭션 안에서 부르면 거부한다.
 */
@Service
public class ReportGeneration {

	/** 생성기가 없을 때의 실패 메시지 */
	static final String NO_GENERATOR = "생성기가 없다";

	/** 트랜잭션 안에서 불렀을 때의 예외 메시지 */
	static final String IN_TRANSACTION = "리포트 생성을 트랜잭션 안에서 불렀다 — 생성기 호출은 트랜잭션 밖에서 한다";

	private static final Logger logger = LoggerFactory.getLogger(ReportGeneration.class);

	private final ReportRepository reports;

	private final ReportSectionRepository sections;

	private final Supplier<@Nullable ReportGenerator> generator;

	private final TransactionTemplate transaction;

	private final AppClock clock;

	@Autowired
	ReportGeneration(ReportRepository reports, ReportSectionRepository sections,
			ObjectProvider<ReportGenerator> generators, TransactionTemplate transaction, AppClock clock) {
		this(reports, sections, generators::getIfAvailable, transaction, clock);
	}

	ReportGeneration(ReportRepository reports, ReportSectionRepository sections,
			Supplier<@Nullable ReportGenerator> generator, TransactionTemplate transaction, AppClock clock) {
		this.reports = Objects.requireNonNull(reports, "reports");
		this.sections = Objects.requireNonNull(sections, "sections");
		this.generator = Objects.requireNonNull(generator, "generator");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 생성을 기다리는 리포트를 만든다 — 같은 사용자 · 기간의 리포트를 막지 않는다(기간마다 한 번의 제약은 일정을 만드는 작업이 정한다).
	 */
	public ReportId schedule(UserId user, ReportPeriod period) {
		return this.reports.save(Report.schedule(user, period, this.clock.instant())).id();
	}

	/**
	 * 시작 → 갈래가 있고 생성기가 있으면 생성 · 판정 → 대조 → 저장. 끝났거나 생성 중인 리포트면 아무것도 하지 않는다.
	 * @return 이 실행 뒤 그 리포트의 상태 — 다른 실행이 생성 중이면 {@link ReportStatus#RUNNING}
	 * @throws IllegalArgumentException 없는 리포트일 때
	 * @throws IllegalStateException 트랜잭션 안에서 불렀을 때 — 아무것도 쓰지 않는다
	 */
	public ReportStatus run(ReportId id, LlmModel model, ReportInput input) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(input, "input");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
		ReportAliases aliases = ReportAliases.of(input);
		OutputShape.Obj root = ReportOutputSchema.root(aliases);
		Started started = Objects.requireNonNull(
				this.transaction.execute(status -> start(id, model, input.statsSnapshot(), aliases)));
		if (started.finished() != null) {
			return started.finished();
		}
		ReportGenerator found = Objects.requireNonNull(started.generator(), "generator");
		Instant asked = this.clock.instant();
		ReportOutcome outcome = found
			.generate(new ReportRequest(started.user(), model, aliases, StructuredOutputSchema.json(root)));
		Duration took = this.clock.elapsedSince(asked);
		ReportVerdict verdict = ReportVerdict.judge(outcome, root);
		TokenUsage usage = switch (outcome) {
			case ReportOutcome.Generated generated -> generated.usage();
			case ReportOutcome.Failed failed -> null;
		};
		return finish(id, started.version(), verdict, aliases, usage, took);
	}

	private Started start(ReportId id, LlmModel model, String snapshot, ReportAliases aliases) {
		Report report = lock(id);
		if (report.status() != ReportStatus.SCHEDULED) {
			// 끝났거나 다른 실행이 생성 중이다 — 다시 시작하지 않는다
			return new Started(report.status(), report.userId(), 0, null);
		}
		this.reports.writeSnapshot(id.value(), snapshot);
		Report running = this.reports.save(report.start(model, this.clock.instant()));
		if (aliases.hasNoSections()) {
			Report empty = this.reports.save(running.complete(new Completion.Empty(), this.clock.instant()));
			return new Started(empty.status(), empty.userId(), 0, null);
		}
		ReportGenerator found = this.generator.get();
		if (found == null) {
			Report failed = this.reports.save(running
				.complete(new Completion.Failed(ReportFailure.NO_GENERATOR, NO_GENERATOR, null, null), this.clock.instant()));
			return new Started(failed.status(), failed.userId(), 0, null);
		}
		return new Started(null, running.userId(), running.version().orElseThrow(), found);
	}

	/** 리포트 행을 잠그고, 시작 뒤 그대로면 판정대로 끝낸다 */
	private ReportStatus finish(ReportId id, long version, ReportVerdict verdict, ReportAliases aliases,
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
					if (cited.missingSections() > 0 || cited.unknownRefs() > 0 || read.output().overLimit() > 0) {
						logger.info("리포트 출력에서 버린 것이 있다 — 리포트 {}, 빠진 섹션 {}, 모르는 별칭 {}, 상한으로 버린 수 {}", id.value(),
								cited.missingSections(), cited.unknownRefs(), read.output().overLimit());
					}
					this.sections.saveAll(ReportSection.of(id, cited));
					yield new Completion.Ready(Objects.requireNonNull(usage, "읽은 응답은 공급자가 센 토큰이 있다"), took);
				}
			};
			return this.reports.save(report.complete(completion, this.clock.instant())).status();
		}));
	}

	private Report lock(ReportId id) {
		return this.reports.lock(id.value())
			.orElseThrow(() -> new IllegalArgumentException("없는 리포트다: " + id.value()));
	}

	/**
	 * 트랜잭션 ① 의 결과 — 끝났거나 다른 실행이 생성 중이면 그 상태, 아니면 생성기를 부를 사용자 · 시작 뒤 버전 · 찾은 생성기
	 */
	private record Started(@Nullable ReportStatus finished, UserId user, long version,
			@Nullable ReportGenerator generator) {
	}

}
