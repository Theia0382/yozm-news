package net.yozmnews.tag.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.evaluation.TagJudgementId;
import net.yozmnews.platform.ChangeActor;
import net.yozmnews.tag.CategoryCoverage;
import net.yozmnews.tag.EvaluationPeriod;
import net.yozmnews.tag.MultiTagKind;
import net.yozmnews.tag.SubtopicCoverage;
import net.yozmnews.tag.TagSetAdministration;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.tag.TopicCoverage;
import net.yozmnews.tag.TopicCoverageResult;
import net.yozmnews.tag.TopicCoverageView;
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
 * 주제별 보도량 — 실제 PostgreSQL 에 넣은 평가 행을 결선된 활성 체계 · 기준 · 계보 · 저장소로 센다. 기본 체계(버전 1, 처음 태그 기준 · 저장
 * 기준 0.5 / 0.1)를 쓰고, 테스트마다 평가 행 · 버전 · 기준을 처음으로 되돌린다.
 * <p>
 * 버전 1 평가 다섯: ① 10-28 금융 0.8 ② 10-29 금융 0.3(low_confidence) ③ 10-30 행정부 0.9 · 국회 0.7 ④ 10-20 행정부 0.9(기간 전 — 첫 평가)
 * ⑤ 10-31 주제 없음(미분류). 기간은 {@code Asia/Seoul} 10-28 ~ 11-03 이다.
 * <p>
 * 지키는 규칙: tag-R30
 */
@Import({ TestcontainersConfiguration.class, TransactionSettings.class })
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TopicCoverageTests {

	private static final EvaluationPeriod PERIOD = new EvaluationPeriod(Instant.parse("2026-10-27T15:00:00Z"),
			Instant.parse("2026-11-03T15:00:00Z"));

	@Autowired
	TopicCoverage coverage;

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
	void evaluateFiveArticles() {
		reset();
		this.first = this.directory.activeTagSet().id();
		insert(1, this.first, "2026-10-28T00:00:00Z", List.of(topic("economy.finance", 0.8)));
		insert(2, this.first, "2026-10-29T00:00:00Z", List.of(topic("economy.finance", 0.3)));
		insert(3, this.first, "2026-10-30T00:00:00Z",
				List.of(topic("politics.executive", 0.9), topic("politics.legislature", 0.7)));
		insert(4, this.first, "2026-10-20T00:00:00Z", List.of(topic("politics.executive", 0.9)));
		insert(5, this.first, "2026-10-31T00:00:00Z", List.of());
	}

	@Test
	void articlesOfAnOlderVersionFollowTheLineageToTheActiveKeys() {
		// 버전 2: 금융의 키를 banking 으로 바꾼다. ④ 는 버전 2 로 기간 안에 재평가됐지만 첫 평가가 기간 전이다
		TagSetId second = TagSetVersions.activate(
				this.tagSetAdministration.versionText(this.first)
					.orElseThrow()
					.replace("key: \"finance\"", "key: \"banking\"")
						+ "lineage:\n  - { kind: topic, change: rename, from: [economy.finance], to: [economy.banking] }\n",
				this.tagSets, this.thresholds, this.transaction, Instant.parse("2026-10-30T12:00:00Z"));
		insert(4, second, "2026-10-31T00:00:00Z", List.of(topic("economy.banking", 0.9)));
		// ⑤ 는 버전 2 로 다시 평가돼 행정부가 붙는다 — 한 조회에 두 버전의 평가가 섞이고, ⑤ 는 최신(버전 2) 평가로 센다
		insert(5, second, "2026-11-01T00:00:00Z", List.of(topic("politics.executive", 0.9)));

		TopicCoverageView view = counted(Set.of());

		assertThat(view.activeVersion()).isEqualTo(second);
		assertThat(view.articles()).isEqualTo(4);
		assertThat(view.withoutActiveTopic()).isZero();
		// low_confidence(②)도 센다. ① · ② 는 버전 1 의 finance 에서 활성 banking 으로 이어진다
		assertThat(category(view, "economy").articles()).isEqualTo(2);
		assertThat(category(view, "economy").subtopics()).contains(new SubtopicCoverage("economy.banking", "금융", 2));
		// 정치 2(③ · ⑤), 행정부 2(③ · ⑤), 국회 1(③)
		assertThat(category(view, "politics").articles()).isEqualTo(2);
		assertThat(category(view, "politics").subtopics()).contains(
				new SubtopicCoverage("politics.executive", "대통령실·행정부", 2),
				new SubtopicCoverage("politics.legislature", "국회·정당·선거", 1));
	}

	@Test
	void changingTheThresholdsOfTheVersionChangesItsCoverageAtQueryTime() {
		this.tagSetAdministration.changeThresholds(MultiTagKind.TOPIC, new BigDecimal("0.85"), new BigDecimal("0.1"),
				ChangeActor.OPERATOR);

		// 태그 기준 0.85 면 ③ 의 국회 0.7 은 붙지 않고, ① 의 금융 0.8 은 low_confidence 로 남는다
		TopicCoverageView view = counted(Set.of());
		assertThat(category(view, "politics").subtopics())
			.contains(new SubtopicCoverage("politics.legislature", "국회·정당·선거", 0));
		assertThat(category(view, "economy").articles()).isEqualTo(2);
	}

	@Test
	void chosenTopicsNarrowTheResult() {
		assertThat(counted(Set.of("politics.legislature")).categories()).containsExactly(new CategoryCoverage(
				"politics", "정치", 1, List.of(new SubtopicCoverage("politics.legislature", "국회·정당·선거", 1))));
	}

	@Test
	void topicsMissingFromTheActiveVersionAreRefused() {
		assertThat(this.coverage.count(PERIOD, Set.of("economy", "economy.banking")))
			.isEqualTo(new TopicCoverageResult.UnknownTopics(Set.of("economy.banking")));
		// 없는 키는 사전 순으로 온다 — 응답 문구가 요청마다 같다
		TopicCoverageResult.UnknownTopics unknown = (TopicCoverageResult.UnknownTopics) this.coverage.count(PERIOD,
				Set.of("zeta", "politics", "economy.banking", "alpha", "politics.president", "mu.none"));
		assertThat(unknown.keys()).containsExactly("alpha", "economy.banking", "mu.none", "politics.president", "zeta");
	}

	@Test
	void theCoverageIsCountedInOneReadOnlyRepeatableReadTransaction() {
		// 대조: 집계 밖에서 읽으면 이 기록 수단이 기본 격리 수준 · 쓰기 가능을 적는다
		this.thresholds.current(this.first);
		counted(Set.of());

		assertThat(this.settings.seen).containsExactly(Map.of("isolation", "read committed", "readOnly", "off"),
				Map.of("isolation", "repeatable read", "readOnly", "on"));
	}

	private TopicCoverageView counted(Set<String> topics) {
		return switch (this.coverage.count(PERIOD, topics)) {
			case TopicCoverageResult.Counted counted -> counted.view();
			case TopicCoverageResult.UnknownTopics unknown -> throw new AssertionError("없는 주제: " + unknown.keys());
		};
	}

	private static CategoryCoverage category(TopicCoverageView view, String key) {
		return view.categories().stream().filter(category -> category.key().equals(key)).findFirst().orElseThrow();
	}

	private void insert(long article, TagSetId version, String at, List<StoredTag> tags) {
		TagJudgementId judgement = TagJudgementRows.insert(this.jdbc, article, "fake", "fake-1", "{}", 1_000,
				Instant.parse(at));
		this.results.insert(new EvaluationRecord(new ArticleId(article), version, judgement, "straight", 0.7,
				Map.of("straight", 0.7, "opinion", 0.3), 3.0, Map.of(3, 1.0), tags, Instant.parse(at)));
	}

	private static StoredTag topic(String key, double probability) {
		return new StoredTag(MultiTagKind.TOPIC, key, probability);
	}

}
