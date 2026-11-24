package net.yozmnews.report.application;

import static net.yozmnews.report.ReportFixtures.coverage;
import static net.yozmnews.report.ReportFixtures.flow;
import static net.yozmnews.report.ReportFixtures.flows;
import static net.yozmnews.report.ReportFixtures.input;
import static net.yozmnews.report.ReportFixtures.interests;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import net.yozmnews.TestClocks;
import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportFixtures;
import net.yozmnews.report.ReportId;
import net.yozmnews.report.ReportPeriodKind;
import net.yozmnews.report.ReportStatus;
import net.yozmnews.report.adapter.out.fake.FakeReportGenerator;
import net.yozmnews.report.domain.content.ReportSectionRepository;
import net.yozmnews.report.domain.generator.ReportGenerator;
import net.yozmnews.report.domain.generator.ReportOutcome;
import net.yozmnews.report.domain.generator.ReportRequest;
import net.yozmnews.report.domain.generator.TokenUsage;
import net.yozmnews.report.domain.input.Count;
import net.yozmnews.report.domain.input.FlowMaterial;
import net.yozmnews.report.domain.input.FlowSummary;
import net.yozmnews.report.domain.input.ReportInput;
import net.yozmnews.report.domain.input.StorylinesSection;
import net.yozmnews.report.domain.report.ReportPeriod;
import net.yozmnews.report.domain.report.ReportRepository;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineState;
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
 * 리포트 생성 흐름 — 예약 · 입력 맞추기 · 시작과 스냅숏 · 프롬프트 · 생성 · 판정과 한 번 다시 묻기 · 대조 · 저장이 실제 PostgreSQL 위에서
 * 끝까지 간다. 갈래가 없으면 EMPTY, 실패 · 잘림 · 다시 물어도 읽지 못함은 그 코드로 끝난다. 입력의 사용자 ·
 * 기간이 리포트 행과 다르면 아무것도 쓰지 않고 거부한다. 요청은 추론 강도 · 맞춘 입력의 별칭 · 프롬프트를 싣는다.
 * <p>
 * 지키는 규칙: report-R1 · report-R8 · report-R9 · report-R13 · report-R22
 * <p>
 * 시험을 트랜잭션으로 감싸지 않는다 — 유스케이스가 트랜잭션을 둘로 나눈다. 생성기 · 시계는 유스케이스의 패키지 생성자로 넣는다(대본 생성기 · 로컬
 * 대체물). 컨텍스트의 빈은 시험 profile 에 {@code local} 이 없어 실제 생성기다. 기대값은 손으로 쓴 리터럴이다.
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
		ReportInput input = input(flows(flow(31, 902)), interests(31), coverage());

		ReportStatus status = generation(new FakeReportGenerator()).run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input);

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
			.containsEntry("error_code", null)
			.containsEntry("zone", "Asia/Seoul")
			.containsEntry("previous_report_id", null);
		assertThat(instant(id, "started_at")).isEqualTo(T0);
		assertThat(instant(id, "completed_at")).isEqualTo(T0);
		assertThat(JSON.readTree((String) row.get("snapshot")))
			.isEqualTo(JSON.readTree("{\"coverage\": {\"직전\": 7, \"이번\": 12}}"));
		assertThat(sectionRows(id)).containsExactly("0 OVERVIEW null 로컬 대체물이 만든 리포트다. 섹션 3개.",
				"1 STORYLINES 로컬 섹션 1 로컬 대체물이 만든 본문이다.", "2 INTERESTS 로컬 섹션 2 로컬 대체물이 만든 본문이다.",
				"3 COVERAGE 로컬 섹션 3 로컬 대체물이 만든 본문이다.");
		// 다른 갈래에 다시 든 흐름(S31)은 같은 별칭으로 인용되고, 구독 주제 보도량은 인용이 없다
		assertThat(itemRows(id)).containsExactly("1:0 S31", "1:1 E902", "2:0 S31");
	}

	@Test
	void recordsThePreviousReportOfTheInputWhenItStarts() {
		ReportGeneration generation = generation(new FakeReportGenerator());
		ReportId previous = generation.schedule(new UserId(7),
				new ReportPeriod(ReportPeriodKind.WEEK, WEEK.start().minus(Duration.ofDays(7)), WEEK.start()));
		ReportId withPrevious = generation.schedule(new UserId(7), WEEK);
		ReportId empty = generation.schedule(new UserId(7), WEEK);
		ReportInput base = input(interests());

		generation.run(withPrevious, ReportFixtures.MODEL, ReportFixtures.EFFORT, new ReportInput(base.user(),
				base.period(), base.zone(), base.sections(), base.priors(), base.statsSnapshot(), previous));
		generation.run(empty, ReportFixtures.MODEL, ReportFixtures.EFFORT,
				new ReportInput(base.user(), base.period(), base.zone(), List.of(), base.priors(), base.statsSnapshot(),
						previous));

		assertThat(row(withPrevious)).containsEntry("status", "READY")
			.containsEntry("previous_report_id", previous.value())
			.containsEntry("zone", "Asia/Seoul");
		// 갈래가 없어 바로 끝나는 시작도 함께 쓴다
		assertThat(row(empty)).containsEntry("status", "EMPTY")
			.containsEntry("previous_report_id", previous.value())
			.containsEntry("zone", "Asia/Seoul");
	}

	@Test
	void refusesAnInputOfAnotherUserOrPeriodWithoutWritingAnything() {
		ReportGeneration generation = generation(new FakeReportGenerator());
		ReportId id = generation.schedule(new UserId(7), WEEK);
		List<ReportInput> others = List.of(
				input(new UserId(8), WEEK, interests()),
				input(new UserId(7), new ReportPeriod(ReportPeriodKind.MONTH, WEEK.start(), WEEK.end()), interests()),
				input(new UserId(7), new ReportPeriod(ReportPeriodKind.WEEK, Instant.parse("2026-11-16T01:00:00Z"),
						WEEK.end()), interests()),
				input(new UserId(7), new ReportPeriod(ReportPeriodKind.WEEK, WEEK.start(),
						Instant.parse("2026-11-22T23:00:00Z")), interests()));

		for (ReportInput other : others) {
			assertThatIllegalArgumentException().as(other.user() + " " + other.period())
				.isThrownBy(() -> generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, other))
				.withMessageContaining("입력의 사용자 · 기간이 리포트 행과 다르다");
			// 아무것도 쓰지 않았다 — 예약 그대로, 스냅숏 · 시간대 없음
			assertThat(row(id)).containsEntry("status", "SCHEDULED")
				.containsEntry("snapshot", null)
				.containsEntry("zone", null);
		}
		// 대조 — 같은 사용자 · 기간이면 생성한다
		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(new UserId(7), WEEK, interests())))
			.isEqualTo(ReportStatus.READY);
	}

	@Test
	void endsEmptyWithoutCallingTheGeneratorWhenTheInputHasNoSection() {
		AtomicInteger calls = new AtomicInteger();
		ReportGeneration generation = generation(scripted(calls, request -> "{}"));
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input())).isEqualTo(ReportStatus.EMPTY);

		assertThat(calls).hasValue(0);
		assertThat(row(id)).containsEntry("status", "EMPTY")
			.containsEntry("zone", "Asia/Seoul")
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

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests())))
			.isEqualTo(ReportStatus.READY);

		assertThat(sectionRows(id)).containsExactly("0 OVERVIEW null 로컬 대체물이 만든 리포트다. 섹션 1개.",
				"1 INTERESTS 로컬 섹션 1 로컬 대체물이 만든 본문이다.");
		assertThat(itemRows(id)).isEmpty();
	}

	@Test
	void theContextUseCaseCallsTheRealGeneratorOutsideLocalWhichEndsKeyMissingWithoutAKey() {
		// 시험 profile 에 local 이 없어 컨텍스트의 생성기는 실제 생성기다 — 사용자 7 은 저장한 키가 없어 관문이 보내지 않는다
		ReportId id = this.contextGeneration.schedule(new UserId(7), WEEK);

		assertThat(this.contextGeneration.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests())))
			.isEqualTo(ReportStatus.FAILED);

		assertThat(row(id)).containsEntry("status", "FAILED")
			.containsEntry("error_code", "KEY_MISSING")
			.containsEntry("error_message", "쓸 모델 공급자의 저장한 키가 없다")
			.containsEntry("input_tokens", null);
		assertThat(row(id).get("duration_ms")).isNotNull();
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
		generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests()));

		ReportGeneration again = generation(scripted(calls, request -> "{}"));
		assertThat(again.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(coverage())))
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
					status -> generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests()))))
			.withMessage(ReportGeneration.IN_TRANSACTION);
		assertThat(row(id)).containsEntry("status", "SCHEDULED").containsEntry("snapshot", null);
	}

	@Test
	void refusesAReportThatDoesNotExist() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> generation(new FakeReportGenerator()).run(new ReportId(999_999), ReportFixtures.MODEL, ReportFixtures.EFFORT,
					input()))
			.withMessage("없는 리포트다: 999999");
	}

	@Test
	void writesNothingWhenTheReportChangedDuringGeneration() {
		AtomicInteger calls = new AtomicInteger();
		AtomicReference<ReportId> target = new AtomicReference<>();
		ReportGeneration generation = generation(scripted(calls, request -> {
			this.jdbc.sql("""
					update reports set status = 'FAILED', error_code = 'PROVIDER_UNEXPECTED', error_message = '다른 실행',
					completed_at = now(), version = version + 1 where id = :id""").param("id", target.get().value()).update();
			return "{\"overview\":\"개요\",\"sections\":{\"interests\":{\"title\":\"제목\",\"body\":\"본문\",\"refs\":[]}}}";
		}));
		ReportId id = generation.schedule(new UserId(7), WEEK);
		target.set(id);

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests())))
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

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests())))
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
			.isThrownBy(() -> generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests())))
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

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests())))
			.isEqualTo(ReportStatus.FAILED);

		assertThat(row(id)).containsEntry("error_code", "OUTPUT_REFUSED")
			.containsEntry("error_message", "공급자 문구")
			.containsEntry("input_tokens", null)
			.containsEntry("duration_ms", 1_500L);
	}

	@Test
	void asksOnceMoreAndEndsUnreadableWithTheDiagnosisAndTheTokensOfBothCalls(CapturedOutput output) {
		AtomicInteger calls = new AtomicInteger();
		List<ReportRequest> requests = new ArrayList<>();
		ReportGeneration generation = generation(scripted(calls, request -> {
			requests.add(request);
			return "{\"overview\":\"개요\"}";
		}));
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests())))
			.isEqualTo(ReportStatus.FAILED);

		assertThat(calls).hasValue(2);
		assertThat(requests.get(1).prompt().instructions())
			.isEqualTo(requests.get(0).prompt().instructions() + "\n앞 출력이 출력 스키마에 맞지 않았다: $.sections: 없다"
					+ "\n출력 스키마에 맞는 JSON 객체 하나만 다시 출력하라.");
		assertThat(requests.get(1).prompt().userContent()).isEqualTo(requests.get(0).prompt().userContent());
		assertThat(row(id)).containsEntry("error_code", "UNREADABLE_OUTPUT")
			.containsEntry("error_message", "$.sections: 없다")
			.containsEntry("input_tokens", 22)
			.containsEntry("output_tokens", 44);
		assertThat(sectionRows(id)).isEmpty();
		assertThat(output).contains("리포트 생성을 다시 물었다 — 리포트 " + id.value() + ", 까닭 읽지 못함");
	}

	@Test
	void savesTheSecondReadingWhenTheAnswerAskedAgainCanBeRead() {
		AtomicInteger calls = new AtomicInteger();
		ReportGeneration generation = generation(scripted(calls, request -> (calls.get() == 1) ? "{\"overview\":\"개요\"}"
				: """
						{"overview":"다시 쓴 개요","sections":{"interests":{"title":"관심","body":"본문","refs":[]}}}"""));
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests())))
			.isEqualTo(ReportStatus.READY);

		assertThat(calls).hasValue(2);
		assertThat(sectionRows(id)).containsExactly("0 OVERVIEW null 다시 쓴 개요", "1 INTERESTS 관심 본문");
		assertThat(row(id)).containsEntry("input_tokens", 22).containsEntry("output_tokens", 44);
	}

	@Test
	void asksAgainForAMissingSectionAndKeepsTheReadingWithFewerMissing(CapturedOutput output) {
		AtomicInteger calls = new AtomicInteger();
		List<ReportRequest> requests = new ArrayList<>();
		ReportGeneration generation = generation(scripted(calls, request -> {
			requests.add(request);
			return (calls.get() == 1) ? """
					{"overview":"첫 개요","sections":{"coverage":{"title":"보도량","body":"본문","refs":[]}}}""" : """
					{"overview":"둘째 개요","sections":{"coverage":{"title":"보도량","body":"본문","refs":[]},\
					"interests":{"title":"관심","body":"본문","refs":[]}}}""";
		}));
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(coverage(), interests())))
			.isEqualTo(ReportStatus.READY);

		assertThat(requests.get(1).prompt().instructions()).endsWith(
				"앞 출력이 출력 스키마에 맞지 않았다: 출력에 없는 갈래: interests\n출력 스키마에 맞는 JSON 객체 하나만 다시 출력하라.");
		assertThat(sectionRows(id)).containsExactly("0 OVERVIEW null 둘째 개요", "1 COVERAGE 보도량 본문",
				"2 INTERESTS 관심 본문");
		assertThat(output).contains("리포트 생성을 다시 물었다 — 리포트 " + id.value() + ", 까닭 빠진 갈래 1");
	}

	@Test
	void keepsTheFirstReadingWhenTheAnswerAskedAgainForAMissingSectionIsTruncated() {
		AtomicInteger calls = new AtomicInteger();
		ReportGeneration generation = generation(request -> {
			calls.incrementAndGet();
			return (calls.get() == 1) ? new ReportOutcome.Generated("""
					{"overview":"첫 개요","sections":{"coverage":{"title":"보도량","body":"본문","refs":[]}}}""",
					new TokenUsage(11, 22), GenerationStop.COMPLETE)
					: new ReportOutcome.Generated("{", new TokenUsage(5, 7), GenerationStop.TRUNCATED);
		});
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(coverage(), interests())))
			.isEqualTo(ReportStatus.READY);

		assertThat(sectionRows(id)).containsExactly("0 OVERVIEW null 첫 개요", "1 COVERAGE 보도량 본문");
		assertThat(row(id)).containsEntry("input_tokens", 16).containsEntry("output_tokens", 29);
	}

	@Test
	void doesNotAskAgainForATruncatedAnswer() {
		AtomicInteger calls = new AtomicInteger();
		ReportGeneration generation = generation(request -> {
			calls.incrementAndGet();
			return new ReportOutcome.Generated("{", new TokenUsage(11, 22), GenerationStop.TRUNCATED);
		});
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT, input(interests())))
			.isEqualTo(ReportStatus.FAILED);

		assertThat(calls).hasValue(1);
		assertThat(row(id)).containsEntry("error_code", "OUTPUT_TRUNCATED")
			.containsEntry("input_tokens", 11)
			.containsEntry("output_tokens", 22);
	}

	@Test
	void theRequestCarriesTheEffortTheAliasesAndThePrompt() {
		List<ReportRequest> requests = new ArrayList<>();
		ReportGeneration generation = generation(scripted(new AtomicInteger(), request -> {
			requests.add(request);
			return """
					{"overview":"개요","sections":{"storylines":{"title":"흐름","body":"본문","refs":["S1"]}}}""";
		}));
		ReportId id = generation.schedule(new UserId(7), WEEK);

		generation.run(id, ReportFixtures.MODEL, ReasoningEffort.HIGH, input(flows(flow(31, 902))));

		assertThat(requests).singleElement().satisfies(request -> {
			assertThat(request.userId()).isEqualTo(new UserId(7));
			assertThat(request.effort()).isEqualTo(ReasoningEffort.HIGH);
			assertThat(request.aliases().sections()).singleElement()
				.satisfies(section -> assertThat(section.aliases()).containsExactly("S1", "E1"));
			assertThat(JSON.readTree(request.prompt().userContent()).get("storylines").get(0).get(0).asString())
				.isEqualTo("S1");
			assertThat(JSON.readTree(request.prompt().outputSchema()).get("properties").get("sections").get("required"))
				.isEqualTo(JSON.readTree("[\"storylines\"]"));
			assertThat(request.prompt().instructions()).contains("서로 견주거나 더하지 않는다");
		});
	}

	@Test
	void sendsTheInputFittedToTheBudgetAndKeepsTheOriginalSnapshot(CapturedOutput output) {
		// 모델 예산 9,600 — 사용자 구획 예산 1,600. 흐름 셋의 최근 전개가 1,000 자씩이라 흐름 하나만 들어가 보도량이 적은 흐름 둘을 뺀다
		LlmModel small = new LlmModel(Provider.OPENAI, "gpt-small", 9_600, true);
		List<ReportRequest> requests = new ArrayList<>();
		ReportGeneration generation = generation(scripted(new AtomicInteger(), request -> {
			requests.add(request);
			return """
					{"overview":"개요","sections":{"storylines":{"title":"흐름","body":"본문","refs":["S1"]}}}""";
		}));
		ReportId id = generation.schedule(new UserId(7), WEEK);
		ReportInput input = input(new StorylinesSection(List.of(recentFlow(31, 50, "가"), recentFlow(32, 10, "나"),
				recentFlow(33, 30, "다"))));

		assertThat(generation.run(id, small, ReportFixtures.EFFORT, input)).isEqualTo(ReportStatus.READY);

		String sent = requests.get(0).prompt().userContent();
		assertThat(sent).contains("가".repeat(1_000)).doesNotContain("나".repeat(1_000), "다".repeat(1_000));
		assertThat(itemRows(id)).containsExactly("1:0 S31");
		assertThat(JSON.readTree((String) row(id).get("snapshot")))
			.isEqualTo(JSON.readTree("{\"coverage\": {\"직전\": 7, \"이번\": 12}}"));
		assertThat(output).contains("리포트 입력을 예산에 맞춰 덜어 냈다 — 리포트 " + id.value() + ", 예산 1600,")
			.contains("흐름 뺌 2, 예산을 넘긴 채 false")
			.doesNotContain("나".repeat(10));
	}

	@Test
	void dropsUnknownAliasesKeepsTheReportAndLogsWhatWasDropped(CapturedOutput output) {
		// 구독 주제 보도량 칸이 빠져 한 번 다시 묻고, 다시 물은 답도 같아 다시 물은 쪽을 쓴다
		AtomicInteger calls = new AtomicInteger();
		ReportGeneration generation = generation(scripted(calls, request -> """
				{"overview":"개요","sections":{"storylines":{"title":"흐름","body":"본문","refs":["S1","Z9",""]}}}"""));
		ReportId id = generation.schedule(new UserId(7), WEEK);

		assertThat(generation.run(id, ReportFixtures.MODEL, ReportFixtures.EFFORT,
				input(flows(flow(31)), coverage())))
			.isEqualTo(ReportStatus.READY);

		assertThat(calls).hasValue(2);
		assertThat(sectionRows(id)).containsExactly("0 OVERVIEW null 개요", "1 STORYLINES 흐름 본문");
		assertThat(itemRows(id)).containsExactly("1:0 S31");
		assertThat(output).contains("리포트 출력에서 버린 것이 있다 — 리포트 " + id.value() + ", 빠진 섹션 1, 모르는 별칭 2, 상한으로 버린 수 0")
			.doesNotContain("Z9");
	}

	private ReportGeneration generation(ReportGenerator generator) {
		return new ReportGeneration(this.reports, this.sections, generator, this.transaction,
				TestClocks.of(() -> this.now.get()));
	}

	/** 사건 줄 없는 추적 흐름 — 최근 전개가 {@code letter} 1,000 자 */
	private static FlowMaterial recentFlow(long head, long articles, String letter) {
		return new FlowMaterial(new StorylineId(head), Set.of(new StorylineId(head)),
				new FlowSummary("제목", "", letter.repeat(1_000), T0), StorylineState.ACTIVE, false,
				new Count(articles, null), List.of());
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
				       error_message, started_at, completed_at, stats_snapshot::text as snapshot, zone, previous_report_id
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
