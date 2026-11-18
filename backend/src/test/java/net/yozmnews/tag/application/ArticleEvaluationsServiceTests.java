package net.yozmnews.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.tag.ArticleEvaluationView;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.TagProbability;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicStatus;
import net.yozmnews.tag.domain.result.EvaluationRecord;
import net.yozmnews.tag.domain.result.EvaluationResultRepository;
import net.yozmnews.tag.domain.result.StoredTag;
import net.yozmnews.tag.domain.tagset.TagSetRepository;
import net.yozmnews.tag.domain.threshold.ThresholdRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 기사 여럿의 평가 조회와 첫 평가 시각의 기사 조회 — 여럿 조회는 기사 하나의 조회와 같은 평가 · 같은 판정(기본 체계의 태그 기준 0.5 · 저장 기준
 * 0.1)이고, 한 번에 묻는 기사 수에 상한이 있다. 실제 PostgreSQL 과 기본 체계(버전 1)로 본다.
 * <p>
 * 지키는 규칙: tag-R15 · tag-R30
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ArticleEvaluationsServiceTests {

	private static final Instant AT = Instant.parse("2026-11-17T00:00:00Z");

	@Autowired
	ArticleEvaluations evaluations;

	@Autowired
	EvaluationResultRepository results;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TagSetRepository tagSetRepository;

	@Autowired
	ThresholdRepository thresholdRepository;

	private TagSetId first;

	private long nextJudgement = 9_001;

	@BeforeEach
	void start() {
		reset();
		this.first = this.tagSets.activeTagSet().id();
	}

	@AfterEach
	void reset() {
		this.jdbc.sql("delete from article_tags").update();
		this.jdbc.sql("delete from article_evaluations").update();
	}

	@Test
	void findAllJudgesEachArticleLikeFindAndLeavesOutUnevaluatedOnes() {
		// 71: 주제 하나가 태그 기준을 넘고(시장 0.75) 하나는 못 넘는다(행정 0.3) · 지역 하나가 넘는다(국내 0.6)
		insert(71, 4.0, List.of(new StoredTag(MultiTagKind.TOPIC, "economy.markets", 0.75),
				new StoredTag(MultiTagKind.TOPIC, "politics.executive", 0.3),
				new StoredTag(MultiTagKind.REGION, "domestic", 0.6), new StoredTag(MultiTagKind.REGION, "us", 0.2)));
		// 72: 태그 기준을 넘는 주제가 없어 저장 기준 이상의 가장 높은 하나(행정 0.3) — low_confidence
		insert(72, null, List.of(new StoredTag(MultiTagKind.TOPIC, "politics.executive", 0.3)));

		Map<ArticleId, ArticleEvaluationView> found = this.evaluations
			.findAll(List.of(new ArticleId(72), new ArticleId(71), new ArticleId(73), new ArticleId(71)));

		assertThat(found).containsOnlyKeys(new ArticleId(71), new ArticleId(72));
		assertThat(found.get(new ArticleId(71))).isEqualTo(new ArticleEvaluationView(new ArticleId(71), this.first,
				TopicStatus.TAGGED, List.of(new TagProbability("economy.markets", 0.75)), List.of("economy"),
				List.of(new TagProbability("domestic", 0.6)), "straight", 4.0));
		assertThat(found.get(new ArticleId(72))).isEqualTo(new ArticleEvaluationView(new ArticleId(72), this.first,
				TopicStatus.LOW_CONFIDENCE, List.of(new TagProbability("politics.executive", 0.3)), List.of("politics"),
				List.of(), "straight", null));
		assertThat(found.get(new ArticleId(71))).isEqualTo(this.evaluations.find(new ArticleId(71)).orElseThrow());
		assertThat(found.get(new ArticleId(72))).isEqualTo(this.evaluations.find(new ArticleId(72)).orElseThrow());
		assertThat(this.evaluations.findAll(List.of())).isEmpty();
	}

	@Test
	void findAllReadsEachVersionAndItsThresholdsOnceForManyArticles() {
		insert(91, 3.0, List.of(new StoredTag(MultiTagKind.TOPIC, "economy.markets", 0.75)));
		insert(92, 3.0, List.of(new StoredTag(MultiTagKind.TOPIC, "politics.executive", 0.75)));
		insert(93, 3.0, List.of());
		Map<String, Integer> calls = new HashMap<>();
		ArticleEvaluationsService counted = new ArticleEvaluationsService(this.results,
				new TagSetViews(counting(TagSetRepository.class, this.tagSetRepository, calls)),
				counting(ThresholdRepository.class, this.thresholdRepository, calls));

		assertThat(counted.findAll(List.of(new ArticleId(91), new ArticleId(92), new ArticleId(93)))).hasSize(3);

		// 같은 버전의 평가 셋 — 버전 내용 · 기준을 한 번씩만 읽는다(평가마다 읽으면 셋씩)
		assertThat(calls).containsEntry("findById", 1).containsEntry("current", 1);
	}

	@Test
	void findAllTakesAtMostAThousandDistinctArticles() {
		List<ArticleId> thousand = LongStream.rangeClosed(1, 1_000).mapToObj(ArticleId::new).toList();
		List<ArticleId> thousandAndOne = LongStream.rangeClosed(1, 1_001).mapToObj(ArticleId::new).toList();

		List<ArticleId> thousandWithARepeat = new ArrayList<>(thousand);
		thousandWithARepeat.add(new ArticleId(1));

		assertThat(this.evaluations.findAll(thousand)).isEmpty();
		assertThat(this.evaluations.findAll(thousandWithARepeat)).as("겹친 ID 는 한 번으로 센다").isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> this.evaluations.findAll(thousandAndOne));
	}

	@Test
	void firstEvaluatedInReadsThePeriod() {
		insert(81, 3.0, List.of());

		assertThat(this.evaluations.firstEvaluatedIn(EvaluationPeriod.of(AT, AT.plusSeconds(1)).orElseThrow()))
			.containsExactly(new ArticleId(81));
		assertThat(this.evaluations.firstEvaluatedIn(EvaluationPeriod.of(AT.plusSeconds(1), AT.plusSeconds(2))
			.orElseThrow())).isEmpty();
	}

	/** 인터페이스 호출을 메서드 이름마다 센다 — 실제 저장소에 넘긴다 */
	private static <T> T counting(Class<T> type, T delegate, Map<String, Integer> calls) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
			calls.merge(method.getName(), 1, Integer::sum);
			try {
				return method.invoke(delegate, args);
			}
			catch (InvocationTargetException ex) {
				throw ex.getCause();
			}
		}));
	}

	private void insert(long article, @Nullable Double importance, List<StoredTag> tags) {
		this.results.insert(new EvaluationRecord(new ArticleId(article), this.first,
				new TagJudgementId(this.nextJudgement++), "straight", 0.5, Map.of("straight", 0.5), importance,
				Map.of(4, 1.0), tags, AT));
	}

}
