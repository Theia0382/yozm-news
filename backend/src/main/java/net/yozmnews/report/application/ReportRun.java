package net.yozmnews.report.application;

import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.report.Report;
import net.yozmnews.report.domain.report.ReportLedger;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.report.domain.schedule.ReportScheduleRules;
import net.yozmnews.user.ReportModel;
import net.yozmnews.user.ReportModelDirectory;
import net.yozmnews.user.UserTimezones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 생성 작업의 유스케이스 — 리포트 행 하나를 읽어 쓸 모델 · 추론 강도를 정하고, 재료를 모으고, 생성한다. docs/specs/report.md §4
 * <p>
 * 생성을 기다리는 리포트만 진행한다 — 끝났거나 생성 중이면 그 상태를 돌려준다. 쓸 모델이 없거나 계정이 없어 재료를 모으지 못하면 생성기를 부르지
 * 않고 시작하지 않은 채 {@link ReportFailure#NO_USABLE_MODEL} 로 끝낸다 — 그때의 사용자 시간대(계정이 없으면
 * {@link ReportPeriod#NO_ACCOUNT_ZONE})를 행에 먼저 쓴다. 월간이면 그 기간 안에 다 든 READY 주간 리포트를 입력으로 싣는다. 트랜잭션 밖에서
 * 부른다 — 생성이 그렇다. 역할 애너테이션이 없다(부르는 작업이 작업 역할이다).
 */
@Service
public class ReportRun {

	/** 쓸 모델이 없을 때의 실패 메시지 */
	static final String NO_MODEL = "쓸 모델이 없다 — 유효한 키가 있는 공급자의 허용 모델이 없거나 계정을 쓸 수 없다";

	/** 재료를 모으지 못했을 때(계정이 없다)의 실패 메시지 */
	static final String NO_ACCOUNT = "계정이 없어 재료를 모으지 못했다";

	private final ReportRepository reports;

	private final ReportLedger ledger;

	private final ReportModelDirectory models;

	private final UserTimezones timezones;

	private final ReportMaterialCollection collection;

	private final ReportGeneration generation;

	private final ReportEndings endings;

	private final TransactionTemplate transaction;

	private final InstantSource clock;

	ReportRun(ReportRepository reports, ReportLedger ledger, ReportModelDirectory models, UserTimezones timezones,
			ReportMaterialCollection collection, ReportGeneration generation, ReportEndings endings,
			TransactionTemplate transaction, InstantSource clock) {
		this.reports = Objects.requireNonNull(reports, "reports");
		this.ledger = Objects.requireNonNull(ledger, "ledger");
		this.models = Objects.requireNonNull(models, "models");
		this.timezones = Objects.requireNonNull(timezones, "timezones");
		this.collection = Objects.requireNonNull(collection, "collection");
		this.generation = Objects.requireNonNull(generation, "generation");
		this.endings = Objects.requireNonNull(endings, "endings");
		this.transaction = Objects.requireNonNull(transaction, "transaction");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * @return 이 실행 뒤 그 리포트의 상태
	 * @throws IllegalArgumentException 없는 리포트일 때
	 */
	public ReportStatus run(ReportId id) {
		Objects.requireNonNull(id, "id");
		Report report = this.reports.findById(id.value())
			.orElseThrow(() -> new IllegalArgumentException("없는 리포트다: " + id.value()));
		if (report.status() != ReportStatus.SCHEDULED) {
			return report.status();
		}
		Optional<ReportModel> model = this.models.modelOf(report.userId());
		if (model.isEmpty()) {
			return abandon(id, NO_MODEL);
		}
		ReportPeriod period = report.period();
		List<ReportId> weeklies = (period.kind() == ReportPeriodKind.MONTH)
				? ReportScheduleRules.monthlyInputs(
						this.ledger.weekliesOverlapping(report.userId(), period.start(), period.end()), period)
				: List.of();
		Optional<ReportInput> input = this.collection.collect(report.userId(), period, weeklies);
		if (input.isEmpty()) {
			return abandon(id, NO_ACCOUNT);
		}
		return this.generation.run(id, model.get().model(), model.get().effort(), input.get());
	}

	/**
	 * 생성을 기다리는 리포트면 생성기를 부르지 않고 시작하지 않은 채 실패로 끝낸다 — 시간대를 먼저 쓴다(끝난 행은 시간대가 있다). 그사이 다른
	 * 실행이 시작했거나 끝냈으면 그 상태
	 */
	private ReportStatus abandon(ReportId id, String message) {
		return Objects.requireNonNull(this.transaction.execute(status -> {
			Report locked = this.reports.lock(id.value())
				.orElseThrow(() -> new IllegalArgumentException("없는 리포트다: " + id.value()));
			if (locked.status() != ReportStatus.SCHEDULED) {
				return locked.status();
			}
			this.reports.writeZone(id.value(),
					this.timezones.timezoneOf(locked.userId()).orElse(ReportPeriod.NO_ACCOUNT_ZONE).getId());
			return this.endings
				.save(locked.abandon(ReportFailure.NO_USABLE_MODEL, message, this.clock.instant()))
				.status();
		}));
	}

}
