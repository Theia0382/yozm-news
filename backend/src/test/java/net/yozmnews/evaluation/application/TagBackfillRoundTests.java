package net.yozmnews.evaluation.application;

import static net.yozmnews.evaluation.application.BackfillScenario.topic;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.ArticleTagging;
import net.yozmnews.evaluation.TagBackfillRequestResult;
import net.yozmnews.evaluation.TagBackfillRunView;
import net.yozmnews.evaluation.TagBackfillState;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagReread;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.backfill.TagBackfill;
import net.yozmnews.evaluation.domain.backfill.TagBackfillRepository;
import net.yozmnews.evaluation.domain.result.EvaluationResultRepository;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 빠진 태그 채움 회차의 점유와 멈춤 — 점유가 만료되면 다른 회차가 커서부터 잇고, 점유를 잃은 묶음은 되돌리고, 다시 읽기의 예외는 그 평가만
 * 세고, 읽지 못한 판정 원본은 읽을 수 없음으로 세고, 그 밖의 예외는 점유를 풀고 멈추는 것을 실제 PostgreSQL 에서 본다. 활성 버전(1)의
 * 저장 기준 0.1 로 채운다. 다시 읽기의 결과는 대역({@link ScriptedTagging})의 대본이다 — 원본 응답 해석 · 질문 묶음과의 대조는 관문
 * 시험이 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TagBackfillRoundTests {

	private static final Instant EVALUATED = Instant.parse("2026-10-05T00:00:00Z");

	private static final Instant REQUESTED = Instant.parse("2026-10-31T00:00:00Z");

	/** 시작 가능 시각(요청 + 1분) 뒤 */
	private static final Instant RUN = REQUESTED.plus(Duration.ofMinutes(2));

	@Autowired
	TagSetAdministration tagSetAdministration;

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	EvaluationResultRepository results;

	@Autowired
	TagBackfillRepository backfills;

	@Autowired
	TagSetViews views;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ArticleTagging tagging;

	@Autowired
	JdbcClient jdbc;

	private BackfillScenario scenario;

	private TagSetId version;

	/** 8001 의 판정 원본 */
	private TagJudgementId legislature;

	private long backfill;

	@BeforeEach
	void twoEvaluationsWithMissingTagsAndARequest() {
		this.scenario = new BackfillScenario(this.tagSetAdministration, this.tagSets, this.thresholds, this.results,
				this.transaction, this.jdbc);
		this.scenario.reset();
		this.version = this.tagSets.findByActiveTrue().orElseThrow().id();
		this.legislature = this.scenario.insertRead(this.version, 8001, EVALUATED,
				Map.of("politics.legislature", 0.2468), Map.of(), List.of());
		this.scenario.insertRead(this.version, 8002, EVALUATED.plusSeconds(1), Map.of("economy.finance", 0.2), Map.of(),
				List.of());
		this.backfill = ((TagBackfillRequestResult.Requested) new TagBackfillAdministrationService(this.tagSets,
				this.thresholds, this.results, this.backfills, this.transaction, InstantSource.fixed(REQUESTED))
			.request(30)).run().id();
	}

	@AfterEach
	void reset() {
		this.scenario.reset();
	}

	@Test
	void anotherRoundContinuesFromTheCursorOnceTheLeaseExpires() {
		// 앞 회차가 8001 을 처리하고 점유를 쥔 채 죽었다
		UUID dead = UUID.randomUUID();
		this.transaction.executeWithoutResult(status -> {
			TagBackfill claimed = this.backfills.lockDue(RUN).orElseThrow().claim(dead, RUN);
			this.backfills.saveLocked(claimed);
			this.backfills.advance(this.backfill, dead, evaluationId(8001), Map.of(), RUN.plus(Duration.ofMinutes(10)));
		});

		round(RUN.plus(Duration.ofMinutes(5)), scripted()).run();
		assertThat(run().state()).as("점유가 살아 있으면 가져가지 않는다").isEqualTo(TagBackfillState.RUNNING);
		assertThat(this.scenario.tags(8002)).isEmpty();

		round(RUN.plus(Duration.ofMinutes(11)), scripted()).run();

		assertThat(run().state()).isEqualTo(TagBackfillState.DONE);
		assertThat(this.scenario.tags(8002)).containsExactly(topic("economy.finance", 0.2));
		// 커서 앞의 8001 은 다시 처리하지 않는다
		assertThat(this.scenario.tags(8001)).isEmpty();
		assertThat(run().processed()).isEqualTo(1);
	}

	@Test
	void aBatchWhoseLeaseWasTakenIsRolledBack() {
		ScriptedTagging stealing = scripted().rereading((judgement, questions) -> {
			this.jdbc.sql("update tag_backfills set lease_token = :other").param("other", UUID.randomUUID()).update();
			return this.scenario.reread(judgement, questions);
		});

		round(RUN, stealing).run();

		assertThat(this.scenario.tags(8001)).isEmpty();
		assertThat(this.scenario.tags(8002)).isEmpty();
		assertThat(run().processed()).isZero();
		assertThat(run().state()).isEqualTo(TagBackfillState.RUNNING);
	}

	@Test
	void anUnexpectedRereadExceptionCountsThatEvaluationAndContinues() {
		ScriptedTagging throwingOnLegislature = scripted().rereading((judgement, questions) -> {
			if (judgement.equals(this.legislature)) {
				throw new IllegalStateException("시험 판정의 결함");
			}
			return this.scenario.reread(judgement, questions);
		});

		round(RUN, throwingOnLegislature).run();

		TagBackfillRunView run = run();
		assertThat(run.state()).isEqualTo(TagBackfillState.DONE);
		assertThat(List.of(run.processed(), run.tagsAdded(), run.skippedUnreadable())).containsExactly(2L, 1L, 1L);
		assertThat(this.scenario.tags(8001)).isEmpty();
		assertThat(this.scenario.tags(8002)).containsExactly(topic("economy.finance", 0.2));
	}

	@Test
	void anUnreadableJudgementIsCountedAsUnreadable() {
		// 관문이 읽지 못했다고 답한다(질문 묶음과 어긋난 원본 포함 — 그 대조는 관문의 일이다) — 태그를 더하지 않고 읽을 수 없음으로 센다
		ScriptedTagging unreadable = scripted().rereading((judgement, questions) -> new TagReread.Unread(
				TagReread.Unread.Reason.UNREADABLE, "주제 키가 체계와 다르다 — 빠진 키 [economy.finance], 모르는 키 []"));

		round(RUN, unreadable).run();

		TagBackfillRunView run = run();
		assertThat(run.state()).isEqualTo(TagBackfillState.DONE);
		assertThat(List.of(run.processed(), run.tagsAdded(), run.skippedOtherEvaluator(), run.skippedUnreadable()))
			.containsExactly(2L, 0L, 0L, 2L);
		assertThat(this.scenario.tags(8001)).isEmpty();
		assertThat(this.scenario.tags(8002)).isEmpty();
	}

	@Test
	void anyOtherFailureRollsBackTheBatchReleasesTheLeaseAndStops() {
		// 요청 뒤에 생긴 버전의 평가 — 채움 계획에 없는 버전은 불변식이 깨진 것이다
		long later = this.jdbc.sql("""
				insert into tag_sets (number, active, topic_template, region_template, format_question,
				    importance_question, created_by, created_at)
				values (99, false, '{description}', '{description}', '형식', '중요도', 'SYSTEM', now())
				returning id
				""").query(Long.class).single();
		this.scenario.insert(new TagSetId(later), 8003, EVALUATED.plusSeconds(2), "jev", List.of());

		round(RUN, scripted()).run();

		assertThat(this.scenario.tags(8001)).isEmpty();
		assertThat(run().processed()).isZero();
		assertThat(run().state()).isEqualTo(TagBackfillState.RUNNING);
		assertThat(this.jdbc.sql("select lease_token is null from tag_backfills").query(Boolean.class).single())
			.isTrue();
	}

	/** 넣은 판정 원본의 대본대로 다시 읽는 대역 */
	private ScriptedTagging scripted() {
		return new ScriptedTagging(this.tagging).rereading(this.scenario::reread);
	}

	private TagBackfillRound round(Instant now, ScriptedTagging tagging) {
		return new TagBackfillRound(this.backfills, this.results, this.thresholds, this.views, tagging,
				this.transaction, InstantSource.fixed(now));
	}

	private TagBackfillRunView run() {
		return this.backfills.run(this.backfill).orElseThrow();
	}

	private long evaluationId(long article) {
		return this.jdbc.sql("select id from article_evaluations where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single();
	}

}
