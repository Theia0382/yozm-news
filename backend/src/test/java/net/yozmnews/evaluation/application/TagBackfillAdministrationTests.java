package net.yozmnews.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.TagBackfillRequestResult;
import net.yozmnews.evaluation.TagBackfillReviewResult;
import net.yozmnews.evaluation.TagBackfillRunView;
import net.yozmnews.evaluation.TagBackfillState;
import net.yozmnews.evaluation.TagBackfillVersionCount;
import net.yozmnews.evaluation.TagBackfillVersionView;
import net.yozmnews.evaluation.TagSetAdministration;
import net.yozmnews.evaluation.TagSetId;
import net.yozmnews.evaluation.domain.backfill.TagBackfillRepository;
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
 * 빠진 태그 채움의 확인 · 요청 — 일수의 범위, 덮는 범위와 버전별 평가 수, 버전별 채울 저장 기준, 열린 채움이 하나뿐인 것을 실제 PostgreSQL
 * 에서 본다. 시각은 서비스의 시계로 고정한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TagBackfillAdministrationTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

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

	private BackfillScenario scenario;

	private TagBackfillAdministrationService administration;

	private TagSetId first;

	private TagSetId second;

	@BeforeEach
	void twoVersionsWithEvaluationsAroundTheRange() {
		this.scenario = new BackfillScenario(this.tagSetAdministration, this.tagSets, this.thresholds, this.results,
				this.views, this.transaction, this.jdbc);
		this.scenario.reset();
		this.administration = new TagBackfillAdministrationService(this.tagSets, this.thresholds, this.results,
				this.backfills, this.transaction, InstantSource.fixed(NOW));
		this.first = this.tagSets.findByActiveTrue().orElseThrow().id();
		this.second = this.scenario.nextVersionAt(Instant.parse("2026-05-01T00:00:00Z"));
		this.scenario.changeTopicStoreAt(this.second, "0.25", Instant.parse("2026-05-02T00:00:00Z"));
		// 180일 안: 버전 1 둘 · 버전 2 하나. 범위 밖: 180일 직전 · 지금(끝 제외)
		this.scenario.insert(this.first, 1, NOW.minus(Duration.ofDays(180)), "jev", "{}", List.of());
		this.scenario.insert(this.first, 2, NOW.minus(Duration.ofHours(1)), "jev", "{}", List.of());
		this.scenario.insert(this.second, 3, NOW.minus(Duration.ofDays(2)), "jev", "{}", List.of());
		this.scenario.insert(this.first, 4, NOW.minus(Duration.ofDays(180)).minusSeconds(1), "jev", "{}", List.of());
		this.scenario.insert(this.second, 5, NOW, "jev", "{}", List.of());
	}

	@AfterEach
	void reset() {
		this.scenario.reset();
	}

	@Test
	void daysOutOfRangeDoNothing() {
		assertThat(this.administration.review(0)).isEqualTo(new TagBackfillReviewResult.DaysOutOfRange());
		assertThat(this.administration.review(181)).isEqualTo(new TagBackfillReviewResult.DaysOutOfRange());
		assertThat(this.administration.request(0)).isEqualTo(new TagBackfillRequestResult.DaysOutOfRange());
		assertThat(this.administration.request(181)).isEqualTo(new TagBackfillRequestResult.DaysOutOfRange());

		assertThat(TagSetRows.count(this.jdbc, "tag_backfills")).isZero();
	}

	@Test
	void theReviewCountsTheEvaluationsOfTheRangeByVersion() {
		assertThat(this.administration.review(180)).isEqualTo(new TagBackfillReviewResult.Ready(
				Instant.parse("2026-05-05T00:00:00Z"), NOW, 3, List.of(new TagBackfillVersionCount(this.first, 1, 2),
						new TagBackfillVersionCount(this.second, 2, 1))));
		assertThat(this.administration.review(1)).isEqualTo(new TagBackfillReviewResult.Ready(
				Instant.parse("2026-10-31T00:00:00Z"), NOW, 1, List.of(new TagBackfillVersionCount(this.first, 1, 1))));
		assertThat(TagSetRows.count(this.jdbc, "tag_backfills")).isZero();
	}

	@Test
	void aRequestRecordsTheRangeAndTheCurrentStoreThresholdOfEveryVersion() {
		TagBackfillRequestResult result = this.administration.request(180);

		assertThat(result).isInstanceOf(TagBackfillRequestResult.Requested.class);
		TagBackfillRunView run = ((TagBackfillRequestResult.Requested) result).run();
		assertThat(run).isEqualTo(new TagBackfillRunView(run.id(), 180, Instant.parse("2026-05-05T00:00:00Z"), NOW, NOW,
				TagBackfillState.WAITING, null, null, 3, 0, 0, 0, 0,
				List.of(new TagBackfillVersionView(this.first, 1, new BigDecimal("0.100"), new BigDecimal("0.100"), 2, 0,
						0, 0, 0),
						new TagBackfillVersionView(this.second, 2, new BigDecimal("0.250"), new BigDecimal("0.100"), 1, 0,
								0, 0, 0))));
		assertThat(this.jdbc.sql("select eligible_at from tag_backfills").query(OffsetDateTime.class).single()
			.toInstant()).isEqualTo(Instant.parse("2026-11-01T00:01:00Z"));
		assertThat(this.administration.runs()).containsExactly(run);
	}

	@Test
	void anOpenBackfillIsReturnedInsteadOfASecondOne() {
		TagBackfillRunView open = ((TagBackfillRequestResult.Requested) this.administration.request(1)).run();

		assertThat(this.administration.request(30)).isEqualTo(new TagBackfillRequestResult.AlreadyOpen(open));
		assertThat(TagSetRows.count(this.jdbc, "tag_backfills")).isEqualTo(1);
		assertThat(this.administration.runs()).extracting(TagBackfillRunView::days).containsExactly(1);
	}

	@Test
	void aFinishedBackfillDoesNotBlockTheNextRequest() {
		TagBackfillRunView done = ((TagBackfillRequestResult.Requested) this.administration.request(1)).run();
		this.jdbc.sql("update tag_backfills set finished_at = :at where id = :id")
			.param("at", OffsetDateTime.parse("2026-11-01T00:05:00Z"))
			.param("id", done.id())
			.update();

		assertThat(this.administration.request(30)).isInstanceOf(TagBackfillRequestResult.Requested.class);
		assertThat(this.administration.runs()).extracting(TagBackfillRunView::days, TagBackfillRunView::state)
			.containsExactly(tuple(30, TagBackfillState.WAITING),
					tuple(1, TagBackfillState.DONE));
	}

}
