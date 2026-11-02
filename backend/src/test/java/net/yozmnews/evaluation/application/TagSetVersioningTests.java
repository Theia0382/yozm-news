package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.evaluation.ChangeActor;
import net.yozmnews.evaluation.LineageKind;
import net.yozmnews.evaluation.MultiTagKind;
import net.yozmnews.evaluation.TagKind;
import net.yozmnews.evaluation.TagLineageView;
import net.yozmnews.evaluation.TagRef;
import net.yozmnews.evaluation.TagSetActivationResult;
import net.yozmnews.evaluation.TagSetChanges;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.TagSetProblemView;
import net.yozmnews.evaluation.TagSetReviewResult;
import net.yozmnews.evaluation.TagSetVersionView;
import net.yozmnews.evaluation.TagThresholds;
import net.yozmnews.evaluation.ThresholdDifference;
import net.yozmnews.evaluation.domain.queue.EvaluationQueueRepository;
import net.yozmnews.evaluation.domain.tagset.TagSetRepository;
import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 확인 · 활성화 — 실제 PostgreSQL 과 실제 기사 조회로, 확인이 아무것도 쓰지 않는지, 활성화가 새 버전 · 처음 태그 기준 · 저장 기준 ·
 * 계보 · 재평가 대기를 한 번에 쓰는지 본다.
 * <p>
 * 기사 넷은 시험 시각(2032-01-10) 기준 5일 전(A) · 정확히 7일 전(B — 경계) · 3일 전(C) · 8일 전(D)에 처음 보았다. 다른 시험이 남긴 기사와
 * 겹치지 않게 먼 시각을 쓴다.
 * <p>
 * 지키는 규칙: evaluation-R16 · evaluation-R22 ~ evaluation-R25
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TagSetVersioningTests {

	private static final Instant NOW = Instant.parse("2032-01-10T00:00:00Z");

	private static final String US = "description: \"미국\"";

	private static final List<String> TABLES = List.of("tag_sets", "tags", "importance_rubrics", "tag_key_lineage",
			"tag_set_thresholds", "tag_set_threshold_changes", "evaluation_queue", "article_evaluations");

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	EvaluationQueueRepository queue;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcClient jdbc;

	private TagSetVersioning versioning;

	private TagSetId first;

	private long a;

	private long b;

	private long c;

	private long d;

	@BeforeEach
	void startFromTheFirstVersionWithFourArticles() {
		clean();
		this.versioning = new TagSetVersioning(this.tagSets, this.thresholds, this.queue, this.articles,
				new TransactionTemplate(this.transactionManager), InstantSource.fixed(NOW));
		this.first = this.tagSets.findByActiveTrue().orElseThrow().id();
		this.a = article(NOW.minus(Duration.ofDays(5)));
		this.b = article(NOW.minus(Duration.ofDays(7)));
		this.c = article(NOW.minus(Duration.ofDays(3)));
		this.d = article(NOW.minus(Duration.ofDays(8)));
	}

	@AfterEach
	void restore() {
		clean();
	}

	@Test
	void aReviewIsReadyWithTheChangesAndTheRecentArticleCountAndWritesNothing() {
		Map<String, Long> before = counts();

		TagSetReviewResult result = this.versioning.review(firstText().replace(US, "description: \"미국 · 미국령\""), 7);

		assertThat(result).isEqualTo(new TagSetReviewResult.Ready(new TagSetChanges(List.of(), List.of(),
				List.of(new TagRef(TagKind.REGION, "us")), List.of(), false, false, List.of()), 3));
		assertThat(counts()).isEqualTo(before);
	}

	@Test
	void otherReviewOutcomesWriteNothing() {
		Map<String, Long> before = counts();
		String text = firstText();

		assertThat(this.versioning.review(text, 0)).isEqualTo(new TagSetReviewResult.Unchanged());
		assertThat(this.versioning.review(text.replace("key: \"us\"", "key: \"US\""), 0))
			.isInstanceOfSatisfying(TagSetReviewResult.Invalid.class, invalid -> assertThat(invalid.problems())
				.extracting(TagSetProblemView::path, TagSetProblemView::code)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("regions[2].key", "INVALID_KEY")));
		assertThat(this.versioning.review(text.replace("key: \"us\"", "key: \"usa\"")
				+ "lineage:\n  - { kind: region, change: rename, from: [nowhere], to: [usa] }\n", 0))
			.isInstanceOfSatisfying(TagSetReviewResult.Invalid.class, invalid -> {
				assertThat(invalid.problems()).extracting(TagSetProblemView::path, TagSetProblemView::code)
					.containsExactly(org.assertj.core.groups.Tuple.tuple("lineage[0]", "LINEAGE_SOURCE_MISSING"));
				assertThat(invalid.problems().get(0).line()).isEqualTo(lines(text) + 2);
				assertThat(invalid.problems().get(0).column()).isEqualTo(5);
			});
		assertThat(this.versioning.review(text, 31)).isEqualTo(new TagSetReviewResult.DaysOutOfRange());
		assertThat(this.versioning.review(text, -1)).isEqualTo(new TagSetReviewResult.DaysOutOfRange());
		assertThat(counts()).isEqualTo(before);
	}

	@Test
	void anActivationStoresTheNextVersionAndRequeuesTheArticlesOfThePeriod() {
		this.jdbc.sql("""
				insert into evaluation_queue (article_id, enqueued_at, eligible_at, attempts, failure, last_error)
				values (:b, '2032-01-01T00:00:00Z', '2032-01-01T00:30:00Z', 0, 'ARTICLE_REJECTED', '거부')
				""").param("b", this.b).update();
		this.jdbc.sql("""
				insert into evaluation_queue (article_id, enqueued_at, eligible_at, attempts)
				values (:c, '2032-01-09T23:50:00Z', '2032-01-10T00:20:00Z', 0)
				""").param("c", this.c).update();
		long evaluationsBefore = TagSetRows.count(this.jdbc, "article_evaluations");
		String text = firstText().replace("key: \"japan\"", "key: \"nippon\"")
			.replaceFirst("tagThreshold: 0.5", "tagThreshold: 0.6")
				+ "lineage:\n  - { kind: region, change: rename, from: [japan], to: [nippon] }\n";

		TagSetActivationResult result = this.versioning.activate(text, 7, ChangeActor.OPERATOR);

		TagSetId second = this.tagSets.findByActiveTrue().orElseThrow().id();
		assertThat(result).isEqualTo(new TagSetActivationResult.Activated(
				new TagSetVersionView(second, 2, true, ChangeActor.OPERATOR, NOW),
				new TagSetChanges(List.of(), List.of(), List.of(),
						List.of(new TagLineageView(TagKind.REGION, LineageKind.RENAME, List.of("japan"), List.of("nippon"))),
						false, false,
						List.of(new ThresholdDifference(new TagThresholds(MultiTagKind.TOPIC, new java.math.BigDecimal("0.500"),
								new java.math.BigDecimal("0.100")), new TagThresholds(MultiTagKind.TOPIC,
										new java.math.BigDecimal("0.600"), new java.math.BigDecimal("0.100"))))),
				2));
		assertThat(this.tagSets.isActive(this.first.value())).isFalse();
		assertThat(this.thresholds.current(second).topic().tagThreshold()).isEqualByComparingTo("0.6");
		assertThat(this.jdbc.sql("""
				select count(*) from tag_set_threshold_changes
				where tag_set_id = :version and actor = 'OPERATOR' and previous_tag_threshold is null
				""").param("version", second.value()).query(Long.class).single()).isEqualTo(2);
		assertThat(TagSetRows.count(this.jdbc, "tag_key_lineage")).isEqualTo(1);
		// A 는 새로 넣고 B 는 실패를 지워 되돌린다 — 평가 가능 시각은 활성화 시각이다
		assertThat(queueRow(this.a)).containsEntry("attempts", 0).containsEntry("failure", null);
		assertThat(eligible(this.a)).isEqualTo(NOW);
		assertThat(queueRow(this.b)).containsEntry("attempts", 0).containsEntry("failure", null)
			.containsEntry("last_error", null);
		assertThat(eligible(this.b)).isEqualTo(NOW);
		// C 는 대기 중이라 그대로, D 는 기간 밖이라 넣지 않는다. 평가 행은 건드리지 않는다
		assertThat(eligible(this.c)).isEqualTo(Instant.parse("2032-01-10T00:20:00Z"));
		assertThat(this.jdbc.sql("select count(*) from evaluation_queue where article_id = :d").param("d", this.d)
			.query(Long.class).single()).isZero();
		assertThat(TagSetRows.count(this.jdbc, "article_evaluations")).isEqualTo(evaluationsBefore);
	}

	@Test
	void anActivationWithoutAPeriodRequeuesNothingAndNumbersKeepRising() {
		Map<String, Long> before = counts();

		TagSetActivationResult second = this.versioning.activate(firstText().replace(US, "description: \"미국 · 미국령\""), 0,
				ChangeActor.OPERATOR);
		TagSetActivationResult third = this.versioning.activate(this.versioning
			.versionText(this.tagSets.findByActiveTrue().orElseThrow().id())
			.orElseThrow()
			.replace("description: \"미국 · 미국령\"", "description: \"미국 · 미국령 · 괌\""), 0, ChangeActor.OPERATOR);

		assertThat(second).isInstanceOfSatisfying(TagSetActivationResult.Activated.class, activated -> {
			assertThat(activated.version().number()).isEqualTo(2);
			assertThat(activated.requeued()).isZero();
		});
		assertThat(third).isInstanceOfSatisfying(TagSetActivationResult.Activated.class,
				activated -> assertThat(activated.version().number()).isEqualTo(3));
		assertThat(counts().get("evaluation_queue")).isEqualTo(before.get("evaluation_queue"));
		assertThat(this.versioning.versions()).extracting(TagSetVersionView::number, TagSetVersionView::active)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(3, true), org.assertj.core.groups.Tuple.tuple(2, false),
					org.assertj.core.groups.Tuple.tuple(1, false));
	}

	@Test
	void anActivationThatIsUnchangedInvalidOrOutOfRangeWritesNothing() {
		Map<String, Long> before = counts();
		String text = firstText();

		assertThat(this.versioning.activate(text.replaceFirst("tagThreshold: 0.5", "tagThreshold: 0.6"), 7,
				ChangeActor.OPERATOR)).isEqualTo(new TagSetActivationResult.Unchanged());
		assertThat(this.versioning.activate(text + "lineage:\n  - { kind: region, change: rename, from: [japan], to: [us] }\n",
				7, ChangeActor.OPERATOR)).isInstanceOf(TagSetActivationResult.Invalid.class);
		assertThat(this.versioning.activate("questions: [", 7, ChangeActor.OPERATOR))
			.isInstanceOf(TagSetActivationResult.Invalid.class);
		assertThat(this.versioning.activate(text, 31, ChangeActor.OPERATOR))
			.isEqualTo(new TagSetActivationResult.DaysOutOfRange());
		assertThat(counts()).isEqualTo(before);
		assertThat(this.tagSets.isActive(this.first.value())).isTrue();
	}

	private String firstText() {
		return this.versioning.versionText(this.first).orElseThrow();
	}

	private static int lines(String text) {
		return (int) text.chars().filter(ch -> ch == '\n').count();
	}

	private Map<String, Long> counts() {
		Map<String, Long> counts = new java.util.LinkedHashMap<>();
		TABLES.forEach(table -> counts.put(table, TagSetRows.count(this.jdbc, table)));
		return counts;
	}

	private long article(Instant firstSeen) {
		String url = "https://versioning.example/" + UUID.randomUUID();
		return this.jdbc.sql("""
				insert into articles (canonical_url, url_hash, title, title_key_hash, published_at, published_at_estimated,
				    first_seen_at)
				values (:url, sha256(convert_to(:url, 'UTF8')), '체계 변경 기사', sha256(convert_to(:url, 'UTF8')), :seen, false,
				    :seen)
				returning id
				""").param("url", url).param("seen", firstSeen.atOffset(ZoneOffset.UTC)).query(Long.class).single();
	}

	private Map<String, Object> queueRow(long article) {
		return this.jdbc.sql("select attempts, failure, last_error from evaluation_queue where article_id = :article")
			.param("article", article)
			.query()
			.singleRow();
	}

	private Instant eligible(long article) {
		return this.jdbc.sql("select eligible_at from evaluation_queue where article_id = :article")
			.param("article", article)
			.query(OffsetDateTime.class)
			.single()
			.toInstant();
	}

	private void clean() {
		this.jdbc.sql("""
				delete from evaluation_queue where article_id in
				    (select id from articles where canonical_url like 'https://versioning.example/%')
				""").update();
		this.jdbc.sql("delete from articles where canonical_url like 'https://versioning.example/%'").update();
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
	}

}
