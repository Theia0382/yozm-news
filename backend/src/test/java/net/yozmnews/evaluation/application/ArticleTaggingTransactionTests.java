package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.ArticleTagging;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import net.yozmnews.evaluation.domain.evaluator.EvaluationResult;
import net.yozmnews.evaluation.domain.evaluator.MembershipInput;
import net.yozmnews.evaluation.domain.evaluator.MembershipResult;
import net.yozmnews.evaluation.domain.evaluator.RereadResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 태그 판정 다시 읽기와 부르는 쪽 트랜잭션 — 결선된 운영 구현({@link ArticleTagging} 빈)의 다시 읽기를 부르는 쪽 트랜잭션 안에서 불러,
 * 평가기의 예외가 그 트랜잭션을 롤백 전용으로 만들지 않는 것을 실제 PostgreSQL 에서 본다. 평가기는 다시 읽기에서 예외를 내는 시험 빈이다.
 * <p>
 * 지키는 규칙: evaluation-R42
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ArticleTaggingTransactionTests {

	private static final String DEFECT = "시험 평가기의 결함";

	/** 시험의 질문 묶음 — 평가기가 읽기 전에 예외를 내므로 비어 있다 */
	private static final TagQuestions QUESTIONS = new TagQuestions(List.of(), List.of(),
			new FormatChoice("형식은", List.of()), new ImportanceScale("중요도는", List.of()));

	@Autowired
	ArticleTagging tagging;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		this.jdbc.sql("delete from article_tag_judgements").update();
	}

	@Test
	void anEvaluatorExceptionInARereadLeavesTheCallersTransactionCommittable() {
		TagJudgementId judgement = new TagJudgementId(judgementOf(42));
		AtomicReference<String> thrown = new AtomicReference<>();

		Boolean rollbackOnly = this.transaction.execute(status -> {
			try {
				this.tagging.reread(judgement, QUESTIONS);
			}
			catch (RuntimeException ex) {
				thrown.set(ex.getMessage());
			}
			judgementOf(43);
			return status.isRollbackOnly();
		});

		// 오라클 실현성 — 평가기가 실제로 불려 예외를 냈다
		assertThat(thrown).hasValue(DEFECT);
		assertThat(rollbackOnly).isFalse();
		assertThat(this.jdbc.sql("select count(*) from article_tag_judgements where article_id = 43")
			.query(Long.class)
			.single()).as("예외 뒤에 쓴 행이 커밋됐다").isEqualTo(1);
	}

	/** 기사 하나의 판정 원본을 넣는다 — DB 가 매긴 ID */
	private long judgementOf(long article) {
		return this.jdbc.sql("""
				insert into article_tag_judgements (article_id, evaluator, model_version, raw_response, input_tokens,
				    judged_at)
				values (:article, 'jev', 'jev-1.14.0', '{}', 10, now())
				returning id
				""").param("article", article).query(Long.class).single();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ThrowingEvaluator {

		/** 다시 읽기에서 예상 못한 예외를 내는 평가기 — 평가 · 사건 소속 판정은 부르지 않는다 */
		@Bean
		@Primary
		ArticleEvaluator throwingEvaluator() {
			return new ArticleEvaluator() {

				@Override
				public EvaluationResult evaluate(EvaluationInput input, TagQuestions tagSet) {
					throw new UnsupportedOperationException("이 시험 평가기는 평가하지 않는다");
				}

				@Override
				public RereadResult reread(String evaluator, String rawResponse, TagQuestions tagSet) {
					throw new IllegalStateException(DEFECT);
				}

				@Override
				public MembershipResult judgeMembership(MembershipInput input) {
					throw new UnsupportedOperationException("이 시험 평가기는 사건 소속을 판정하지 않는다");
				}

			};
		}

	}

}
