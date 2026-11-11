package net.yozmnews.tag.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TagThresholds;
import net.yozmnews.tag.ThresholdChangeResult;
import net.yozmnews.tag.ThresholdPreviewResult;
import net.yozmnews.tag.ThresholdWarning;
import net.yozmnews.tag.domain.result.EvaluationRecord;
import net.yozmnews.tag.domain.result.EvaluationResultRepository;
import net.yozmnews.tag.domain.result.StoredTag;
import net.yozmnews.tag.domain.tagset.TagSetRepository;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
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
 * 기준 미리보기 — 실제 PostgreSQL 의 활성 버전(기본 체계, 기준 0.5 / 0.1) 평가로 결선된 공개 API 를 부른다. 기간 안 둘(행정부 0.45 —
 * low_confidence, 행정부 0.8 — TAGGED)과 기간 밖 하나(행정부 0.45).
 * <p>
 * 지키는 규칙: tag-R32 · tag-R35
 */
@Import({ TestcontainersConfiguration.class, TransactionSettings.class })
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ThresholdPreviewTests {

	private static final EvaluationPeriod PERIOD = new EvaluationPeriod(Instant.parse("2026-10-27T15:00:00Z"),
			Instant.parse("2026-11-03T15:00:00Z"));

	/** 처음 값(첫 기동 적재 — 시험 실행 시각)과 시험 안의 변경을 모두 덮는 범위 */
	private static final Instant COVERED_FROM = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant COVERED_TO = Instant.parse("2100-01-01T00:00:00Z");

	@Autowired
	TagSetAdministration administration;

	@Autowired
	TagSetDirectory directory;

	@Autowired
	EvaluationResultRepository results;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	TransactionSettings settings;

	@Autowired
	JdbcClient jdbc;

	private TagSetId first;

	@AfterEach
	void reset() {
		TagBackfillRows.removeAll(this.jdbc);
		this.jdbc.sql("delete from article_tags").update();
		this.jdbc.sql("delete from article_evaluations").update();
		this.jdbc.sql("delete from article_tag_judgements").update();
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
		this.settings.seen.clear();
	}

	@BeforeEach
	void evaluateArticles() {
		reset();
		this.first = this.directory.activeTagSet().id();
		insert(1, Instant.parse("2026-10-28T00:00:00Z"), List.of(topic("politics.executive", 0.45)));
		insert(2, Instant.parse("2026-10-29T00:00:00Z"), List.of(topic("politics.executive", 0.8)));
		insert(3, Instant.parse("2026-11-03T15:00:00Z"), List.of(topic("politics.executive", 0.45)));
	}

	@Test
	void thePreviewCountsTheEvaluationsOfTheActiveVersionInThePeriodWhoseJudgementChanges() {
		ThresholdPreviewResult result = preview(MultiTagKind.TOPIC, "0.4", "0.1");

		assertThat(result).isInstanceOf(ThresholdPreviewResult.Previewed.class);
		ThresholdPreviewResult.Previewed previewed = (ThresholdPreviewResult.Previewed) result;
		assertThat(previewed.version()).isEqualTo(this.first);
		assertThat(previewed.current()).isEqualTo(thresholds(MultiTagKind.TOPIC, "0.500", "0.100"));
		assertThat(previewed.proposed()).isEqualTo(thresholds(MultiTagKind.TOPIC, "0.400", "0.100"));
		assertThat(previewed.warnings()).isEmpty();
		// 기간 밖 기사 3 은 세지 않는다. 기사 1 만 low_confidence → TAGGED
		assertThat(previewed.evaluations()).isEqualTo(2);
		assertThat(previewed.changed()).isEqualTo(1);
		assertThat(previewed.before().topicStatus().lowConfidence().count()).isEqualTo(1);
		assertThat(previewed.after().topicStatus().tagged().count()).isEqualTo(2);
	}

	@Test
	void onlyTheEvaluationsOfTheActiveVersionAreCounted() {
		TagSetId second = TagSetVersions.activate(this.administration.versionText(this.first).orElseThrow(),
				this.tagSets, this.thresholds, this.transaction, Instant.parse("2026-11-04T00:00:00Z"));
		insert(second, 4, Instant.parse("2026-10-30T00:00:00Z"), List.of(topic("politics.executive", 0.45)));

		ThresholdPreviewResult.Previewed previewed = (ThresholdPreviewResult.Previewed) preview(MultiTagKind.TOPIC,
				"0.4", "0.1");

		assertThat(previewed.version()).isEqualTo(second);
		// 대조: 버전 1 의 기간 안 평가 둘(기사 1 · 2)은 세지 않는다 — 기사 4 하나만 low_confidence → TAGGED
		assertThat(previewed.evaluations()).isEqualTo(1);
		assertThat(previewed.changed()).isEqualTo(1);
	}

	@Test
	void thePreviewWarnsAsTheChangeWouldAndWritesNothing() {
		ThresholdPreviewResult.Previewed lowered = (ThresholdPreviewResult.Previewed) preview(MultiTagKind.TOPIC, "0.5",
				"0.05");
		ThresholdPreviewResult.Previewed tagBelow = (ThresholdPreviewResult.Previewed) preview(MultiTagKind.REGION,
				"0.05", "0.1");

		assertThat(lowered.warnings()).containsExactly(ThresholdWarning.STORE_THRESHOLD_LOWERED);
		assertThat(tagBelow.warnings()).containsExactlyInAnyOrder(ThresholdWarning.TAG_BELOW_PAST_STORE,
				ThresholdWarning.TAG_BELOW_STORE);
		// 아무것도 쓰지 않았다 — 이력은 처음 값 둘, 지금 값은 그대로
		assertThat(this.administration.thresholdChanges()).hasSize(2);
		assertThat(this.directory.activeThresholds().topic())
			.isEqualTo(thresholds(MultiTagKind.TOPIC, "0.500", "0.100"));
		// 대조: 같은 값의 변경은 같은 경고를 낸다
		ThresholdChangeResult changed = this.administration.changeThresholds(MultiTagKind.REGION,
				new BigDecimal("0.05"), new BigDecimal("0.1"), ChangeActor.OPERATOR);
		assertThat(((ThresholdChangeResult.Changed) changed).warnings()).isEqualTo(tagBelow.warnings());
	}

	@Test
	void aFinishedBackfillOfTheWholeVersionLiftsThePreviewWarningAboutThePastStoreThreshold() {
		lowerTheTopicStoreThresholdFrom02To01();
		TagBackfillRows.insertFinished(this.jdbc, this.first, COVERED_FROM, COVERED_TO, "0.1", 0);

		assertThat(((ThresholdPreviewResult.Previewed) preview(MultiTagKind.TOPIC, "0.15", "0.1")).warnings()).isEmpty();
	}

	@Test
	void withoutABackfillThePreviewWarnsAboutThePastStoreThreshold() {
		// 대조 — 덮음이 없으면 0.2 로 저장된 평가가 남아 있다
		lowerTheTopicStoreThresholdFrom02To01();

		assertThat(((ThresholdPreviewResult.Previewed) preview(MultiTagKind.TOPIC, "0.15", "0.1")).warnings())
			.containsExactly(ThresholdWarning.TAG_BELOW_PAST_STORE);
	}

	@Test
	void bothOverviewsOfThePreviewSeeTheMixedStoreFloorsOfTheirOwnTagKind() {
		// 기간이 처음 값 시각(첫 기동 적재 — 시험 실행 시각)과 변경 시각을 품어야 섞임을 볼 수 있다
		Instant now = Instant.now();
		EvaluationPeriod period = new EvaluationPeriod(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)));
		lowerTheTopicStoreThresholdFrom02To01();

		ThresholdPreviewResult.Previewed previewed = (ThresholdPreviewResult.Previewed) this.administration
			.previewThresholds(MultiTagKind.TOPIC, new BigDecimal("0.4"), new BigDecimal("0.1"), period);

		assertThat(previewed.before().topicProbabilities().storeThresholdChanged()).isTrue();
		// 대조 — 지역 저장 기준은 바꾸지 않았다
		assertThat(previewed.before().regionProbabilities().storeThresholdChanged()).isFalse();
		// 제안을 이력에 더하지 않는다 — 제안 기준의 집계도 지금 바닥 그대로
		assertThat(previewed.after().topicProbabilities().storeThresholdChanged()).isTrue();
		assertThat(previewed.after().regionProbabilities().storeThresholdChanged()).isFalse();
	}

	@Test
	void theSameOrOutOfRangeValuesPreviewNothing() {
		assertThat(preview(MultiTagKind.REGION, "0.50", "0.1000"))
			.isEqualTo(new ThresholdPreviewResult.Unchanged(this.first,
					thresholds(MultiTagKind.REGION, "0.500", "0.100")));
		assertThat(preview(MultiTagKind.TOPIC, "1.5", "0.1")).isEqualTo(new ThresholdPreviewResult.OutOfRange());
		assertThat(preview(MultiTagKind.TOPIC, "0.5", "0.1234")).isEqualTo(new ThresholdPreviewResult.OutOfRange());
	}

	@Test
	void thePreviewReadsInOneReadOnlyRepeatableReadTransaction() {
		// 대조: 미리보기 밖에서 읽으면 이 기록 수단이 기본 격리 수준 · 쓰기 가능을 적는다
		this.thresholds.current(this.first);
		preview(MultiTagKind.TOPIC, "0.4", "0.1");

		assertThat(this.settings.seen).containsExactly(Map.of("isolation", "read committed", "readOnly", "off"),
				Map.of("isolation", "repeatable read", "readOnly", "on"));
	}

	private void lowerTheTopicStoreThresholdFrom02To01() {
		this.administration.changeThresholds(MultiTagKind.TOPIC, new BigDecimal("0.5"), new BigDecimal("0.2"),
				ChangeActor.OPERATOR);
		this.administration.changeThresholds(MultiTagKind.TOPIC, new BigDecimal("0.5"), new BigDecimal("0.1"),
				ChangeActor.OPERATOR);
	}

	private ThresholdPreviewResult preview(MultiTagKind kind, String tag, String store) {
		return this.administration.previewThresholds(kind, new BigDecimal(tag), new BigDecimal(store), PERIOD);
	}

	private void insert(long article, Instant at, List<StoredTag> tags) {
		insert(this.first, article, at, tags);
	}

	private void insert(TagSetId version, long article, Instant at, List<StoredTag> tags) {
		TagJudgementId judgement = TagJudgementRows.insert(this.jdbc, article, "fake", "fake-1", "{}", 1_000, at);
		this.results.insert(new EvaluationRecord(new ArticleId(article), version, judgement, "straight", 0.7,
				Map.of("straight", 0.7), 3.0, Map.of(3, 1.0), tags, at));
	}

	private static StoredTag topic(String key, double probability) {
		return new StoredTag(MultiTagKind.TOPIC, key, probability);
	}

	private static TagThresholds thresholds(MultiTagKind kind, String tag, String store) {
		return new TagThresholds(kind, new BigDecimal(tag), new BigDecimal(store));
	}

}
