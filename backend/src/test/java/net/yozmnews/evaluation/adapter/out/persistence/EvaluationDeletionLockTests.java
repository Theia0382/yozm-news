package net.yozmnews.evaluation.adapter.out.persistence;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.result.BackfillTarget;
import net.yozmnews.evaluation.domain.result.EvaluationRecord;
import net.yozmnews.evaluation.domain.result.StoredTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지운 기사 따르기와 빠진 태그 채움의 잠금 순서 — 두 연결이 각자 트랜잭션을 쥔 채 엇갈리게 해, 지우기가 채움이 더한 태그 행까지 지우고 채움은
 * 지운 평가를 보지 않는 것을 실제 PostgreSQL 에서 본다. 연결마다 커밋해야 하므로 시험 트랜잭션으로 감싸지 않고, 만든 행은 시험마다 지운다.
 * <p>
 * 지키는 규칙: evaluation-R21
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcEvaluationResultRepository.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EvaluationDeletionLockTests {

	/** 다른 시험의 평가와 겹치지 않는 평가 시각 */
	private static final Instant AT = Instant.parse("2001-01-01T00:00:00Z");

	private static final ArticleId ARTICLE = new ArticleId(9_042);

	@Autowired
	JdbcEvaluationResultRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PlatformTransactionManager transactionManager;

	private TagSetId version;

	@BeforeEach
	void storeAnEvaluation() {
		removeRows();
		this.version = new TagSetId(this.jdbc.sql("""
				insert into tag_sets (number, active, topic_template, region_template, format_question,
				    importance_question, created_by, created_at)
				values (9001, false, '{description}', '{description}', '형식', '중요도', 'SYSTEM', now())
				returning id
				""").query(Long.class).single());
		this.repository.insert(new EvaluationRecord(ARTICLE, this.version, new TagJudgementId(1), "straight", 0.5,
				Map.of("straight", 0.5, "opinion", 0.5), 3.0, Map.of(3, 1.0),
				List.of(new StoredTag(MultiTagKind.TOPIC, "politics.assembly", 0.5)), AT));
	}

	@AfterEach
	void removeRows() {
		this.jdbc.sql("delete from article_tags where article_id = :article").param("article", ARTICLE.value()).update();
		this.jdbc.sql("delete from article_evaluations where article_id = :article")
			.param("article", ARTICLE.value())
			.update();
		this.jdbc.sql("delete from tag_sets where number = 9001").update();
	}

	@Test
	void aDeletionWaitsForABackfillThatLockedTheEvaluationAndRemovesTheTagsItAdded() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		CompletableFuture<Void> backfill = CompletableFuture.runAsync(() -> transaction().executeWithoutResult(status -> {
			BackfillTarget response = this.repository.lockForBackfill(AT, AT.plusSeconds(1), 0, 10).getFirst();
			this.repository.addMissingTags(response.evaluationId(), ARTICLE,
					List.of(new StoredTag(MultiTagKind.TOPIC, "economy.finance", 0.25)));
			locked.countDown();
			awaitQuietly(commit);
		}));
		try {
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			CompletableFuture<Void> deletion = CompletableFuture.runAsync(
					() -> transaction().executeWithoutResult(status -> this.repository.deleteByArticles(List.of(ARTICLE))));
			Thread.sleep(300);
			assertThat(deletion).as("채움이 평가 행을 잡은 동안 지우기가 기다린다").isNotDone();
			commit.countDown();
			backfill.get(10, TimeUnit.SECONDS);
			deletion.get(10, TimeUnit.SECONDS);
		}
		finally {
			commit.countDown();
		}
		assertThat(count("article_evaluations")).isZero();
		assertThat(count("article_tags")).isZero();
	}

	@Test
	void aBackfillWaitingForADeletionDoesNotSeeTheDeletedEvaluation() throws Exception {
		CountDownLatch deleted = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		CompletableFuture<Void> deletion = CompletableFuture.runAsync(() -> transaction().executeWithoutResult(status -> {
			this.repository.deleteByArticles(List.of(ARTICLE));
			deleted.countDown();
			awaitQuietly(commit);
		}));
		CompletableFuture<List<BackfillTarget>> backfill;
		try {
			assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();
			backfill = CompletableFuture.supplyAsync(() -> transaction()
				.execute(status -> this.repository.lockForBackfill(AT, AT.plusSeconds(1), 0, 10)));
			Thread.sleep(300);
			assertThat(backfill).as("지우기가 평가 행을 잡은 동안 채움이 기다린다").isNotDone();
			commit.countDown();
			deletion.get(10, TimeUnit.SECONDS);
		}
		finally {
			commit.countDown();
		}
		assertThat(backfill.get(10, TimeUnit.SECONDS)).isEmpty();
		assertThat(count("article_evaluations")).isZero();
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(this.transactionManager);
	}

	private long count(String table) {
		return this.jdbc.sql("select count(*) from " + table + " where article_id = :article")
			.param("article", ARTICLE.value())
			.query(Long.class)
			.single();
	}

}
