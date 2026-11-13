package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.evaluation.RelationJudgementId;
import net.yozmnews.event.EventId;
import net.yozmnews.storyline.LinkScore;
import net.yozmnews.storyline.LinkedBy;
import net.yozmnews.storyline.RelationOutcome;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineLinkingCriteria;
import net.yozmnews.storyline.StorylineMembership;
import net.yozmnews.storyline.StorylineRelationAsked;
import net.yozmnews.storyline.StorylineRelationCriteria;
import net.yozmnews.storyline.StorylineRelationDetail;
import net.yozmnews.storyline.StorylineRelationKind;
import net.yozmnews.storyline.StorylineRelationView;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.domain.linking.LinkingCriteria;
import net.yozmnews.storyline.domain.linking.LinkingCriteriaRepository;
import net.yozmnews.storyline.domain.linking.Score;
import net.yozmnews.storyline.domain.relation.Relation;
import net.yozmnews.storyline.domain.relation.RelationCriteria;
import net.yozmnews.storyline.domain.relation.RelationKind;
import net.yozmnews.storyline.domain.relation.RelationRepository;
import net.yozmnews.storyline.domain.storyline.LinkJudgement;
import net.yozmnews.storyline.domain.storyline.StorylineQueryRepository;
import net.yozmnews.storyline.domain.storyline.StorylineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 운영자 조회 — 스토리라인과 그 하위의 소속 근거, 관계의 확률 · 판정 원본 · 기준, 물은 기록을 실제 저장소로 읽는다. 합쳐진 ID 는 남는 쪽을
 * 따르고, 없는 ID 는 빈 목록이다.
 * <p>
 * 지키는 규칙: storyline-R34 · storyline-R30 · storyline-R33(가족) · storyline-R21(남는 쪽)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylineReviewFlowTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	private static final Instant LINKED_AT = Instant.parse("2026-11-06T03:00:00Z");

	private static final LinkingCriteria LINKING = new LinkingCriteria(Duration.ofDays(3), 0.62);

	private static final RelationCriteria RELATION = new RelationCriteria(0.60, 0.20, 3, 0.88, Duration.ofDays(14));

	private static final StorylineLinkingCriteria LINKING_VIEW = new StorylineLinkingCriteria(Duration.ofDays(3), 0.62);

	private static final StorylineRelationCriteria RELATION_VIEW = new StorylineRelationCriteria(0.60, 0.20, 3, 0.88,
			Duration.ofDays(14));

	@Autowired
	StorylineQueryRepository storylines;

	@Autowired
	StorylineRepository memberships;

	@Autowired
	RelationRepository relations;

	@Autowired
	LinkingCriteriaRepository linking;

	@Autowired
	JdbcClient jdbc;

	private StorylineReviewService review;

	private long parent;

	private long child;

	private long merged;

	private long follower;

	private long silent;

	private long e1;

	private long e2;

	private long e3;

	@BeforeEach
	void plant() {
		StorylineRows.clear(this.jdbc);
		this.review = new StorylineReviewService(this.storylines, this.memberships, this.relations, this.linking,
				new LinkingCriteria(Duration.ofDays(5), 0.7),
				new RelationCriteria(0.65, 0.25, 4, 0.9, Duration.ofDays(21)));
		this.e1 = event();
		this.e2 = event();
		this.e3 = event();
		this.parent = storyline(this.e1);
		this.child = storyline(this.e2);
		this.merged = storyline(this.e1);
		this.follower = storyline(this.e1);
		this.silent = storyline(this.e1);
		long linkingId = this.linking.register(LINKING, NOW);
		long relationId = this.relations.registerCriteria(RELATION, NOW);
		this.memberships.addMember(this.parent, this.e1,
				LinkJudgement.judged(LinkedBy.RULE, new Score(0.81, 0.77, 0.4), linkingId, LINKED_AT));
		this.memberships.addMember(this.parent, this.e3, LinkJudgement.merged(LINKED_AT.plusSeconds(60)));
		this.memberships.addMember(this.child, this.e2,
				LinkJudgement.judged(LinkedBy.PAIR, new Score(0.7, 0.66, 0.2), linkingId, LINKED_AT));
		this.memberships.addMember(this.child, this.e3,
				LinkJudgement.judged(LinkedBy.RULE, new Score(0.9, 0.88, 0.5), linkingId, LINKED_AT));
		// 포함 parent ⊃ child, 이어짐 parent → follower
		this.relations.link(new Relation(this.parent, this.child, RelationKind.CONTAINS), 0.7, 41, relationId, NOW);
		this.relations.link(new Relation(this.parent, this.follower, RelationKind.FOLLOWS), 0.66, 42, relationId, NOW);
		this.relations.recordAsked(this.follower, this.parent, 3, 42L, relationId, RelationOutcome.LINKED, NOW);
		this.relations.recordAsked(this.parent, this.silent, 4, null, relationId, RelationOutcome.NOTHING_TO_ASK,
				NOW.plusSeconds(30));
		this.jdbc.sql("update storylines set merged_into = :parent, merged_at = now() where id = :merged")
			.param("parent", this.parent)
			.param("merged", this.merged)
			.update();
	}

	@AfterEach
	void clear() {
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void membershipsCoverTheStorylineAndItsDescendantsWithTheirJudgements() {
		List<StorylineMembership> expected = List.of(
				new StorylineMembership(new StorylineId(this.parent), new EventId(this.e1), LinkedBy.RULE,
						new LinkScore(0.81, 0.77, 0.4), LINKING_VIEW, LINKED_AT),
				new StorylineMembership(new StorylineId(this.parent), new EventId(this.e3), LinkedBy.MERGE, null, null,
						LINKED_AT.plusSeconds(60)),
				new StorylineMembership(new StorylineId(this.child), new EventId(this.e2), LinkedBy.PAIR,
						new LinkScore(0.7, 0.66, 0.2), LINKING_VIEW, LINKED_AT),
				new StorylineMembership(new StorylineId(this.child), new EventId(this.e3), LinkedBy.RULE,
						new LinkScore(0.9, 0.88, 0.5), LINKING_VIEW, LINKED_AT));

		assertThat(this.review.memberships(new StorylineId(this.parent))).isEqualTo(expected);
		// 합쳐진 ID 는 남는 쪽을 따른다
		assertThat(this.review.memberships(new StorylineId(this.merged))).isEqualTo(expected);
		// 하위는 자기 소속만 — 상위의 소속은 들지 않는다
		assertThat(this.review.memberships(new StorylineId(this.child))).extracting(StorylineMembership::storyline)
			.containsOnly(new StorylineId(this.child));
	}

	@Test
	void relationsCarryTheirProbabilityJudgementAndCriteria() {
		List<StorylineRelationDetail> expected = List.of(
				new StorylineRelationDetail(new StorylineRelationView(StorylineRelationKind.CONTAINS,
						new StorylineId(this.parent), new StorylineId(this.child), NOW), 0.7, new RelationJudgementId(41),
						RELATION_VIEW),
				new StorylineRelationDetail(new StorylineRelationView(StorylineRelationKind.FOLLOWS,
						new StorylineId(this.parent), new StorylineId(this.follower), NOW), 0.66,
						new RelationJudgementId(42), RELATION_VIEW));

		assertThat(this.review.relations(new StorylineId(this.parent))).isEqualTo(expected);
		assertThat(this.review.relations(new StorylineId(this.merged))).isEqualTo(expected);
	}

	@Test
	void askedRecordsNameTheOtherStorylineAndKeepAMissingJudgementEmpty() {
		assertThat(this.review.asked(new StorylineId(this.parent))).containsExactly(
				new StorylineRelationAsked(new StorylineId(this.follower), 3, RelationOutcome.LINKED,
						new RelationJudgementId(42), RELATION_VIEW, NOW),
				new StorylineRelationAsked(new StorylineId(this.silent), 4, RelationOutcome.NOTHING_TO_ASK, null,
						RELATION_VIEW, NOW.plusSeconds(30)));
		// 쌍의 작은 쪽 · 큰 쪽 어느 편에서 읽어도 상대를 낸다
		assertThat(this.review.asked(new StorylineId(this.follower)))
			.extracting(StorylineRelationAsked::other)
			.containsExactly(new StorylineId(this.parent));
		assertThat(this.review.asked(new StorylineId(this.child))).isEmpty();
	}

	@Test
	void anUnknownStorylineHasNothing() {
		StorylineId unknown = new StorylineId(this.silent + 1000);

		assertThat(this.review.memberships(unknown)).isEmpty();
		assertThat(this.review.relations(unknown)).isEmpty();
		assertThat(this.review.asked(unknown)).isEmpty();
	}

	@Test
	void theCurrentCriteriaAreTheSettings() {
		assertThat(this.review.linkingCriteria()).isEqualTo(new StorylineLinkingCriteria(Duration.ofDays(5), 0.7));
		assertThat(this.review.relationCriteria())
			.isEqualTo(new StorylineRelationCriteria(0.65, 0.25, 4, 0.9, Duration.ofDays(21)));
	}

	private long event() {
		return StorylineRows.event(this.jdbc, "OPEN", "model-a", "[1,0]", 1, "2026-11-05T01:00:00Z",
				"2026-11-05T02:00:00Z", Map.of());
	}

	private long storyline(long representative) {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('model-a', cast('[1,0]' as halfvec), 1, :at, :at, :representative, :at, :at)
				returning id
				""")
			.param("at", Instant.parse("2026-11-05T01:00:00Z").atOffset(ZoneOffset.UTC))
			.param("representative", representative)
			.query(Long.class)
			.single();
	}

}
