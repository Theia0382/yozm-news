package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRows;
import net.yozmnews.digest.InterestHistorySpan;
import net.yozmnews.digest.InterestPeriodView;
import net.yozmnews.digest.InterestPoint;
import net.yozmnews.digest.InterestTimeline;
import net.yozmnews.digest.TopicSubscriptionStep;
import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.event.EventArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.feedback.ArticleFeedback;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagKind;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.TopicSubscriptionAction;
import net.yozmnews.user.TopicSubscriptionHistory;
import net.yozmnews.user.UserTimezones;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 관심사 히스토리 조회 — 사용자 시간대의 주 경계, 기간 끝 뒤 신호가 지난 기간에 들지 않음, 기간마다 붙는 주제 구독 변경, 리포트 꼴의 기준 시각 조회,
 * 계정 없음, 평가 키가 계보로 푼 활성 키로 보임, 재료의 키 버전 어긋남. 실제 PostgreSQL 위에서 다이제스트 인용 · 피드백 이력 · 선호 이력 행을 직접 넣는다.
 * <p>
 * 지금은 2026-11-18(수) 12:00 서울이다. 관심 x 의 값은 산식 · 창의 단위 시험(InterestFormulaTests · InterestWindowTests ·
 * InterestTimelinesTests)이 고정하고, 여기서는 어느 기간에 값 · 변화 · 구독 변경이 붙는지를 본다. 기준 시각 조회만 실제 저장 행을 지난 관심 x 를
 * 손으로 계산한 리터럴로 단언한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class InterestHistoryServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

	private static final Offset<Double> EXACT = within(1e-12);

	@Autowired
	UserTimezones timezones;

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	InterestMaterials materials;

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

	private InterestHistoryService service;

	private long user;

	@BeforeEach
	void prepare() {
		clean();
		this.service = new InterestHistoryService(this.timezones, this.tagSets, this.materials,
				InstantSource.fixed(NOW));
		this.user = user();
	}

	@AfterEach
	void clean() {
		this.jdbc.sql("delete from digests").update();
		this.jdbc.sql("delete from feedback_events").update();
		this.jdbc.sql("delete from article_tags").update();
		this.jdbc.sql("delete from article_evaluations").update();
		EventArticles.clear(this.jdbc);
		this.jdbc.sql("delete from users").update();
	}

	@Test
	void thirteenWeeksInTheUsersZoneEndWithTheWeekInProgress() {
		InterestTimeline timeline = recent(InterestHistorySpan.WEEKS_13);

		assertThat(timeline.zone()).isEqualTo(ZoneId.of("Asia/Seoul"));
		assertThat(timeline.span()).isEqualTo(InterestHistorySpan.WEEKS_13);
		assertThat(timeline.tagSet()).isEqualTo(this.tagSets.activeTagSet().id());
		assertThat(timeline.periods()).hasSize(13);
		assertThat(timeline.periods().get(0).firstDay()).isEqualTo(LocalDate.of(2026, 8, 24));
		InterestPeriodView last = timeline.periods().get(12);
		assertThat(last.firstDay()).isEqualTo(LocalDate.of(2026, 11, 16));
		assertThat(last.inProgress()).isTrue();
		assertThat(last.point().reference()).isEqualTo(NOW);
		assertThat(timeline.periods().subList(0, 12)).noneMatch(InterestPeriodView::inProgress);
		assertThat(timeline.periods()).allSatisfy(period -> assertThat(period.point().levels()).isEmpty());
		assertThat(timeline.prominent()).isEmpty();
	}

	@Test
	void signalsAfterAPeriodEndDoNotReachThatPeriod() {
		// 11-10(화) 서울 09:00 에 끝낸 다이제스트가 입법 기사 둘을 보였고 바로 좋아요 — 11-09 주 안
		long digest = DigestRows.digest(this.jdbc, this.user, "2026-11-10", "2026-11-10T00:00:00Z");
		for (int i = 0; i < 2; i++) {
			long article = article("의회 " + i, "politics.legislature");
			DigestRows.cite(this.jdbc, digest, article);
			react(article, "LIKE", "2026-11-10T01:00:00Z");
		}

		InterestTimeline timeline = recent(InterestHistorySpan.WEEKS_13);

		InterestPeriodView weekBefore = period(timeline, LocalDate.of(2026, 11, 2));
		InterestPeriodView week = period(timeline, LocalDate.of(2026, 11, 9));
		InterestPeriodView current = period(timeline, LocalDate.of(2026, 11, 16));
		assertThat(weekBefore.point().levels()).as("기간 끝 뒤 노출 · 좋아요는 들지 않는다").isEmpty();
		assertThat(week.point().levels()).containsOnlyKeys("politics.legislature");
		assertThat(week.point().entered()).containsExactly("politics.legislature");
		assertThat(current.point().levels()).containsOnlyKeys("politics.legislature");
		assertThat(current.point().entered()).isEmpty();
		assertThat(timeline.prominent()).containsExactly("politics.legislature");
	}

	@Test
	void subscriptionChangesByTheUserAttachToThePeriodTheyHappenedIn() {
		TagSetId active = this.tagSets.activeTagSet().id();
		topicEvent("USER", active, "economy.markets", "ADD", "2026-11-11T05:00:00Z");
		topicEvent("ONBOARDING", active, "politics", "ADD", "2026-11-11T06:00:00Z");
		topicEvent("TAG_SET_CHANGE", active, "politics.legislature", "ADD", "2026-11-12T06:00:00Z");
		topicEvent("USER", active, "economy.markets", "REMOVE", "2026-11-17T05:00:00Z");

		InterestTimeline timeline = recent(InterestHistorySpan.WEEKS_13);

		assertThat(period(timeline, LocalDate.of(2026, 11, 9)).point().subscriptionChanges()).containsExactly(
				new TopicSubscriptionStep(Instant.parse("2026-11-11T05:00:00Z"), TopicSubscriptionAction.ADD,
						Set.of("economy.markets")));
		assertThat(period(timeline, LocalDate.of(2026, 11, 16)).point().subscriptionChanges()).containsExactly(
				new TopicSubscriptionStep(Instant.parse("2026-11-17T05:00:00Z"), TopicSubscriptionAction.REMOVE,
						Set.of("economy.markets")));
		assertThat(period(timeline, LocalDate.of(2026, 11, 2)).point().subscriptionChanges()).isEmpty();
	}

	@Test
	void monthsUseTheFirstOfTheMonthInTheUsersZone() {
		this.jdbc.sql("update user_settings set timezone = 'America/New_York' where user_id = :user")
			.param("user", this.user)
			.update();

		InterestTimeline timeline = recent(InterestHistorySpan.MONTHS_12);

		assertThat(timeline.zone()).isEqualTo(ZoneId.of("America/New_York"));
		assertThat(timeline.periods()).hasSize(12);
		assertThat(timeline.periods().get(0).firstDay()).isEqualTo(LocalDate.of(2025, 12, 1));
		assertThat(timeline.periods().get(11).firstDay()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(timeline.periods().get(10).point().reference())
			.as("10월 끝 = 11-01 00:00 뉴욕(EDT −4)")
			.isEqualTo(Instant.parse("2026-11-01T04:00:00Z"));
	}

	@Test
	void referencesChosenByTheCallerGiveAPointForEachAfterTheFirst() {
		long digest = DigestRows.digest(this.jdbc, this.user, "2026-11-10", "2026-11-10T00:00:00Z");
		long article = article("의회", "politics.legislature");
		DigestRows.cite(this.jdbc, digest, article);
		react(article, "LIKE", "2026-11-10T01:00:00Z");
		topicEvent("USER", this.tagSets.activeTagSet().id(), "economy.markets", "ADD", "2026-11-12T00:00:00Z");

		List<InterestPoint> points = this.service
			.at(new UserId(this.user), List.of(Instant.parse("2026-11-09T00:00:00Z"),
					Instant.parse("2026-11-16T00:00:00Z")))
			.orElseThrow();

		assertThat(points).singleElement().satisfies(point -> {
			assertThat(point.reference()).isEqualTo(Instant.parse("2026-11-16T00:00:00Z"));
			assertThat(point.levels()).containsOnlyKeys("politics.legislature", "economy.markets");
			// 입법: 6일 전 노출 · 좋아요 → w = 0.5^(6/30) → x = w ÷ (w + 3) ≈ 0.2249
			assertThat(point.levels().get("politics.legislature")).isCloseTo(0.22491646835761048, EXACT);
			// 시장: 4일 전 사용자 더함 → x = 0.5^(4/30) ÷ 3 ≈ 0.3039
			assertThat(point.levels().get("economy.markets")).isCloseTo(0.30390749618607227, EXACT);
			assertThat(point.entered()).containsExactlyInAnyOrder("politics.legislature", "economy.markets");
			assertThat(point.left()).isEmpty();
			assertThat(point.subscriptionChanges()).containsExactly(new TopicSubscriptionStep(
					Instant.parse("2026-11-12T00:00:00Z"), TopicSubscriptionAction.ADD, Set.of("economy.markets")));
		});
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.service.at(new UserId(this.user), List.of(Instant.parse("2026-11-16T00:00:00Z"))));
		assertThatIllegalArgumentException().isThrownBy(() -> this.service.at(new UserId(this.user),
				List.of(Instant.parse("2026-11-16T00:00:00Z"), Instant.parse("2026-11-09T00:00:00Z"))));
	}

	@Test
	void anEvaluationKeyAppearsAsTheActiveKeyItResolvesTo() {
		// 재료 쪽 계보 표가 politics.legislature 를 politics.executive 로 옮긴다(이름이 바뀐 버전의 표를 흉내 낸다)
		long digest = DigestRows.digest(this.jdbc, this.user, "2026-11-10", "2026-11-10T00:00:00Z");
		DigestRows.cite(this.jdbc, digest, article("의회", "politics.legislature"));
		TagKeyLineage renamed = from -> {
			TagKeyMapping real = this.lineage.toActive(from);
			Map<TagKind, Map<String, Set<String>>> keys = new HashMap<>(real.keys());
			Map<String, Set<String>> topics = new HashMap<>(keys.get(TagKind.TOPIC));
			topics.put("politics.legislature", Set.of("politics.executive"));
			keys.put(TagKind.TOPIC, topics);
			return new TagKeyMapping(real.from(), real.to(), keys);
		};

		InterestTimeline timeline = withLineage(renamed).recent(new UserId(this.user), InterestHistorySpan.WEEKS_13)
			.orElseThrow();

		assertThat(period(timeline, LocalDate.of(2026, 11, 9)).point().levels())
			.containsOnlyKeys("politics.executive");
	}

	@Test
	void noAccountHasNoHistory() {
		UserId nobody = new UserId(Long.MAX_VALUE);

		assertThat(this.service.recent(nobody, InterestHistorySpan.WEEKS_13)).isEmpty();
		assertThat(this.service.at(nobody,
				List.of(Instant.parse("2026-11-09T00:00:00Z"), Instant.parse("2026-11-16T00:00:00Z"))))
			.isEmpty();
	}

	@Test
	void aMismatchedActiveVersionOfTheMaterialsIsRetriedOnceAndThenRefused() {
		long digest = DigestRows.digest(this.jdbc, this.user, "2026-11-10", "2026-11-10T00:00:00Z");
		DigestRows.cite(this.jdbc, digest, article("의회", "politics.legislature"));
		TagSetId elsewhere = new TagSetId(this.tagSets.activeTagSet().id().value() + 1_000);
		AtomicInteger skewed = new AtomicInteger(1);

		InterestTimeline timeline = withLineage(skewing(skewed, elsewhere)).recent(new UserId(this.user),
				InterestHistorySpan.WEEKS_13).orElseThrow();
		assertThat(skewed).hasValue(0);
		assertThat(period(timeline, LocalDate.of(2026, 11, 9)).point().levels())
			.containsOnlyKeys("politics.legislature");

		InterestHistoryService never = withLineage(skewing(new AtomicInteger(Integer.MAX_VALUE), elsewhere));
		assertThatIllegalStateException()
			.isThrownBy(() -> never.recent(new UserId(this.user), InterestHistorySpan.WEEKS_13))
			.withMessageContaining("2 번 어긋났다");
	}

	private InterestTimeline recent(InterestHistorySpan span) {
		return this.service.recent(new UserId(this.user), span).orElseThrow();
	}

	private static InterestPeriodView period(InterestTimeline timeline, LocalDate firstDay) {
		return timeline.periods()
			.stream()
			.filter(period -> period.firstDay().equals(firstDay))
			.findFirst()
			.orElseThrow();
	}

	/** 재료 쪽에만 계보 표를 넣은 조회 */
	private InterestHistoryService withLineage(TagKeyLineage materialLineage) {
		InterestMaterials skewedMaterials = new InterestMaterials(this.exposures, this.feedback,
				new ActiveEvaluations(this.evaluations, materialLineage), this.topicHistory,
				this.storylineSubscriptions, this.storylines, this.events);
		return new InterestHistoryService(this.timezones, this.tagSets, skewedMaterials, InstantSource.fixed(NOW));
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

	/** 설정 행(서울)이 있는 사용자 */
	private long user() {
		long id = this.jdbc.sql("""
				insert into users (email, display_name, role, status, created_at, version)
				values ('history@example.com', '사용자', 'USER', 'ACTIVE', now(), 0) returning id
				""").query(Long.class).single();
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, interest_text, weekly_report,
				    weekly_report_day, monthly_report, candidate_volume, big_slots, summary_length, reasoning_effort,
				    updated_at, version)
				values (:user, :tagSet, 'Asia/Seoul', '07:00', '', true, 1, true, 'NORMAL', 3, 'NORMAL', 'MEDIUM', now(), 0)
				""").param("user", id).param("tagSet", this.tagSets.activeTagSet().id().value()).update();
		return id;
	}

	/** 기사 · 엔트리와 그 평가 — 주제 하나(확률 0.9), 첫 평가 11-01 */
	private long article(String title, String topic) {
		long article = EventArticles.insert(this.jdbc, title, "요약", 1, 11);
		long evaluation = this.jdbc.sql("""
				insert into article_evaluations (article_id, tag_set_id, judgement_id, format_key, format_probability,
				    format_distribution, importance, importance_distribution, evaluated_at)
				values (:article, :tagSet, :article, 'straight', 1.0, '{}', 3.0, '{}', :at)
				returning id
				""")
			.param("article", article)
			.param("tagSet", this.tagSets.activeTagSet().id().value())
			.param("at", Instant.parse("2026-11-01T00:00:00Z").atOffset(ZoneOffset.UTC))
			.query(Long.class)
			.single();
		this.jdbc.sql("""
				insert into article_tags (evaluation_id, article_id, tag_kind, tag_key, probability)
				values (:evaluation, :article, 'TOPIC', :topic, 0.9)
				""").param("evaluation", evaluation).param("article", article).param("topic", topic).update();
		return article;
	}

	private void react(long article, String value, String at) {
		this.jdbc.sql("""
				insert into feedback_events (user_id, article_id, value, digest_id, occurred_at)
				values (:user, :article, :value, 1, cast(:at as timestamptz))
				""").param("user", this.user).param("article", article).param("value", value).param("at", at).update();
	}

	private void topicEvent(String cause, TagSetId tagSet, String key, String action, String at) {
		this.jdbc.sql("""
				insert into user_tag_preference_events (user_id, cause, tag_set_id, tag_kind, tag_key, mode, action,
				    occurred_at)
				values (:user, :cause, :tagSet, 'TOPIC', :key, 'SUBSCRIBE', :action, cast(:at as timestamptz))
				""")
			.param("user", this.user)
			.param("cause", cause)
			.param("tagSet", tagSet.value())
			.param("key", key)
			.param("action", action)
			.param("at", at)
			.update();
	}

}
