package net.yozmnews.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.tag.DistributionShare;
import net.yozmnews.tag.EvaluationCursor;
import net.yozmnews.tag.EvaluationDetailView;
import net.yozmnews.tag.EvaluationFilter;
import net.yozmnews.tag.EvaluationId;
import net.yozmnews.tag.EvaluationListResult;
import net.yozmnews.tag.EvaluationListing;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.EvaluationReview;
import net.yozmnews.tag.EvaluationRow;
import net.yozmnews.tag.EvaluationSampleResult;
import net.yozmnews.tag.EvaluationSampleView;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.NamedTag;
import net.yozmnews.tag.StoredTagView;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagName;
import net.yozmnews.tag.TagRef;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicStatus;
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
 * 평가 검수 — 실제 PostgreSQL 에 넣은 평가 행을 결선된 서비스로 목록 · 표본 · 상세로 읽는다. 기본 체계(버전 1, 처음 기준 0.5 / 0.1)를 쓰고,
 * 테스트마다 평가 행 · 버전 · 기준을 처음으로 되돌린다. 기사 행은 없다 — 평가는 기사 ID 만 가리킨다.
 * <p>
 * 기간 안 버전 1 평가 넷(평가 시각 순): ① 기사 1 — 행정부 0.8 · 국회 0.4(저장됐지만 태그 기준 아래) · 국내 0.9, TAGGED ② 기사 2 — 금융
 * 0.3, LOW_CONFIDENCE ③ 기사 3 — ② 와 같은 시각, 국회 0.7, TAGGED ④ 기사 4 — 미국 0.6 만, UNCLASSIFIED, 대표 형식 오피니언. 기간 밖
 * 둘(기사 5 · 6)과 버전 2 의 기간 안 평가 하나(기사 7)는 나오지 않는다.
 * <p>
 * 지키는 규칙: tag-R33 · tag-R34
 */
@Import({ TestcontainersConfiguration.class, TransactionSettings.class })
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class EvaluationReviewTests {

	private static final EvaluationPeriod PERIOD = new EvaluationPeriod(Instant.parse("2026-10-27T15:00:00Z"),
			Instant.parse("2026-11-03T15:00:00Z"));

	private static final Instant SAME = Instant.parse("2026-10-30T00:00:00Z");

	/** 형식 분포 — 기본 체계의 선택지 여섯 */
	private static final Map<String, Double> STRAIGHT = Map.of("straight", 0.7, "analysis", 0.1, "opinion", 0.05,
			"interview", 0.05, "press-release", 0.05, "brief", 0.05);

	private static final Map<String, Double> OPINION = Map.of("straight", 0.1, "analysis", 0.1, "opinion", 0.6,
			"interview", 0.1, "press-release", 0.05, "brief", 0.05);

	private static final Map<Integer, Double> IMPORTANCE = Map.of(1, 0.0, 2, 0.25, 3, 0.75, 4, 0.0, 5, 0.0);

	@Autowired
	EvaluationReview review;

	@Autowired
	TagSetAdministration tagSetAdministration;

	@Autowired
	TagSetDirectory directory;

	@Autowired
	EvaluationResultRepository results;

	@Autowired
	TagSetRepository tagSets;

	@Autowired
	ThresholdRepository thresholds;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	TransactionSettings settings;

	@Autowired
	JdbcClient jdbc;

	private TagSetId first;

	@AfterEach
	void reset() {
		this.jdbc.sql("delete from article_tags").update();
		this.jdbc.sql("delete from article_evaluations").update();
		this.jdbc.sql("delete from article_tag_judgements").update();
		TagSetRows.removeVersionsAfterFirst(this.jdbc);
		TagSetRows.resetThresholds(this.jdbc);
		this.settings.seen.clear();
	}

	@BeforeEach
	void evaluateArticlesInAndAroundThePeriod() {
		reset();
		this.first = this.directory.activeTagSet().id();
		insert(this.first, 1, Instant.parse("2026-10-28T00:00:00Z"), "straight", 0.7, STRAIGHT,
				List.of(topic("politics.executive", 0.8), topic("politics.legislature", 0.4), region("domestic", 0.9)));
		insert(this.first, 2, SAME, "straight", 0.7, STRAIGHT, List.of(topic("economy.finance", 0.3)));
		insert(this.first, 3, SAME, "straight", 0.7, STRAIGHT, List.of(topic("politics.legislature", 0.7)));
		insert(this.first, 4, Instant.parse("2026-11-01T00:00:00Z"), "opinion", 0.6, OPINION,
				List.of(region("us", 0.6)));
		insert(this.first, 5, Instant.parse("2026-10-27T14:59:59Z"), "straight", 0.7, STRAIGHT,
				List.of(topic("politics.executive", 0.9)));
		insert(this.first, 6, Instant.parse("2026-11-03T15:00:00Z"), "straight", 0.7, STRAIGHT,
				List.of(topic("politics.executive", 0.9)));
		TagSetId second = TagSetVersions.activate(this.tagSetAdministration.versionText(this.first).orElseThrow(),
				this.tagSets, this.thresholds, this.transaction, Instant.parse("2026-11-04T00:00:00Z"));
		insert(second, 7, Instant.parse("2026-10-31T00:00:00Z"), "straight", 0.7, STRAIGHT,
				List.of(topic("politics.executive", 0.9)));
		// 준비의 원문 내보내기도 지금 기준을 읽는다 — 시험이 부른 것만 남긴다
		this.settings.seen.clear();
	}

	@Test
	void theListIsNewestFirstWithinTheVersionAndThePeriodAndPagesWithACursor() {
		EvaluationListing page = listed(EvaluationFilter.NONE, null, 2);

		// 같은 시각의 ② · ③ 은 평가 ID 내림차순 — 나중에 넣은 ③ 이 앞이다
		assertThat(articles(page)).containsExactly(4L, 3L);
		assertThat(page.next()).isNotNull();
		assertThat(page.next().id()).isEqualTo(page.rows().get(1).id());
		assertThat(page.next().evaluatedAt()).isEqualTo(SAME);

		EvaluationListing rest = listed(EvaluationFilter.NONE, page.next(), 2);

		assertThat(articles(rest)).containsExactly(2L, 1L);
		// 맞는 평가가 더 없으면 커서가 없다 — 쪽 크기와 남은 수가 같아도
		assertThat(rest.next()).isNull();
		assertThat(rest.version()).isEqualTo(this.first);
		assertThat(rest.from()).isEqualTo(PERIOD.from());
		assertThat(rest.to()).isEqualTo(PERIOD.to());
	}

	@Test
	void aTopicTagIsFilteredByTheQueryTimeJudgementNotByTheStoredRow() {
		// 기사 1 은 국회 저장 행이 있지만(필요조건은 지난다) 0.4 라 붙지 않았다 — 나오지 않는다
		assertThat(articles(listed(tag(TagKind.TOPIC, "politics.legislature"), null, 50))).containsExactly(3L);
		assertThat(articles(listed(tag(TagKind.TOPIC, "politics"), null, 50))).containsExactly(3L, 1L);
		// low_confidence 로 붙은 금융 하나 — 대분류로도 나온다
		assertThat(articles(listed(tag(TagKind.TOPIC, "economy"), null, 50))).containsExactly(2L);
		assertThat(articles(listed(tag(TagKind.REGION, "domestic"), null, 50))).containsExactly(1L);
		// 형식은 저장된 대표 형식으로 좁히고, 판정은 저장 행 필요조건이 없다
		assertThat(articles(listed(tag(TagKind.FORMAT, "opinion"), null, 50))).containsExactly(4L);
		assertThat(articles(listed(tag(TagKind.FORMAT, "straight"), null, 50))).containsExactly(3L, 2L, 1L);
		assertThat(articles(listed(new EvaluationFilter(null, TopicStatus.UNCLASSIFIED), null, 50)))
			.containsExactly(4L);
		assertThat(articles(listed(new EvaluationFilter(null, TopicStatus.LOW_CONFIDENCE), null, 50)))
			.containsExactly(2L);
		assertThat(articles(listed(new EvaluationFilter(new TagRef(TagKind.TOPIC, "politics"), TopicStatus.TAGGED),
				null, 50))).containsExactly(3L, 1L);
	}

	@Test
	void aFilteredListPagesOverTheMatchingEvaluationsOnly() {
		EvaluationListing page = listed(tag(TagKind.TOPIC, "politics"), null, 1);

		assertThat(articles(page)).containsExactly(3L);
		assertThat(page.next()).isNotNull();

		EvaluationListing rest = listed(tag(TagKind.TOPIC, "politics"), page.next(), 1);

		assertThat(articles(rest)).containsExactly(1L);
		assertThat(rest.next()).isNull();
	}

	@Test
	void theRowCarriesTheJudgementWithNamesOfTheVersion() {
		EvaluationRow row = listed(tag(TagKind.REGION, "domestic"), null, 50).rows().getFirst();

		assertThat(row.articleId()).isEqualTo(new ArticleId(1));
		assertThat(row.evaluatedAt()).isEqualTo(Instant.parse("2026-10-28T00:00:00Z"));
		assertThat(row.topicStatus()).isEqualTo(TopicStatus.TAGGED);
		assertThat(row.topics()).containsExactly(new NamedTag("politics.executive", "대통령실·행정부", 0.8));
		assertThat(row.topicCategories()).containsExactly(new TagName("politics", "정치"));
		assertThat(row.regions()).containsExactly(new NamedTag("domestic", "국내", 0.9));
		assertThat(row.format()).isEqualTo(new NamedTag("straight", "스트레이트", 0.7));
		assertThat(row.importance()).isEqualTo(2.75);
	}

	@Test
	void anUnknownTagVersionOrPageSizeReadsNothing() {
		assertThat(this.review.list(this.first, PERIOD, tag(TagKind.TOPIC, "no-such-topic"), null, 10))
			.isEqualTo(new EvaluationListResult.UnknownTag(new TagRef(TagKind.TOPIC, "no-such-topic")));
		assertThat(this.review.list(new TagSetId(999_999), PERIOD, EvaluationFilter.NONE, null, 10))
			.isEqualTo(new EvaluationListResult.UnknownVersion());
		assertThat(this.review.list(this.first, PERIOD, EvaluationFilter.NONE, null, 0))
			.isEqualTo(new EvaluationListResult.LimitOutOfRange());
		assertThat(this.review.list(this.first, PERIOD, EvaluationFilter.NONE, null, 51))
			.isEqualTo(new EvaluationListResult.LimitOutOfRange());
		// 경계: 상한 그 자체는 받는다
		assertThat(this.review.list(this.first, PERIOD, EvaluationFilter.NONE, null, 50))
			.isInstanceOf(EvaluationListResult.Listed.class);
	}

	@Test
	void theSampleIsTheSameForTheSameSeedAndASmallerSampleIsItsPrefix() {
		EvaluationSampleView all = sampled(42, 10);

		assertThat(all.population()).isEqualTo(4);
		assertThat(articles(all.rows())).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
		assertThat(sampled(42, 10).rows()).isEqualTo(all.rows());
		// 해시가 작은 것부터 고르므로 작은 표본은 큰 표본의 앞부분이다
		assertThat(sampled(42, 2).rows()).isEqualTo(all.rows().subList(0, 2));
		assertThat(all.seed()).isEqualTo(42);
		assertThat(all.version()).isEqualTo(this.first);
	}

	@Test
	void aSampleOutOfRangeOrOfAnUnknownVersionReadsNothing() {
		assertThat(this.review.sample(this.first, PERIOD, 42, 0))
			.isEqualTo(new EvaluationSampleResult.SizeOutOfRange());
		assertThat(this.review.sample(this.first, PERIOD, 42, 201))
			.isEqualTo(new EvaluationSampleResult.SizeOutOfRange());
		assertThat(this.review.sample(this.first, PERIOD, -1, 10))
			.isEqualTo(new EvaluationSampleResult.SeedOutOfRange());
		assertThat(this.review.sample(this.first, PERIOD, (1L << 53), 10))
			.isEqualTo(new EvaluationSampleResult.SeedOutOfRange());
		assertThat(this.review.sample(new TagSetId(999_999), PERIOD, 42, 10))
			.isEqualTo(new EvaluationSampleResult.UnknownVersion());
		// 경계: 상한 그 자체는 받는다
		assertThat(this.review.sample(this.first, PERIOD, (1L << 53) - 1, 200))
			.isInstanceOf(EvaluationSampleResult.Sampled.class);
	}

	@Test
	void theDetailHasEveryStoredRowWithWhetherItIsAttachedAndTheDistributions() {
		EvaluationDetailView detail = this.review.detail(evaluationOf(1)).orElseThrow();

		assertThat(detail.articleId()).isEqualTo(new ArticleId(1));
		assertThat(detail.version()).isEqualTo(this.first);
		assertThat(detail.versionNumber()).isEqualTo(1);
		assertThat(detail.evaluator()).isEqualTo("fake");
		assertThat(detail.modelVersion()).isEqualTo("fake-1");
		assertThat(detail.inputTokens()).isEqualTo(1_234);
		assertThat(detail.evaluatedAt()).isEqualTo(Instant.parse("2026-10-28T00:00:00Z"));
		assertThat(detail.thresholds().topic().tagThreshold()).isEqualByComparingTo(new BigDecimal("0.5"));
		assertThat(detail.topicStatus()).isEqualTo(TopicStatus.TAGGED);
		// 체계 순 — 행정부가 국회보다 앞이다. 국회는 저장됐지만 붙지 않았다
		assertThat(detail.topics()).containsExactly(new StoredTagView("politics.executive", "대통령실·행정부", 0.8, true),
				new StoredTagView("politics.legislature", "국회·정당·선거", 0.4, false));
		assertThat(detail.topicCategories()).containsExactly(new TagName("politics", "정치"));
		assertThat(detail.regions()).containsExactly(new StoredTagView("domestic", "국내", 0.9, true));
		assertThat(detail.formats()).containsExactly(new DistributionShare("straight", "스트레이트", 0.7),
				new DistributionShare("analysis", "분석·해설", 0.1), new DistributionShare("opinion", "오피니언·칼럼", 0.05),
				new DistributionShare("interview", "인터뷰", 0.05),
				new DistributionShare("press-release", "보도자료·홍보", 0.05), new DistributionShare("brief", "단신·공지", 0.05));
		assertThat(detail.format()).isEqualTo("straight");
		assertThat(detail.importanceLevels()).extracting(DistributionShare::key, DistributionShare::probability)
			.containsExactly(tuple("1", 0.0), tuple("2", 0.25),
					tuple("3", 0.75), tuple("4", 0.0),
					tuple("5", 0.0));
		assertThat(detail.importanceLevels().get(2).name())
			.isEqualTo("영향이 여러 업계 · 지역 · 집단에 걸치지만 사회 전반의 제도나 흐름은 바꾸지 않는 사안, 결과가 아직 정해지지 않은 다툼 · 주장");
		assertThat(detail.importance()).isEqualTo(2.75);
	}

	@Test
	void theDetailOfAnUnknownEvaluationIsEmpty() {
		assertThat(this.review.detail(new EvaluationId(999_999_999))).isEmpty();
	}

	@Test
	void theReviewReadsInOneReadOnlyRepeatableReadTransaction() {
		// 대조: 검수 밖에서 읽으면 이 기록 수단이 기본 격리 수준 · 쓰기 가능을 적는다
		this.thresholds.current(this.first);
		listed(EvaluationFilter.NONE, null, 10);
		sampled(42, 10);
		this.review.detail(evaluationOf(1)).orElseThrow();

		Map<String, String> snapshot = Map.of("isolation", "repeatable read", "readOnly", "on");
		assertThat(this.settings.seen).containsExactly(Map.of("isolation", "read committed", "readOnly", "off"),
				snapshot, snapshot, snapshot);
	}

	private EvaluationListing listed(EvaluationFilter filter, EvaluationCursor after,
			int limit) {
		EvaluationListResult result = this.review.list(this.first, PERIOD, filter, after, limit);
		assertThat(result).isInstanceOf(EvaluationListResult.Listed.class);
		return ((EvaluationListResult.Listed) result).listing();
	}

	private EvaluationSampleView sampled(long seed, int size) {
		EvaluationSampleResult result = this.review.sample(this.first, PERIOD, seed, size);
		assertThat(result).isInstanceOf(EvaluationSampleResult.Sampled.class);
		return ((EvaluationSampleResult.Sampled) result).sample();
	}

	private EvaluationId evaluationOf(long article) {
		return new EvaluationId(this.jdbc.sql("select id from article_evaluations where article_id = :article")
			.param("article", article)
			.query(Long.class)
			.single());
	}

	private static List<Long> articles(EvaluationListing listing) {
		return articles(listing.rows());
	}

	private static List<Long> articles(List<EvaluationRow> rows) {
		return rows.stream().map(row -> row.articleId().value()).toList();
	}

	private static EvaluationFilter tag(TagKind kind, String key) {
		return new EvaluationFilter(new TagRef(kind, key), null);
	}

	private void insert(TagSetId version, long article, Instant at, String format, double formatProbability,
			Map<String, Double> formats, List<StoredTag> tags) {
		TagJudgementId judgement = TagJudgementRows.insert(this.jdbc, article, "fake", "fake-1", "{}", 1_234, at);
		this.results.insert(new EvaluationRecord(new ArticleId(article), version, judgement, format, formatProbability,
				formats, 2.75, IMPORTANCE, tags, at));
	}

	private static StoredTag topic(String key, double probability) {
		return new StoredTag(MultiTagKind.TOPIC, key, probability);
	}

	private static StoredTag region(String key, double probability) {
		return new StoredTag(MultiTagKind.REGION, key, probability);
	}

}
