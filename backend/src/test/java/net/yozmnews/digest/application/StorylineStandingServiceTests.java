package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.feedback.EventFeedback;
import net.yozmnews.platform.UserId;
import net.yozmnews.reader.InterestRows;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.ArticleEvaluations;
import net.yozmnews.tag.TagKeyLineage;
import net.yozmnews.tag.TagKeyMapping;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetId;
import net.yozmnews.user.StorylineStanding;
import net.yozmnews.user.StorylineSubscriptionDirectory;
import net.yozmnews.user.TopicSubscriptionHistory;
import net.yozmnews.user.UserStorylineDirectory;
import net.yozmnews.user.UserStorylineFixtures;
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
 * 스토리라인 판정 — {@code user} 가 선언한 {@code StorylineStandingNow} 의 구현. 실제 PostgreSQL 위에 다이제스트 노출 · 좋아요 / 싫어요 이력 ·
 * 흐름 추적 행을 넣고, 지금을 기준 시각으로 둔 관심 스토리라인 · 남길 스토리라인의 꼭대기를 손으로 계산한 집합으로 단언한다.
 * <p>
 * 다이제스트를 끝낸 시각 {@link #COMPLETED} 의 하루 뒤가 지금이라 노출 · 반응 · 추적의 무게 w 는 0.5 ^ (1 ÷ 30) ≈ 0.977 이다. 스토리라인 관심
 * x = F ÷ (E + 3) + S ÷ 3(추적 신호는 노출 분모 밖의 항, γ 1/3) —
 * <ul>
 * <li>좋아요 둘: 2w ÷ (2w + 3) ≈ 0.394 — 만들 기준 0.35 이상</li>
 * <li>좋아요 하나: w ÷ (w + 3) ≈ 0.246 — 남길 기준 0.2 이상 · 만들 기준 미만</li>
 * <li>추적 + 싫어요 넷: −4w ÷ (4w + 3) + w ÷ 3 ≈ −0.240 — 거름 기준 −0.2 이하(싫어요 셋이면 ≈ −0.169 라 거르지 않는다)</li>
 * <li>추적만: w ÷ 3 ≈ 0.326 — 남길 기준 이상 · 만들 기준 미만</li>
 * <li>추적 + 싫어요 하나: −w ÷ (w + 3) + w ÷ 3 ≈ 0.080 — 거름 기준 초과 · 남길 기준 미만</li>
 * </ul>
 * <p>
 * 지키는 규칙: digest-R49 · digest-R51
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylineStandingServiceTests {

	private static final Instant COMPLETED = Instant.parse("2026-10-19T03:00:00Z");

	private static final Instant NOW = COMPLETED.plus(Duration.ofDays(1));

	private static final Instant REACTED = COMPLETED.plus(Duration.ofHours(1));

	@Autowired
	TagSetDirectory tagSets;

	@Autowired
	UserTimezones timezones;

	@Autowired
	CurrentInterests currentInterests;

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
	UserStorylineDirectory userStorylines;

	@Autowired
	StorylineDirectory storylines;

	@Autowired
	EventDirectory events;

	@Autowired
	JdbcClient jdbc;

	private InterestRows rows;

	private UserId user;

	@BeforeEach
	void prepare() {
		this.rows = new InterestRows(this.jdbc);
		clean();
		this.user = user();
	}

	@AfterEach
	void clean() {
		this.rows.clear();
		UserStorylineFixtures.clear(this.jdbc);
	}

	@Test
	void aStorylineLikedTwiceIsFollowedAndKept() {
		long storyline = reactedStoryline("LIKE", "LIKE");

		assertThat(at(NOW).standingOf(this.user))
			.contains(new StorylineStanding(Set.of(new StorylineId(storyline)), Set.of(new StorylineId(storyline))));
	}

	@Test
	void aStorylineLikedOnceIsKeptButNotFollowed() {
		long storyline = reactedStoryline("LIKE");

		assertThat(at(NOW).standingOf(this.user))
			.contains(new StorylineStanding(Set.of(), Set.of(new StorylineId(storyline))));
	}

	@Test
	void aTrackedStorylineIsFollowedBelowTheFollowThreshold() {
		long storyline = UserStorylineFixtures.storyline(this.jdbc, COMPLETED);
		UserStorylineFixtures.tracked(this.jdbc, this.user, storyline, COMPLETED);

		assertThat(at(NOW).standingOf(this.user))
			.contains(new StorylineStanding(Set.of(new StorylineId(storyline)), Set.of(new StorylineId(storyline))));
	}

	@Test
	void aTrackedStorylineDislikedIntoExclusionIsNeitherFollowedNorKept() {
		// 추적 + 노출 넷 모두 싫어요 → x = −4w ÷ (4w + 3) + w ÷ 3 ≈ −0.240 ≤ −0.2 — 추적 신호는 노출로 묽어지지 않아 셋으로는
		// −3w ÷ (3w + 3) + w ÷ 3 ≈ −0.169 라 걸러지지 않는다
		long storyline = reactedStoryline("DISLIKE", "DISLIKE", "DISLIKE", "DISLIKE");
		UserStorylineFixtures.tracked(this.jdbc, this.user, storyline, COMPLETED);

		assertThat(at(NOW).standingOf(this.user)).contains(new StorylineStanding(Set.of(), Set.of()));
	}

	@Test
	void aTrackedStorylineDislikedOnceIsFollowedButNotKept() {
		long storyline = reactedStoryline("DISLIKE");
		UserStorylineFixtures.tracked(this.jdbc, this.user, storyline, COMPLETED);

		assertThat(at(NOW).standingOf(this.user))
			.contains(new StorylineStanding(Set.of(new StorylineId(storyline)), Set.of()));
	}

	@Test
	void storylinesAreJudgedSeparately() {
		long likedTwice = reactedStoryline("LIKE", "LIKE");
		long likedOnce = reactedStoryline("LIKE");
		long excluded = reactedStoryline("DISLIKE", "DISLIKE", "DISLIKE", "DISLIKE");
		UserStorylineFixtures.tracked(this.jdbc, this.user, excluded, COMPLETED);
		long trackedOnly = UserStorylineFixtures.storyline(this.jdbc, COMPLETED);
		UserStorylineFixtures.tracked(this.jdbc, this.user, trackedOnly, COMPLETED);

		assertThat(at(NOW).standingOf(this.user)).contains(new StorylineStanding(
				Set.of(new StorylineId(likedTwice), new StorylineId(trackedOnly)),
				Set.of(new StorylineId(likedTwice), new StorylineId(likedOnce), new StorylineId(trackedOnly))));
	}

	@Test
	void aUserWithoutSignalsHasNoStorylines() {
		assertThat(at(NOW).standingOf(this.user)).contains(new StorylineStanding(Set.of(), Set.of()));
	}

	@Test
	void noAccountGivesNothing() {
		assertThat(at(NOW).standingOf(new UserId(Long.MAX_VALUE))).isEmpty();
	}

	@Test
	void materialsSkewedOnceAreReadAgain() {
		long storyline = reactedStoryline("LIKE", "LIKE");
		TagSetId elsewhere = new TagSetId(active().value() + 1_000);

		assertThat(withLineage(skewing(new AtomicInteger(1), elsewhere)).standingOf(this.user))
			.contains(new StorylineStanding(Set.of(new StorylineId(storyline)), Set.of(new StorylineId(storyline))));
	}

	@Test
	void materialsSkewedEveryTimeFail() {
		reactedStoryline("LIKE", "LIKE");
		TagSetId elsewhere = new TagSetId(active().value() + 1_000);

		assertThatIllegalStateException()
			.isThrownBy(() -> withLineage(skewing(new AtomicInteger(Integer.MAX_VALUE), elsewhere))
				.standingOf(this.user))
			.withMessageContaining("어긋났다");
	}

	/** 스토리라인 하나와, 반응마다 그 스토리라인에 든 사건 하나 — 한 다이제스트가 사건마다 항목 하나로 보이고 그 값으로 눌렀다 */
	private long reactedStoryline(String... reactions) {
		long storyline = UserStorylineFixtures.storyline(this.jdbc, COMPLETED);
		long[] articles = new long[reactions.length];
		for (int i = 0; i < reactions.length; i++) {
			articles[i] = this.rows.article("기사 " + storyline + "-" + i, "politics.legislature");
			this.jdbc.sql("""
					insert into storyline_members (storyline_id, event_id, linked_by, linked_at)
					values (:storyline, (select event_id from event_articles where article_id = :article), 'MERGE', now())
					""").param("storyline", storyline).param("article", articles[i]).update();
		}
		this.rows.cite(this.user.value(), COMPLETED, articles);
		for (int i = 0; i < reactions.length; i++) {
			this.rows.react(this.user.value(), articles[i], reactions[i], REACTED);
		}
		return storyline;
	}

	private StorylineStandingService at(Instant now) {
		return new StorylineStandingService(this.tagSets, this.timezones, this.currentInterests,
				InstantSource.fixed(now));
	}

	/** 재료 쪽에만 계보 표를 넣은 판정 */
	private StorylineStandingService withLineage(TagKeyLineage materialLineage) {
		InterestMaterials skewedMaterials = new InterestMaterials(this.exposures, this.feedback,
				new ActiveEvaluations(this.evaluations, materialLineage), this.topicHistory,
				this.storylineSubscriptions, this.storylines, this.events, this.userStorylines);
		return new StorylineStandingService(this.tagSets, this.timezones, new CurrentInterests(skewedMaterials),
				InstantSource.fixed(NOW));
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

	private TagSetId active() {
		return this.tagSets.activeTagSet().id();
	}

	/** 설정 행(서울)이 있는 사용자 */
	private UserId user() {
		UserId id = UserStorylineFixtures.user(this.jdbc, "standing@example.com");
		this.jdbc.sql("""
				insert into user_settings (user_id, tag_set_id, timezone, digest_time, weekly_report,
				    weekly_report_day, monthly_report, candidate_volume, big_slots, summary_length, reasoning_effort,
				    updated_at, version)
				values (:user, :tagSet, 'Asia/Seoul', '07:00', true, 1, true, 'NORMAL', 3, 'NORMAL', 'MEDIUM', now(), 0)
				""").param("user", id.value()).param("tagSet", active().value()).update();
		return id;
	}

}
