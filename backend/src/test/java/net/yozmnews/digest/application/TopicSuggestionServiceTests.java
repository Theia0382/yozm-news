package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.TopicSuggestionList;
import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.feedback.EventFeedback;
import net.yozmnews.platform.UserId;
import net.yozmnews.reader.InterestRows;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.DigestInterestsDirectory;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.TopicSubscriptionHistory;
import net.yozmnews.user.TopicSuggestionDeclineList;
import net.yozmnews.user.TopicSuggestionDeclines;
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
 * 구독 제안 — 제안 조회({@code TopicSuggestions})와 {@code user} 가 선언한 {@code TopicInterestNow} 의 구현. 실제 PostgreSQL 위에
 * 다이제스트 인용 · 좋아요 이력 · 주제 구독 · 거절 행을 넣고, 지금을 기준 시각으로 둔 관심 x 를 손으로 계산한 리터럴로 단언한다.
 * <p>
 * 다이제스트를 끝낸 시각 {@link #COMPLETED} 에 노출 둘 · 좋아요 둘이 있다. 노출 · 반응의 무게 w 가 같아 x = 2w ÷ (2w + 3) 이다 — 정확히 30일
 * (반감기) 뒤면 w = 0.5 라 0.25, 1일 뒤면 약 0.3945(기준 0.35 이상), 10일 뒤면 약 0.3460(기준 미만).
 * <p>
 * 지키는 규칙: digest-R23 · digest-R25 · digest-R41 ~ digest-R43. 기준 시각과 창 끝은 모두 지금(고정 시계)이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class TopicSuggestionServiceTests {

	private static final Instant COMPLETED = Instant.parse("2026-10-19T03:00:00Z");

	private static final Instant NOW = COMPLETED.plus(Duration.ofDays(30));

	private static final Instant DAY_AFTER = COMPLETED.plus(Duration.ofDays(1));

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	UserTimezones timezones;

	@Autowired
	CurrentInterests currentInterests;

	@Autowired
	DigestInterestsDirectory digestInterests;

	@Autowired
	TopicSuggestionDeclines declines;

	@Autowired
	ExposureRepository exposures;

	@Autowired
	EventFeedback feedback;

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
		this.service = at(NOW);
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

		assertThat(withLineage(skewed, NOW).levelOf(new UserId(this.user), active(), "politics.legislature"))
			.isEmpty();
	}

	@Test
	void anUnsubscribedTopicLikedTwiceADayAgoIsSuggested() {
		likedPair("politics.legislature");

		assertThat(at(DAY_AFTER).suggestionsOf(new UserId(this.user)))
			.contains(new TopicSuggestionList(active(), List.of("politics.legislature")));
	}

	@Test
	void theSameSignalsTenDaysLaterFallBelowTheThreshold() {
		likedPair("politics.legislature");

		assertThat(at(COMPLETED.plus(Duration.ofDays(10))).suggestionsOf(new UserId(this.user)))
			.contains(new TopicSuggestionList(active(), List.of()));
	}

	@Test
	void aTopicCoveredByASubscribedCategoryIsNotSuggested() {
		likedPair("politics.legislature");
		this.jdbc.sql("""
				insert into user_tag_preferences (user_id, tag_kind, tag_key, mode, created_at)
				values (:user, 'TOPIC', 'politics', 'SUBSCRIBE', now())
				""").param("user", this.user).update();

		assertThat(at(DAY_AFTER).suggestionsOf(new UserId(this.user)))
			.contains(new TopicSuggestionList(active(), List.of()));
	}

	@Test
	void aRecentDeclineHoldsTheSuggestionBackWithoutChangingTheLevel() {
		likedPair("politics.legislature");
		OptionalDouble before = this.service.levelOf(new UserId(this.user), active(), "politics.legislature");
		decline("politics.legislature", 0.39, DAY_AFTER.minus(Duration.ofHours(1)));

		assertThat(at(DAY_AFTER).suggestionsOf(new UserId(this.user)))
			.contains(new TopicSuggestionList(active(), List.of()));
		assertThat(before).hasValue(0.25);
		assertThat(this.service.levelOf(new UserId(this.user), active(), "politics.legislature"))
			.as("거절은 관심 재료에 들지 않는다")
			.hasValue(0.25);
	}

	@Test
	void noAccountHasNoSuggestions() {
		assertThat(this.service.suggestionsOf(new UserId(Long.MAX_VALUE))).isEmpty();
	}

	@Test
	void aMismatchedMaterialVersionIsRetriedOnceAndThenRefused() {
		likedPair("politics.legislature");
		TagSetId elsewhere = new TagSetId(active().value() + 1_000);
		AtomicInteger skewedOnce = new AtomicInteger(1);

		assertThat(withLineage(skewing(skewedOnce, elsewhere), DAY_AFTER).suggestionsOf(new UserId(this.user)))
			.contains(new TopicSuggestionList(active(), List.of("politics.legislature")));
		assertThat(skewedOnce).hasValue(0);
		TopicSuggestionService never = withLineage(skewing(new AtomicInteger(Integer.MAX_VALUE), elsewhere),
				DAY_AFTER);
		assertThatIllegalStateException().isThrownBy(() -> never.suggestionsOf(new UserId(this.user)))
			.withMessageContaining("2 번 어긋났다");
	}

	/** 거절을 푼 버전이 관심 설정의 버전과 다르면 처음부터 다시 한다 — 다시 하지 않으면 거절 조회를 한 번만 부른다 */
	@Test
	void aDeclineListResolvedAgainstAnotherVersionIsRetried() {
		likedPair("politics.legislature");
		TagSetId elsewhere = new TagSetId(active().value() + 1_000);
		AtomicInteger calls = new AtomicInteger();
		TopicSuggestionDeclines skewedOnce = user -> calls.getAndIncrement() == 0
				? new TopicSuggestionDeclineList(elsewhere, List.of()) : this.declines.declinesOf(user);
		TopicSuggestionService service = new TopicSuggestionService(this.digestInterests, skewedOnce, this.tagSets,
				this.timezones, this.currentInterests, InstantSource.fixed(DAY_AFTER));

		assertThat(service.suggestionsOf(new UserId(this.user)))
			.contains(new TopicSuggestionList(active(), List.of("politics.legislature")));
		assertThat(calls).hasValue(2);
	}

	private TopicSuggestionService at(Instant now) {
		return new TopicSuggestionService(this.digestInterests, this.declines, this.tagSets, this.timezones,
				this.currentInterests, InstantSource.fixed(now));
	}

	/** 재료 쪽에만 계보 표를 넣은 조회 */
	private TopicSuggestionService withLineage(TagKeyLineage materialLineage, Instant now) {
		InterestMaterials skewedMaterials = new InterestMaterials(this.exposures, this.feedback,
				new ActiveEvaluations(this.evaluations, materialLineage), this.topicHistory,
				this.storylineSubscriptions, this.storylines, this.events);
		return new TopicSuggestionService(this.digestInterests, this.declines, this.tagSets, this.timezones,
				new CurrentInterests(skewedMaterials), InstantSource.fixed(now));
	}

	private TagKeyLineage skewing(AtomicInteger remaining, TagSetId elsewhere) {
		return from -> {
			TagKeyMapping real = this.lineage.toActive(from);
			if (remaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
				return new TagKeyMapping(real.from(), elsewhere, real.keys());
			}
			return real;
		};
	}

	/** 활성 버전의 거절 행 하나 */
	private void decline(String topic, double level, Instant at) {
		this.jdbc.sql("""
				insert into user_topic_suggestion_declines (user_id, tag_set_id, topic_key, interest_level, declined_at)
				values (:user, :tagSet, :topic, :level, :at)
				""")
			.param("user", this.user)
			.param("tagSet", active().value())
			.param("topic", topic)
			.param("level", level)
			.param("at", Timestamp.from(at))
			.update();
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
