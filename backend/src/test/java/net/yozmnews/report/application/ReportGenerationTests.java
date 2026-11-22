package net.yozmnews.report.application;

import static net.yozmnews.report.ReportFixtures.event;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.section;
import static net.yozmnews.report.ReportFixtures.storyline;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import net.yozmnews.TestClocks;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.adapter.out.fake.FakeReportGenerator;
import net.yozmnews.report.domain.content.ReportSectionRepository;
import net.yozmnews.report.domain.generator.ReportGenerator;
import net.yozmnews.report.domain.generator.ReportOutcome;
import net.yozmnews.report.domain.generator.ReportRequest;
import net.yozmnews.report.domain.generator.TokenUsage;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.report.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 리포트 생성 흐름 — 예약 · 시작과 스냅숏 · 생성 · 판정 · 대조 · 저장이 실제 PostgreSQL 위에서 끝까지 간다. 갈래가 없으면 EMPTY, 생성기가 없으면
 * NO_GENERATOR, 실패 · 잘림 · 읽지 못함은 그 코드로 끝난다.
 * <p>
 * 지키는 규칙: report-R1 · report-R9 · report-R13
 * <p>
 * 시험을 트랜잭션으로 감싸지 않는다 — 유스케이스가 트랜잭션을 둘로 나눈다. 생성기 · 시계는 유스케이스의 패키지 생성자로 넣는다(대본 생성기 · 로컬
 * 대체물). 컨텍스트의 빈은 시험 profile 에 {@code local} 이 없어 생성기가 없다. 기대값은 손으로 쓴 리터럴이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ReportGenerationTests {

	private static final Instant T0 = Instant.parse("2026-11-23T00:30:00Z");

	private static final ReportPeriod WEEK = new ReportPeriod(ReportPeriodKind.WEEK,
			Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-23T00:00:00Z"));

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	ReportRepository reports;

	@Autowired
	ReportSectionRepository sections;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ReportGeneration contextGeneration;

	private final AtomicReference<Instant> now = new AtomicReference<>(T0);

	@BeforeEach
	void clear() {
		this.jdbc.sql("delete from reports").update();
	}

	@Test
	void savesTheOverviewSectionsAndCitationsOfAReadyReport() {
		ReportId id = generation(new FakeReportGenerator()).schedule(new UserId(7), WEEK);
		ReportInput input = input(section(ReportSectionKind.STORYLINES, storyline(31), event(902)),
				section(ReportSectionKind.INTERESTS), section(ReportSectionKind.COVERAGE, event(903), storyline(31)));

		ReportStatus status = generation(new FakeReportGenerator()).run(id, ReportFixtures.MODEL, input);

		assertThat(status).isEqualTo(ReportStatus.READY);
		Map<String, Object> row = row(id);
		assertThat(row).containsEntry("user_id", 7L)
			.containsEntry("period_kind", "WEEK")
			.containsEntry("status", "READY")
			.containsEntry("provider", "openai")
			.containsEntry("model", "gpt-5.5-mini")
			.containsEntry("input_tokens", 0)
			.containsEntry("output_tokens", 0)
			.containsEntry("duration_ms", 0L)
			.containsEntry("error_code", null);
		assertThat(instant(id, "started_at")).isEqualTo(T0);
		assertThat(instant(id, "completed_at")).isEqualTo(T0);
		assertThat(JSON.readTree((String) row.get("snapshot")))
			.isEqualTo(JSON.readTree("{\"coverage\": {\"직전\": 7, \"이번\": 12}}"));
		assertThat(sectionRows(id)).containsExactly("0 OVERVIEW null 로컬 대체물이 만든 리포트다. 섹션 3개.",
				"1 STORYLINES 로컬 섹션 1 로컬 대체물이 만든 본문이다.", "2 INTERESTS 로컬 섹션 2 로컬 대체물이 만든 본문이다.",
				"3 COVERAGE 로컬 섹션 3 로컬 대체물이 만든 본문이다.");
		assertThat(itemRows(id)).containsExactly("1:0 S31", "1:1 E902", "3:0 E903", "3:1 S31");
	}

	@Test
	void endsEmptyWithoutCallingTheGeneratorWhenTheInputHasNoSection() {
		AtomicInteger calls = new AtomicInteger();
		ReportGeneration generation = generation(scripted(calls, request -> "{}"));
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, input())).isEqualTo(ReportStatus.EMPTY);

		assertThat(calls).hasValue(0);
		assertThat(row(id)).containsEntry("status", "EMPTY")
			.containsEntry("provider", "openai")
			.containsEntry("input_tokens", null)
			.containsEntry("duration_ms", null);
		assertThat(JSON.readTree((String) row(id).get("snapshot")))
			.isEqualTo(JSON.readTree("{\"coverage\": {\"직전\": 7, \"이번\": 12}}"));
		assertThat(sectionRows(id)).isEmpty();
	}

	@Test
	void writesAReadyReportForASectionWithoutTargets() {
		ReportGeneration generation = generation(new FakeReportGenerator());
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, input(section(ReportSectionKind.INTERESTS))))
			.isEqualTo(ReportStatus.READY);

		assertThat(sectionRows(id)).containsExactly("0 OVERVIEW null 로컬 대체물이 만든 리포트다. 섹션 1개.",
				"1 INTERESTS 로컬 섹션 1 로컬 대체물이 만든 본문이다.");
		assertThat(itemRows(id)).isEmpty();
	}

	@Test
	void theContextUseCaseHasNoGeneratorOutsideLocalAndEndsWithNoGenerator() {
		ReportId id = this.contextGeneration.schedule(new UserId(7), WEEK);

		assertThat(this.contextGeneration.run(id, ReportFixtures.MODEL, input(section(ReportSectionKind.INTERESTS))))
			.isEqualTo(ReportStatus.FAILED);

		assertThat(row(id)).containsEntry("status", "FAILED")
			.containsEntry("error_code", "NO_GENERATOR")
			.containsEntry("error_message", "생성기가 없다")
			.containsEntry("input_tokens", null)
			.containsEntry("duration_ms", null);
		assertThat(row(id).get("started_at")).isNotNull();
		assertThat(JSON.readTree((String) row(id).get("snapshot")))
			.isEqualTo(JSON.readTree("{\"coverage\": {\"직전\": 7, \"이번\": 12}}"));
		assertThat(sectionRows(id)).isEmpty();
	}

	@Test
	void doesNothingForAFinishedReport() {
		AtomicInteger calls = new AtomicInteger();
		ReportGeneration generation = generation(new FakeReportGenerator());
		ReportId id = generation.schedule(new UserId(7), WEEK);
		generation.run(id, ReportFixtures.MODEL, input(section(ReportSectionKind.INTERESTS)));

		ReportGeneration again = generation(scripted(calls, request -> "{}"));
		assertThat(again.run(id, ReportFixtures.MODEL, input(section(ReportSectionKind.COVERAGE, event(1)))))
			.isEqualTo(ReportStatus.READY);

		assertThat(calls).hasValue(0);
		assertThat(sectionRows(id)).hasSize(2);
	}

	@Test
	void refusesToRunInsideATransactionWithoutWriting() {
		ReportGeneration generation = generation(new FakeReportGenerator());
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.transaction.executeWithoutResult(
					status -> generation.run(id, ReportFixtures.MODEL, input(section(ReportSectionKind.INTERESTS)))))
			.withMessage(ReportGeneration.IN_TRANSACTION);
		assertThat(row(id)).containsEntry("status", "SCHEDULED").containsEntry("snapshot", null);
	}

	@Test
	void refusesAReportThatDoesNotExist() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> generation(new FakeReportGenerator()).run(new ReportId(999_999), ReportFixtures.MODEL,
					input()))
			.withMessage("없는 리포트다: 999999");
	}

	@Test
	void writesNothingWhenTheReportChangedDuringGeneration() {
		AtomicInteger calls = new AtomicInteger();
		AtomicReference<ReportId> target = new AtomicReference<>();
		ReportGeneration generation = generation(scripted(calls, request -> {
			this.jdbc.sql("""
					update reports set status = 'FAILED', error_code = 'NO_GENERATOR', error_message = '다른 실행',
					completed_at = now(), version = version + 1 where id = :id""").param("id", target.get().value()).update();
			return "{\"overview\":\"개요\",\"sections\":{\"interests\":{\"title\":\"제목\",\"body\":\"본문\",\"refs\":[]}}}";
		}));
		ReportId id = generation.schedule(new UserId(7), WEEK);
		target.set(id);

		assertThat(generation.run(id, ReportFixtures.MODEL, input(section(ReportSectionKind.INTERESTS))))
			.isEqualTo(ReportStatus.FAILED);

		assertThat(calls).hasValue(1);
		assertThat(row(id)).containsEntry("error_message", "다른 실행");
		assertThat(sectionRows(id)).isEmpty();
	}

	@Test
	void writesNothingWhenOnlyTheVersionChangedDuringGeneration() {
		AtomicInteger calls = new AtomicInteger();
		AtomicReference<ReportId> target = new AtomicReference<>();
		ReportGeneration generation = generation(scripted(calls, request -> {
			this.jdbc.sql("update reports set version = version + 1 where id = :id")
				.param("id", target.get().value())
				.update();
			return "{\"overview\":\"개요\",\"sections\":{\"interests\":{\"title\":\"제목\",\"body\":\"본문\",\"refs\":[]}}}";
		}));
		ReportId id = generation.schedule(new UserId(7), WEEK);
		target.set(id);

		assertThat(generation.run(id, ReportFixtures.MODEL, input(section(ReportSectionKind.INTERESTS))))
			.isEqualTo(ReportStatus.RUNNING);

		assertThat(calls).hasValue(1);
		assertThat(row(id)).containsEntry("status", "RUNNING").containsEntry("completed_at", null);
		assertThat(sectionRows(id)).isEmpty();
	}

	@Test
	void leavesTheReportRunningWhenTheGeneratorThrows() {
		ReportGeneration generation = generation(request -> {
			throw new IllegalStateException("예상 밖의 생성기 오류");
		});
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThatIllegalStateException()
			.isThrownBy(() -> generation.run(id, ReportFixtures.MODEL, input(section(ReportSectionKind.INTERESTS))))
			.withMessage("예상 밖의 생성기 오류");

		assertThat(row(id)).containsEntry("status", "RUNNING")
			.containsEntry("completed_at", null)
			.containsEntry("error_code", null);
		assertThat(sectionRows(id)).isEmpty();
	}

	@Test
	void endsWithTheGeneratorFailureCodeWithoutTokensButWithTheDuration() {
		this.now.set(T0);
		ReportGeneration generation = generation(request -> {
			this.now.set(T0.plusMillis(1_500));
			return new ReportOutcome.Failed(ReportFailure.OUTPUT_REFUSED, "공급자 문구");
		});
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, input(section(ReportSectionKind.INTERESTS))))
			.isEqualTo(ReportStatus.FAILED);

		assertThat(row(id)).containsEntry("error_code", "OUTPUT_REFUSED")
			.containsEntry("error_message", "공급자 문구")
			.containsEntry("input_tokens", null)
			.containsEntry("duration_ms", 1_500L);
	}

	@Test
	void endsUnreadableWithTheDiagnosisAndTheTokens() {
		AtomicInteger calls = new AtomicInteger();
		ReportGeneration generation = generation(scripted(calls, request -> "{\"overview\":\"개요\"}"));
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, input(section(ReportSectionKind.INTERESTS))))
			.isEqualTo(ReportStatus.FAILED);

		assertThat(row(id)).containsEntry("error_code", "UNREADABLE_OUTPUT")
			.containsEntry("error_message", "$.sections: 없다")
			.containsEntry("input_tokens", 11)
			.containsEntry("output_tokens", 22);
		assertThat(sectionRows(id)).isEmpty();
	}

	@Test
	void dropsUnknownAliasesKeepsTheReportAndLogsWhatWasDropped(CapturedOutput output) {
		AtomicInteger calls = new AtomicInteger();
		ReportGeneration generation = generation(scripted(calls, request -> """
				{"overview":"개요","sections":{"storylines":{"title":"흐름","body":"본문","refs":["S1","Z9",""]}}}"""));
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL,
				input(section(ReportSectionKind.STORYLINES, storyline(31)), section(ReportSectionKind.COVERAGE))))
			.isEqualTo(ReportStatus.READY);

		assertThat(sectionRows(id)).containsExactly("0 OVERVIEW null 개요", "1 STORYLINES 흐름 본문");
		assertThat(itemRows(id)).containsExactly("1:0 S31");
		assertThat(output).contains("리포트 출력에서 버린 것이 있다 — 리포트 " + id.value() + ", 빠진 섹션 1, 모르는 별칭 2, 상한으로 버린 수 0")
			.doesNotContain("Z9");
	}

	private ReportGeneration generation(ReportGenerator generator) {
		return new ReportGeneration(this.reports, this.sections, () -> generator, this.transaction,
				TestClocks.of(() -> this.now.get()));
	}

	private static ReportGenerator scripted(AtomicInteger calls, Function<ReportRequest, String> text) {
		return request -> {
			calls.incrementAndGet();
			return new ReportOutcome.Generated(text.apply(request), new TokenUsage(11, 22), GenerationStop.COMPLETE);
		};
	}

	private Map<String, Object> row(ReportId id) {
		return this.jdbc.sql("""
				select user_id, period_kind, status, provider, model, input_tokens, output_tokens, duration_ms, error_code,
				       error_message, started_at, completed_at, stats_snapshot::text as snapshot
				from reports where id = :id""").param("id", id.value()).query().singleRow();
	}

	private Instant instant(ReportId id, String column) {
		return this.jdbc.sql("select " + column + " from reports where id = :id")
			.param("id", id.value())
			.query((rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant())
			.single();
	}

	private List<String> sectionRows(ReportId id) {
		return this.jdbc.sql("""
				select position || ' ' || kind || ' ' || coalesce(title, 'null') || ' ' || body
				from report_sections where report_id = :id order by position""")
			.param("id", id.value())
			.query(String.class)
			.list();
	}

	private List<String> itemRows(ReportId id) {
		return this.jdbc.sql("""
				select s.position || ':' || i.position || ' '
				       || coalesce('S' || i.storyline_id, 'E' || i.event_id)
				from report_items i join report_sections s on s.id = i.report_section_id
				where s.report_id = :id order by s.position, i.position""")
			.param("id", id.value())
			.query(String.class)
			.list();
	}

}
