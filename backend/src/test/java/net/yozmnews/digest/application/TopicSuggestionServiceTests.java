package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.OptionalDouble;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.feedback.ArticleFeedback;
import net.yozmnews.platform.UserId;
import net.yozmnews.reader.InterestRows;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.TopicSubscriptionHistory;
import net.yozmnews.user.UserTimezones;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 구독 제안의 관심 — {@code user} 가 선언한 {@code TopicInterestNow} 의 구현. 실제 PostgreSQL 위에 다이제스트 인용 · 좋아요 이력을 넣고, 지금을
 * 기준 시각으로 둔 관심 x 를 손으로 계산한 리터럴로 단언한다.
 * <p>
 * 다이제스트를 끝낸 시각 {@link #COMPLETED} 에서 정확히 30일(반감기) 뒤가 지금이다 — 노출 둘 · 좋아요 둘의 무게가 0.5 씩이라 x = (0.5 + 0.5) ÷
 * (0.5 + 0.5 + 3) = 0.25.
 * <p>
 * 지키는 규칙: digest-R23 · digest-R25. 기준 시각과 창 끝은 모두 지금(고정 시계)이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TopicSuggestionServiceTests {

	private static final Instant COMPLETED = Instant.parse("2026-10-19T03:00:00Z");

	private static final Instant NOW = COMPLETED.plus(Duration.ofDays(30));

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	UserTimezones timezones;

	@Autowired
	CurrentInterests currentInterests;

	@Autowired
	ExposureRepository exposures;

	@Autowired
	ArticleFeedback feedback;

	@Autowired
	ArticleEvaluations evaluations;

	@Autowired
	TagKeyLineage lineage;

	@Autowired
	TopicSubscriptionHistory topicHistory;

	@Autowired
	StorylineSubscriptionDirectory storylineSubscriptions;

	@Autowired
	StorylineDirectory storylines;

	@Autowired
	EventDirectory events;

	@Autowired
	JdbcClient jdbc;

	private InterestRows rows;

	private TopicSuggestionService service;

	private long user;

	@BeforeEach
	void prepare() {
		this.rows = new InterestRows(this.jdbc);
		clean();
		this.service = new TopicSuggestionService(this.tagSets, this.timezones, this.currentInterests,
				InstantSource.fixed(NOW));
		this.user = user();
	}

	@AfterEach
	void clean() {
		this.rows.clear();
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void twoLikedExposuresAHalfLifeAgoGiveAQuarter() {
		likedPair("politics.legislature");

		OptionalDouble level = this.service.levelOf(new UserId(this.user), active(), "politics.legislature");

		assertThat(level).hasValue(0.25);
	}

	@Test
	void aSubtopicWithoutSignalsIsZero() {
		likedPair("politics.legislature");

		assertThat(this.service.levelOf(new UserId(this.user), active(), "economy.markets")).hasValue(0.0);
	}

	/** 구현의 활성 버전 검사와 재료의 키 버전 검사가 모두 빈 값을 낸다 — 이 시험은 그 결과(빈 값)를 지킨다 */
	@Test
	void aVersionOtherThanTheActiveOneGivesNothing() {
		likedPair("politics.legislature");
		TagSetId other = new TagSetId(active().value() + 1_000);

		assertThat(this.service.levelOf(new UserId(this.user), other, "politics.legislature")).isEmpty();
	}

	@Test
	void noAccountGivesNothing() {
		assertThat(this.service.levelOf(new UserId(Long.MAX_VALUE), active(), "politics.legislature")).isEmpty();
	}

	@Test
	void materialsResolvedAgainstAnotherActiveVersionGiveNothing() {
		likedPair("politics.legislature");
		TagSetId elsewhere = new TagSetId(active().value() + 1_000);
		TagKeyLineage skewed = from -> {
			TagKeyMapping real = this.lineage.toActive(from);
			return new TagKeyMapping(real.from(), elsewhere, real.keys());
		};
		InterestMaterials skewedMaterials = new InterestMaterials(this.exposures, this.feedback,
				new ActiveEvaluations(this.evaluations, skewed), this.topicHistory, this.storylineSubscriptions,
				this.storylines, this.events);
		TopicSuggestionService service = new TopicSuggestionService(this.tagSets, this.timezones,
				new CurrentInterests(skewedMaterials), InstantSource.fixed(NOW));

		assertThat(service.levelOf(new UserId(this.user), active(), "politics.legislature")).isEmpty();
	}

	/** 한 다이제스트가 그 주제의 기사 둘을 인용하고 둘 다 좋아요 */
	private void likedPair(String topic) {
		long first = this.rows.article("첫 기사", topic);
		long second = this.rows.article("둘째 기사", topic);
		this.rows.cite(this.user, COMPLETED, first, second);
		this.rows.react(this.user, first, "LIKE", COMPLETED.plus(Duration.ofHours(1)));
		this.rows.react(this.user, second, "LIKE", COMPLETED.plus(Duration.ofHours(1)));
	}

	private TagSetId active() {
		return this.tagSets.activeTagSet().id();
	}

	/** 설정 행(서울)이 있는 사용자 */
	private long user() {
		long id = this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values ('suggestion@example.com', '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").query(Long.class).single();
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, interest_text, weekly_report,
				    weekly_report_day, monthly_report, candidate_volume, big_slots, summary_length, reasoning_effort,
				    updated_at, version)
				values (:user, :tagSet, 'Asia/Seoul', '07:00', '', true, 1, true, 'NORMAL', 3, 'NORMAL', 'MEDIUM', now(), 0)
				""").param("user", id).param("tagSet", active().value()).update();
		return id;
	}

}
