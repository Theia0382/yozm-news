package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.FlowDescriptor;
import net.yozmnews.evaluation.FlowHappening;
import net.yozmnews.evaluation.RelationAnswer;
import net.yozmnews.evaluation.RelationJudgement;
import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.evaluation.RelationQuery;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.FlowText;
import net.yozmnews.evaluation.domain.evaluator.HappeningText;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipResult;
import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RelationInput;
import net.yozmnews.evaluation.domain.evaluator.RelationRereadResult;
import net.yozmnews.evaluation.domain.evaluator.RelationResult;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import net.yozmnews.evaluation.domain.evaluator.RereadResult;
import net.yozmnews.evaluation.domain.jev.JevResponse;
import net.yozmnews.evaluation.domain.relation.RelationJudgementRepository;
import net.yozmnews.evaluation.domain.relation.RelationRecord;
import net.yozmnews.evaluation.domain.relation.StoredRelationJudgement;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관계 판정 공개 API — 서술 기사 읽기, 물을 서술 고르기, 결과 갈래, 판정 원본 기록을 실제 PostgreSQL 과 결선된 저장소 · 기사 조회로 본다.
 * 평가기는 시험이 넣는다. 커밋 경계를 지나므로 시험을 트랜잭션으로 감싸지 않는다.
 * <p>
 * 지키는 규칙: evaluation-R48 · evaluation-R49
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class FlowRelationFlowTests {

	private static final Instant AT = Instant.parse("2026-11-05T06:30:00Z");

	private static final Instant T1 = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant T2 = Instant.parse("2026-11-02T00:00:00Z");

	private static final Instant T3 = Instant.parse("2026-11-03T00:00:00Z");

	private static final String RAW = "{\"model\":\"jev-1.14.0\",\"answers\":{}}";

	private static final Map<RelationAnswer, Double> PROBABILITIES = Map.of(RelationAnswer.NONE, 0.1,
			RelationAnswer.SECOND_PART_OF_FIRST, 0.7, RelationAnswer.FIRST_PART_OF_SECOND, 0.05,
			RelationAnswer.FIRST_LED_TO_SECOND, 0.1, RelationAnswer.SECOND_LED_TO_FIRST, 0.05);

	@Autowired
	ArticleDirectory articles;

	@Autowired
	RelationJudgementRepository judgements;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from flow_relation_judgements").update();
		this.jdbc.sql("delete from article_feed_entries").update();
		this.jdbc.sql("delete from articles").update();
	}

	@Test
	void judgesTheTwoFlowsAndRecordsTheJudgement() {
		long passed = article("특검법 통과", "재석 과반");
		long veto = article("거부권 건의", "야당 반발");
		long nominee = article("특검 후보 추천", null);
		Stub stub = new Stub(input -> relation(PROBABILITIES));

		RelationJudgement judgement = service(stub).judge(new RelationQuery(
				new FlowDescriptor(7, 12, T1, T3, List.of(happening(passed, T1, true), happening(veto, T2, false))),
				new FlowDescriptor(9, 4, T2, T3, List.of(happening(nominee, T2, true)))));

		assertThat(judgement)
			.isEqualTo(new RelationJudgement.Judged(new RelationJudgementId(judgementId()), PROBABILITIES, 0.8, 0.6));
		assertThat(stub.inputs).containsExactly(new RelationInput(
				new FlowText(12, T1, T3,
						List.of(new HappeningText(T1, "특검법 통과", "재석 과반"), new HappeningText(T2, "거부권 건의", null))),
				new FlowText(4, T2, T3, List.of(new HappeningText(T2, "특검 후보 추천", null))),
				RelationWording.CURRENT));
		Map<String, Object> row = this.jdbc.sql("""
				select first_flow_id, second_flow_id, described::text as described, evaluator, model_version,
				       wording_version, raw_response = cast(:raw as jsonb) as same_raw, input_tokens
				from flow_relation_judgements
				""").param("raw", RAW).query().singleRow();
		assertThat(row).containsEntry("first_flow_id", 7L)
			.containsEntry("second_flow_id", 9L)
			.containsEntry("described",
					"{\"first\": [{\"articleId\": " + passed + ", \"summarized\": true}, {\"articleId\": " + veto
							+ ", \"summarized\": false}], \"second\": [{\"articleId\": " + nominee
							+ ", \"summarized\": false}]}")
			.containsEntry("evaluator", "jev")
			.containsEntry("model_version", "jev-1.14.0")
			.containsEntry("wording_version", "relation-1")
			.containsEntry("same_raw", true)
			.containsEntry("input_tokens", 1924);
		assertThat(this.jdbc.sql("select judged_at from flow_relation_judgements")
			.query(OffsetDateTime.class)
			.single()
			.toInstant()).isEqualTo(AT);
	}

	@Test
	void findsStoredJudgementsByIdAndTheirRawResponseStillReadsAfterJsonb() {
		// 원본 응답은 jsonb 를 거치면 공백 · 키 순서가 바뀐다 — 그래도 Jev 형식으로 같은 확률이 읽혀야 한다
		String body = "{\"model\": \"jev-1.14.0\", \"answers\": {\"relation\": {\"type\": \"choice\", \"choice\": \"none\", "
				+ "\"confidence\": 0.62, \"probabilities\": {\"none\": 0.62, \"b_part_of_a\": 0.2, \"a_part_of_b\": 0.08, "
				+ "\"a_led_to_b\": 0.06, \"b_led_to_a\": 0.04}}, \"a_one_matter\": {\"type\": \"noul\", \"noul\": 0.35}, "
				+ "\"b_one_matter\": {\"type\": \"noul\", \"noul\": 0.77}}, \"usage\": {\"input_tokens\": 811, \"output_tokens\": 90}}";
		RelationJudgementId first = this.transaction.execute(status -> this.judgements.insert(new RelationRecord(5, 3,
				List.of(new RelationRecord.Described(new ArticleId(11), true)),
				List.of(new RelationRecord.Described(new ArticleId(12), false)), "jev", "jev-1.14.0", "relation-1", 811,
				body, AT)));
		RelationJudgementId second = this.transaction.execute(status -> this.judgements.insert(new RelationRecord(8, 9,
				List.of(new RelationRecord.Described(new ArticleId(13), false)),
				List.of(new RelationRecord.Described(new ArticleId(14), false)), "fake", "fake-rules-2", "relation-1", 0,
				"{\"evaluator\": \"fake\"}", AT)));

		List<StoredRelationJudgement> found = this.judgements
			.findAll(List.of(second, new RelationJudgementId(second.value() + 1000), first));

		assertThat(found).extracting(StoredRelationJudgement::id).containsExactly(first, second);
		StoredRelationJudgement stored = found.getFirst();
		assertThat(stored).extracting(StoredRelationJudgement::firstFlowId, StoredRelationJudgement::secondFlowId,
				StoredRelationJudgement::evaluator, StoredRelationJudgement::modelVersion,
				StoredRelationJudgement::wordingVersion, StoredRelationJudgement::inputTokens,
				StoredRelationJudgement::judgedAt)
			.containsExactly(5L, 3L, "jev", "jev-1.14.0", "relation-1", 811, AT);
		Relation reread = (Relation) JevResponse.readRelation(stored.rawResponse(), null);
		assertThat(reread.probabilities()).isEqualTo(Map.of(RelationAnswer.NONE, 0.62,
				RelationAnswer.SECOND_PART_OF_FIRST, 0.2, RelationAnswer.FIRST_PART_OF_SECOND, 0.08,
				RelationAnswer.FIRST_LED_TO_SECOND, 0.06, RelationAnswer.SECOND_LED_TO_FIRST, 0.04));
		assertThat(reread.firstOneMatter()).isEqualTo(0.35);
		assertThat(reread.secondOneMatter()).isEqualTo(0.77);
		assertThat(this.judgements.findAll(List.of())).isEmpty();
	}

	@Test
	void readsAnArticleSharedByBothFlowsOnceAndKeepsEveryJudgementOfThePair() {
		long shared = article("특검법 통과", null);
		long other = article("특검 후보 추천", null);
		Stub stub = new Stub(input -> relation(PROBABILITIES));
		RelationQuery query = new RelationQuery(
				new FlowDescriptor(7, 12, T1, T3, List.of(happening(shared, T1, false))),
				new FlowDescriptor(9, 4, T1, T3, List.of(happening(shared, T1, false), happening(other, T2, false))));

		service(stub).judge(query);
		service(stub).judge(query);

		assertThat(stub.inputs.getFirst().second().described()).hasSize(2);
		assertThat(judgementRows()).isEqualTo(2);
	}

	@Test
	void returnsEveryFailureKindWithoutRecordingIt() {
		long one = article("특검법 통과", null);
		long two = article("특검 후보 추천", null);
		for (EvaluatorFailureKind kind : EvaluatorFailureKind.values()) {
			Stub stub = new Stub(input -> new EvaluationFailure(kind, "실패 " + kind));

			assertThat(service(stub).judge(query(one, two))).isEqualTo(new RelationJudgement.Failed(kind, "실패 " + kind));
		}
		assertThat(judgementRows()).isZero();
	}

	@Test
	void doesNotAskWhenAFlowHasNoDescriptionLeft() {
		long one = article("특검법 통과", null);
		Stub stub = new Stub(input -> relation(PROBABILITIES));

		assertThat(service(stub).judge(query(one, 999_999))).isEqualTo(new RelationJudgement.NothingToAsk());
		assertThat(stub.inputs).isEmpty();
		assertThat(judgementRows()).isZero();
	}

	@Test
	void throwsWithoutRecordingWhenTheResultDoesNotCarryTheFiveAnswers() {
		long one = article("특검법 통과", null);
		long two = article("특검 후보 추천", null);
		Stub stub = new Stub(input -> relation(Map.of(RelationAnswer.NONE, 0.9)));

		assertThatIllegalStateException().isThrownBy(() -> service(stub).judge(query(one, two)))
			.withMessageStartingWith("관계 판정 결과를 저장할 수 없다 — 답이 선택지 다섯과 다르다");
		assertThat(judgementRows()).isZero();
	}

	@Test
	void refusesToJudgeInsideATransaction() {
		long one = article("특검법 통과", null);
		long two = article("특검 후보 추천", null);
		Stub stub = new Stub(input -> relation(PROBABILITIES));

		assertThatIllegalStateException()
			.isThrownBy(() -> this.transaction.executeWithoutResult(status -> service(stub).judge(query(one, two))))
			.withMessage("관계 판정을 트랜잭션 안에서 불렀다 — Jev 호출은 트랜잭션 밖에서 한다");
		assertThat(stub.inputs).isEmpty();
	}

	@Test
	void theTableRefusesAJudgementOfAFlowWithItself() {
		// 값 타입이 먼저 막지만 테이블도 같은 흐름 둘의 행을 받지 않는다
		assertThatThrownBy(() -> this.jdbc.sql("""
				insert into flow_relation_judgements (first_flow_id, second_flow_id, described, evaluator, model_version,
				    wording_version, raw_response, input_tokens, judged_at)
				values (7, 7, cast('{}' as jsonb), 'jev', 'jev-1.14.0', 'relation-1', cast('{}' as jsonb), 0,
				    '2026-11-05T06:30:00Z')
				""").update()).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("flow_relation_judgements_distinct_flows");
		assertThat(judgementRows()).isZero();
	}

	@Test
	void failsWithoutAnEvaluator() {
		FlowRelationService service = new FlowRelationService(() -> null, this.articles, this.judgements,
				this.transaction, () -> AT);

		assertThatIllegalStateException().isThrownBy(() -> service.judge(query(1, 2)))
			.withMessage("평가기가 없는 프로세스에서 관계 판정을 불렀다 — 판정은 작업 역할의 작업에서 부른다");
	}

	private FlowRelationService service(ArticleEvaluator evaluator) {
		return new FlowRelationService(() -> evaluator, this.articles, this.judgements, this.transaction, () -> AT);
	}

	private static Relation relation(Map<RelationAnswer, Double> probabilities) {
		return new Relation(probabilities, 0.8, 0.6, "jev", "jev-1.14.0", 1924, RAW);
	}

	private static RelationQuery query(long first, long second) {
		return new RelationQuery(new FlowDescriptor(7, 12, T1, T3, List.of(happening(first, T1, false))),
				new FlowDescriptor(9, 4, T2, T3, List.of(happening(second, T2, false))));
	}

	private static FlowHappening happening(long articleId, Instant at, boolean summarized) {
		return new FlowHappening(new ArticleId(articleId), at, summarized);
	}

	private long article(String title, @Nullable String summary) {
		String url = "https://relation.example/" + UUID.randomUUID();
		long id = this.jdbc.sql("""
				insert into articles (canonical_url, url_hash, title, title_key_hash, summary_text, published_at,
				    published_at_estimated, first_seen_at)
				values (:url, sha256(convert_to(:url, 'UTF8')), :title, sha256(convert_to(:url, 'UTF8')), :summary,
				    '2026-11-05T05:00:00Z', false, '2026-11-05T05:00:00Z')
				returning id
				""").param("url", url).param("title", title).param("summary", summary, java.sql.Types.VARCHAR)
			.query(Long.class)
			.single();
		this.jdbc.sql("""
				insert into article_feed_entries (article_id, feed_id, source_id, item_url_hash, feed_categories,
				    published_at_estimated, match_kind, seen_at)
				values (:article, 510, 51, sha256(convert_to(:url, 'UTF8')), :categories, false, 'NEW',
				    '2026-11-05T05:00:00Z')
				""").param("article", id).param("url", url).param("categories", new String[0]).update();
		return id;
	}

	private long judgementRows() {
		return this.jdbc.sql("select count(*) from flow_relation_judgements").query(Long.class).single();
	}

	/** 하나뿐인 판정 원본의 ID — DB 가 매긴 값 */
	private long judgementId() {
		return this.jdbc.sql("select id from flow_relation_judgements").query(Long.class).single();
	}

	/** 관계만 판정하는 시험 평가기 — 받은 입력을 모은다 */
	private static final class Stub implements ArticleEvaluator {

		private final Function<RelationInput, RelationResult> answer;

		private final List<RelationInput> inputs = new ArrayList<>();

		Stub(Function<RelationInput, RelationResult> answer) {
			this.answer = answer;
		}

		@Override
		public EvaluationResult evaluate(EvaluationInput input, TagQuestions tagSet) {
			throw new UnsupportedOperationException("이 시험 평가기는 평가하지 않는다");
		}

		@Override
		public RereadResult reread(String evaluator, String rawResponse, TagQuestions tagSet) {
			throw new UnsupportedOperationException("이 시험 평가기는 원본 응답을 다시 읽지 않는다");
		}

		@Override
		public MembershipResult judgeMembership(MembershipInput input) {
			throw new UnsupportedOperationException("이 시험 평가기는 사건 소속을 판정하지 않는다");
		}

		@Override
		public RelationResult judgeRelation(RelationInput input) {
			this.inputs.add(input);
			return this.answer.apply(input);
		}

		@Override
		public RelationRereadResult rereadRelation(String evaluator, String rawResponse) {
			throw new UnsupportedOperationException("이 시험 평가기는 관계 판정 원본을 다시 읽지 않는다");
		}

	}

}
