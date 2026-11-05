package net.yozmnews.evaluation.application;

import static net.yozmnews.evaluation.application.BackfillScenario.topic;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.TagBackfillRequestResult;
import net.yozmnews.evaluation.TagBackfillRunView;
import net.yozmnews.evaluation.TagBackfillState;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetView;
import net.yozmnews.evaluation.adapter.out.jev.JevArticleEvaluator;
import net.yozmnews.evaluation.adapter.out.jev.JevEvaluators;
import net.yozmnews.evaluation.domain.backfill.TagBackfill;
import net.yozmnews.evaluation.domain.backfill.TagBackfillRepository;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.Evaluation;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipResult;
import net.yozmnews.evaluation.domain.evaluator.RereadResult;
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
 * 세고, 체계와 어긋난 평가는 읽을 수 없음으로 세고, 그 밖의 예외는 점유를 풀고 멈추는 것을 실제 PostgreSQL 에서 본다. 활성 버전(1)의
 * 저장 기준 0.1 로 채운다.
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
	JdbcClient jdbc;

	private final JevArticleEvaluator jev = JevEvaluators.offline();

	private BackfillScenario scenario;

	private TagSetId version;

	private long backfill;

	@BeforeEach
	void twoEvaluationsWithMissingTagsAndARequest() {
		this.scenario = new BackfillScenario(this.tagSetAdministration, this.tagSets, this.thresholds, this.results,
				this.views, this.transaction, this.jdbc);
		this.scenario.reset();
		this.version = this.tagSets.findByActiveTrue().orElseThrow().id();
		this.scenario.insertJev(this.version, 8001, EVALUATED, Map.of("politics.legislature", 0.2468), Map.of(),
				List.of());
		this.scenario.insertJev(this.version, 8002, EVALUATED.plusSeconds(1), Map.of("economy.finance", 0.2), Map.of(),
				List.of());
		this.backfill = ((TagBackfillRequestResult.Requested) new TagBackfillAdministrationService(this.tagSets,
				this.thresholds, this.results, this.backfills, this.transaction, InstantSource.fixed(REQUESTED))
			.request(30)).run().id();
	}

	@AfterEach
	void reset() {
		this.scenario.reset();
		this.jev.close();
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

		round(RUN.plus(Duration.ofMinutes(5))).run(this.jev);
		assertThat(run().state()).as("점유가 살아 있으면 가져가지 않는다").isEqualTo(TagBackfillState.RUNNING);
		assertThat(this.scenario.tags(8002)).isEmpty();

		round(RUN.plus(Duration.ofMinutes(11))).run(this.jev);

		assertThat(run().state()).isEqualTo(TagBackfillState.DONE);
		assertThat(this.scenario.tags(8002)).containsExactly(topic("economy.finance", 0.2));
		// 커서 앞의 8001 은 다시 처리하지 않는다
		assertThat(this.scenario.tags(8001)).isEmpty();
		assertThat(run().processed()).isEqualTo(1);
	}

	@Test
	void aBatchWhoseLeaseWasTakenIsRolledBack() {
		ArticleEvaluator stealing = new Rereading(this.jev) {

			@Override
			public RereadResult reread(String evaluator, String rawResponse, TagSetView tagSet) {
				TagBackfillRoundTests.this.jdbc.sql("update tag_backfills set lease_token = :other")
					.param("other", UUID.randomUUID())
					.update();
				return super.reread(evaluator, rawResponse, tagSet);
			}

		};

		round(RUN).run(stealing);

		assertThat(this.scenario.tags(8001)).isEmpty();
		assertThat(this.scenario.tags(8002)).isEmpty();
		assertThat(run().processed()).isZero();
		assertThat(run().state()).isEqualTo(TagBackfillState.RUNNING);
	}

	@Test
	void anUnexpectedRereadExceptionCountsThatEvaluationAndContinues() {
		ArticleEvaluator throwingOnLegislature = new Rereading(this.jev) {

			@Override
			public RereadResult reread(String evaluator, String rawResponse, TagSetView tagSet) {
				// 저장된 원본 응답은 jsonb 가 공백을 바꾸므로 확률의 숫자로 가린다
				if (rawResponse.contains("0.2468")) {
					throw new IllegalStateException("시험 평가기의 결함");
				}
				return super.reread(evaluator, rawResponse, tagSet);
			}

		};

		round(RUN).run(throwingOnLegislature);

		TagBackfillRunView run = run();
		assertThat(run.state()).isEqualTo(TagBackfillState.DONE);
		assertThat(List.of(run.processed(), run.tagsAdded(), run.skippedUnreadable())).containsExactly(2L, 1L, 1L);
		assertThat(this.scenario.tags(8001)).isEmpty();
		assertThat(this.scenario.tags(8002)).containsExactly(topic("economy.finance", 0.2));
	}

	@Test
	void aRereadEvaluationThatDoesNotMatchTheTagSetIsCountedAsUnreadable() {
		// 다시 읽은 평가에서 주제 키 하나를 뺀다 — 체계와 대조해 어긋나면 태그를 더하지 않고 읽을 수 없음으로 센다
		ArticleEvaluator dropping = new Rereading(this.jev) {

			@Override
			public RereadResult reread(String evaluator, String rawResponse, TagSetView tagSet) {
				Evaluation read = (Evaluation) super.reread(evaluator, rawResponse, tagSet);
				Map<String, Double> topics = new LinkedHashMap<>(read.topics());
				topics.remove("economy.finance");
				return new Evaluation(topics, read.regions(), read.formats(), read.importance(), read.evaluator(),
						read.modelVersion(), read.inputTokens(), read.rawResponse());
			}

		};

		round(RUN).run(dropping);

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
		this.scenario.insert(new TagSetId(later), 8003, EVALUATED.plusSeconds(2), "jev", "{}", List.of());

		round(RUN).run(this.jev);

		assertThat(this.scenario.tags(8001)).isEmpty();
		assertThat(run().processed()).isZero();
		assertThat(run().state()).isEqualTo(TagBackfillState.RUNNING);
		assertThat(this.jdbc.sql("select lease_token is null from tag_backfills").query(Boolean.class).single())
			.isTrue();
	}

	private TagBackfillRound round(Instant now) {
		return new TagBackfillRound(this.backfills, this.results, this.thresholds, this.views, this.transaction,
				InstantSource.fixed(now));
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

	/** 다시 읽기만 하는 시험 평가기 — 실제 Jev 평가기에 맡긴다. 평가 · 사건 소속 판정은 크게 실패한다 */
	private static class Rereading implements ArticleEvaluator {

		private final ArticleEvaluator delegate;

		Rereading(ArticleEvaluator delegate) {
			this.delegate = delegate;
		}

		@Override
		public EvaluationResult evaluate(EvaluationInput input, TagSetView tagSet) {
			throw new UnsupportedOperationException("이 시험 평가기는 평가하지 않는다");
		}

		@Override
		public RereadResult reread(String evaluator, String rawResponse, TagSetView tagSet) {
			return this.delegate.reread(evaluator, rawResponse, tagSet);
		}

		@Override
		public MembershipResult judgeMembership(MembershipInput input) {
			throw new UnsupportedOperationException("이 시험 평가기는 사건 소속을 판정하지 않는다");
		}

	}

}
