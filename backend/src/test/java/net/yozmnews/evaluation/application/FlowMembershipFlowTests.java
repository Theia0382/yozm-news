package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
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
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.evaluation.EvaluatorFailureKind;
import net.yozmnews.evaluation.FlowCandidate;
import net.yozmnews.evaluation.FlowMembership;
import net.yozmnews.evaluation.FlowMembershipJudgement;
import net.yozmnews.evaluation.FlowMembershipJudgementId;
import net.yozmnews.evaluation.FlowMembershipQuery;
import net.yozmnews.evaluation.JudgedEvent;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.CandidateEvent;
import net.yozmnews.evaluation.domain.evaluator.EvaluationFailure;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.Membership;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipResult;
import net.yozmnews.evaluation.domain.evaluator.MembershipWording;
import net.yozmnews.evaluation.domain.evaluator.RelationInput;
import net.yozmnews.evaluation.domain.evaluator.RelationRereadResult;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import net.yozmnews.evaluation.domain.evaluator.RelationResult;
import net.yozmnews.evaluation.domain.evaluator.RereadResult;
import net.yozmnews.evaluation.domain.membership.FlowMembershipJudgementRepository;
import net.yozmnews.evaluation.domain.membership.MembershipRecord;
import net.yozmnews.source.SourceDeleted;
import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 흐름 소속 판정 공개 API — 흐름 소속 문구로 묻기, 물을 후보 고르기, 결과 갈래, 흐름 소속 원본 표에 기록, 판정 중 지운 기사, 지운 기사의
 * 판정 원본 지우기를 실제 PostgreSQL 과 결선된 저장소 · 기사 조회로 본다. 평가기는 시험이 넣는다. 커밋 경계를 지나므로 시험을 트랜잭션으로
 * 감싸지 않는다.
 * <p>
 * 지키는 규칙: evaluation-R51 · evaluation-R52 · evaluation-R44
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class FlowMembershipFlowTests {

	private static final Instant AT = Instant.parse("2026-11-16T09:00:00Z");

	private static final String RAW = "{\"model\":\"jev-1.14.0\",\"answers\":{}}";

	@Autowired
	ArticleDirectory articles;

	@Autowired
	FlowMembershipJudgementRepository judgements;

	@Autowired
	FlowMembership wired;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from flow_membership_judgements").update();
		this.jdbc.sql("delete from article_event_judgements").update();
		this.jdbc.sql("delete from article_tag_judgements").update();
		this.jdbc.sql("delete from article_feed_entries").update();
		this.jdbc.sql("delete from articles").update();
	}

	@Test
	void asksWithTheSameMatterWordingAndRecordsTheJudgementInItsOwnTable() {
		long judged = article("서울 시장 선거 결과", "개표가 끝났다", 51);
		long counting = article("서울 시장 선거 개표", null, 52);
		long winner = article("서울 시장 당선 확정", null, 52);
		long strike = article("부산 항만 파업", null, 52);
		Stub stub = new Stub(input -> new Membership(Map.of(305L, 0.83, 306L, 0.07), "jev", "jev-1.14.0", 812, RAW));

		FlowMembershipJudgement judgement = service(stub).judge(new FlowMembershipQuery(new ArticleId(judged),
				List.of(new FlowCandidate(305, List.of(new ArticleId(counting), new ArticleId(winner))),
						new FlowCandidate(306, List.of(new ArticleId(strike))))));

		assertThat(judgement).isEqualTo(new FlowMembershipJudgement.Judged(new FlowMembershipJudgementId(judgementId()),
				Map.of(305L, 0.83, 306L, 0.07)));
		assertThat(stub.inputs).containsExactly(new MembershipInput("서울 시장 선거 결과", "개표가 끝났다",
				List.of(new CandidateEvent(305, List.of("서울 시장 선거 개표", "서울 시장 당선 확정")),
						new CandidateEvent(306, List.of("부산 항만 파업"))),
				MembershipWording.SAME_MATTER));
		Map<String, Object> row = this.jdbc.sql("""
				select article_id, evaluator, model_version, wording_version, candidates::text as candidates,
				       input_tokens
				from flow_membership_judgements
				""").query().singleRow();
		assertThat(row).containsEntry("article_id", judged)
			.containsEntry("evaluator", "jev")
			.containsEntry("model_version", "jev-1.14.0")
			.containsEntry("wording_version", "matter-1")
			.containsEntry("candidates", "[{\"flowId\": 305, \"articleIds\": [" + counting + ", " + winner
					+ "], \"probability\": 0.83}, {\"flowId\": 306, \"articleIds\": [" + strike + "], \"probability\": 0.07}]")
			.containsEntry("input_tokens", 812);
		assertThat(this.jdbc.sql("select judged_at from flow_membership_judgements")
			.query(OffsetDateTime.class)
			.single()
			.toInstant()).isEqualTo(AT);
		// 사건 소속 판정 원본 표에는 쓰지 않는다
		assertThat(this.jdbc.sql("select count(*) from article_event_judgements").query(Long.class).single()).isZero();
	}

	@Test
	void theWiredServiceRefusesToBeCalledInsideATransactionBeforeReadingAnything() {
		long judged = article("서울 시장 선거 결과", null, 51);
		long counting = article("서울 시장 선거 개표", null, 52);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.transaction
				.executeWithoutResult(status -> this.wired.judge(query(judged, 305, counting))))
			.withMessage("흐름 소속 판정을 트랜잭션 안에서 불렀다 — Jev 호출은 트랜잭션 밖에서 한다");
		assertThat(judgementRows()).isZero();
	}

	@Test
	void failsBeforeReadingAnythingWithoutAnEvaluator() {
		FlowMembershipService service = new FlowMembershipService(() -> null, this.articles, this.judgements,
				this.transaction, () -> AT);

		assertThatIllegalStateException().isThrownBy(() -> service.judge(query(1, 305, 3)))
			.withMessage("평가기가 없는 프로세스에서 흐름 소속 판정을 불렀다 — 판정은 작업 역할의 작업에서 부른다");
	}

	@Test
	void returnsEveryFailureKindWithoutRecordingIt() {
		long judged = article("서울 시장 선거 결과", null, 51);
		long counting = article("서울 시장 선거 개표", null, 52);
		for (EvaluatorFailureKind kind : EvaluatorFailureKind.values()) {
			Stub stub = new Stub(input -> new EvaluationFailure(kind, "실패 " + kind));

			assertThat(service(stub).judge(query(judged, 305, counting)))
				.isEqualTo(new FlowMembershipJudgement.Failed(kind, "실패 " + kind));
		}
		assertThat(judgementRows()).isZero();
	}

	@Test
	void doesNotAskWhenTheJudgedArticleIsGone() {
		long counting = article("서울 시장 선거 개표", null, 52);
		Stub stub = new Stub(input -> new Membership(Map.of(305L, 0.83), "jev", "jev-1.14.0", 812, RAW));

		assertThat(service(stub).judge(query(999_999, 305, counting)))
			.isEqualTo(new FlowMembershipJudgement.ArticleMissing());
		assertThat(stub.inputs).isEmpty();
		assertThat(judgementRows()).isZero();
	}

	@Test
	void doesNotAskWhenNoCandidateHasADescriptionLeft() {
		long judged = article("서울 시장 선거 결과", null, 51);
		Stub stub = new Stub(input -> new Membership(Map.of(305L, 0.83), "jev", "jev-1.14.0", 812, RAW));

		assertThat(service(stub).judge(query(judged, 305, 999_999)))
			.isEqualTo(new FlowMembershipJudgement.Judged(null, Map.of()));
		assertThat(stub.inputs).isEmpty();
		assertThat(judgementRows()).isZero();
	}

	@Test
	void refusesToRecordAResultThatDoesNotMatchTheAskedFlows() {
		long judged = article("서울 시장 선거 결과", null, 51);
		long counting = article("서울 시장 선거 개표", null, 52);
		long strike = article("부산 항만 파업", null, 52);
		FlowMembershipQuery query = new FlowMembershipQuery(new ArticleId(judged),
				List.of(new FlowCandidate(305, List.of(new ArticleId(counting))),
						new FlowCandidate(306, List.of(new ArticleId(strike)))));

		assertThatIllegalStateException()
			.isThrownBy(() -> service(new Stub(
					input -> new Membership(Map.of(305L, 0.83, 99L, 0.5), "jev", "jev-1.14.0", 812, RAW)))
				.judge(query))
			.withMessage("흐름 소속 판정 결과를 저장할 수 없다 — 흐름 키가 물은 후보와 다르다 — 빠진 흐름 [306], 모르는 흐름 [99]");
		assertThat(judgementRows()).isZero();
	}

	@Test
	void removesItsJudgementWhenTheArticleIsRemovedDuringTheCall(CapturedOutput output) {
		long judged = article("서울 시장 선거 결과", null, 51);
		long counting = article("서울 시장 선거 개표", null, 52);
		Stub stub = new Stub(input -> {
			// 판정 중에 판정할 기사의 소스를 지운다 — 기사 삭제와 그 리스너들이 모두 끝난 뒤에 답한다
			this.transaction.executeWithoutResult(status -> this.events.publishEvent(new SourceDeleted(new SourceId(51))));
			await().atMost(Duration.ofSeconds(10)).until(() -> articleRows(judged) == 0 && publications() == 0);
			return new Membership(Map.of(305L, 0.83), "jev", "jev-1.14.0", 812, RAW);
		});

		assertThat(service(stub).judge(query(judged, 305, counting)))
			.isEqualTo(new FlowMembershipJudgement.ArticleMissing());
		assertThat(judgementRows()).isZero();
		assertThat(output).contains("흐름 소속 판정 중 기사가 지워져 판정 원본을 지웠다 — 기사 " + judged);
	}

	@Test
	void removingArticlesRemovesTheirFlowMembershipJudgementsOnlyAndAgainChangesNothing() {
		this.judgements.insert(judgement(41, 43));
		this.judgements.insert(judgement(41, 44));
		this.judgements.insert(judgement(42, 41));
		ArticlesRemoved removed = new ArticlesRemoved(List.of(new ArticleId(41)));

		// 41 을 서술 기사로만 쓴 42 의 판정 원본은 남는다
		publish(removed);
		assertThat(judgedArticles()).containsExactly(42L);
		publish(removed);
		assertThat(judgedArticles()).containsExactly(42L);
	}

	/** 공개 경로로 기사 삭제를 알리고 리스너들이 끝나기를 기다린다 */
	private void publish(ArticlesRemoved removed) {
		this.transaction.executeWithoutResult(status -> this.events.publishEvent(removed));
		await().atMost(Duration.ofSeconds(10)).until(() -> publications() == 0);
	}

	/** 기사 하나를 흐름 305 에 대해 판정한 원본 — 서술 기사 하나 */
	private static MembershipRecord judgement(long article, long described) {
		return new MembershipRecord(new ArticleId(article),
				List.of(new JudgedEvent(305, List.of(new ArticleId(described)), 0.8)), "fake", "fake-rules-2",
				"matter-1", 10, "{\"events\":{\"305\":0.8}}", AT);
	}

	/** 흐름 소속 판정 원본이 있는 기사 — 기사 ID 순, 한 번씩 */
	private List<Long> judgedArticles() {
		return this.jdbc.sql("select distinct article_id from flow_membership_judgements order by article_id")
			.query(Long.class)
			.list();
	}

	private FlowMembershipService service(ArticleEvaluator evaluator) {
		return new FlowMembershipService(() -> evaluator, this.articles, this.judgements, this.transaction, () -> AT);
	}

	private static FlowMembershipQuery query(long judged, long flowId, long described) {
		return new FlowMembershipQuery(new ArticleId(judged),
				List.of(new FlowCandidate(flowId, List.of(new ArticleId(described)))));
	}

	/** 기사 하나와 그 엔트리 — 소스 ID 를 정해 소스 삭제로 지울 수 있게 한다 */
	private long article(String title, @Nullable String summary, long source) {
		String url = "https://flow-membership.example/" + UUID.randomUUID();
		long id = this.jdbc.sql("""
				insert into articles (canonical_url, url_hash, title, title_key_hash, summary_text, published_at,
				    published_at_estimated, first_seen_at)
				values (:url, sha256(convert_to(:url, 'UTF8')), :title, sha256(convert_to(:title, 'UTF8')), :summary,
				    '2026-11-16T08:00:00Z', false, '2026-11-16T08:00:00Z')
				returning id
				""").param("url", url).param("title", title).param("summary", summary, java.sql.Types.VARCHAR)
			.query(Long.class)
			.single();
		this.jdbc.sql("""
				insert into article_feed_entries (article_id, feed_id, source_id, item_url_hash, feed_categories,
				    published_at_estimated, match_kind, seen_at)
				values (:article, :feed, :source, sha256(convert_to(:url, 'UTF8')), :categories, false, 'NEW',
				    '2026-11-16T08:00:00Z')
				""")
			.param("article", id)
			.param("feed", source * 10)
			.param("source", source)
			.param("url", url)
			.param("categories", new String[0])
			.update();
		return id;
	}

	private long articleRows(long article) {
		return this.jdbc.sql("select count(*) from articles where id = :id").param("id", article).query(Long.class).single();
	}

	private long publications() {
		return this.jdbc.sql("select count(*) from event_publication").query(Long.class).single();
	}

	private long judgementRows() {
		return this.jdbc.sql("select count(*) from flow_membership_judgements").query(Long.class).single();
	}

	/** 하나뿐인 판정 원본의 ID — DB 가 매긴 값 */
	private long judgementId() {
		return this.jdbc.sql("select id from flow_membership_judgements").query(Long.class).single();
	}

	/** 소속만 판정하는 시험 평가기 — 받은 입력을 모은다. 평가 · 다시 읽기 · 관계 판정은 크게 실패한다 */
	private static final class Stub implements ArticleEvaluator {

		private final Function<MembershipInput, MembershipResult> answer;

		private final List<MembershipInput> inputs = new ArrayList<>();

		Stub(Function<MembershipInput, MembershipResult> answer) {
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
			this.inputs.add(input);
			return this.answer.apply(input);
		}

		@Override
		public RelationResult judgeRelation(RelationInput input) {
			throw new AssertionError("관계 판정을 부르지 않는다");
		}

		@Override
		public RelationRereadResult rereadRelation(String evaluator, String rawResponse, RelationWording wording) {
			throw new UnsupportedOperationException("이 시험 평가기는 관계 판정 원본을 다시 읽지 않는다");
		}

	}

}
