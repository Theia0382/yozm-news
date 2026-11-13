package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.storyline.domain.queue.LinkingLease;
import net.yozmnews.storyline.domain.relation.PendingStoryline;
import net.yozmnews.storyline.domain.relation.Relation;
import net.yozmnews.storyline.domain.relation.RelationCriteria;
import net.yozmnews.storyline.domain.relation.RelationKind;
import net.yozmnews.storyline.domain.relation.RelationMove;
import net.yozmnews.storyline.domain.relation.RelationOutcome;
import net.yozmnews.storyline.domain.relation.StoredRelation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 관계 · 물은 기록 · 관계 기준 이력 · 관계 대기 — 제약(자기 자신 · 쌍마다 하나 · 포함의 상위 하나), 맺기와 지우기, 합쳐진 스토리라인의
 * 관계 거르기, 옮기기, 스토리라인을 지울 때의 연쇄, 대기의 점유를 실제 PostgreSQL 에서 본다. 테스트는 테스트 트랜잭션 안에서 돈다.
 * <p>
 * 지키는 규칙: storyline-R25 · storyline-R26 · storyline-R29 · storyline-R30 · storyline-R31 · storyline-R32
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcRelationRepository.class, JdbcRelationQueueRepository.class })
class JdbcRelationRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	private static final UUID TOKEN = UUID.fromString("00000000-0000-0000-0000-00000000000c");

	private static final RelationCriteria CRITERIA = new RelationCriteria(0.60, 0.20, 3, 0.88, Duration.ofDays(14));

	@Autowired
	JdbcRelationRepository relations;

	@Autowired
	JdbcRelationQueueRepository queue;

	@Autowired
	JdbcClient jdbc;

	private long criteriaId;

	private long a;

	private long b;

	private long c;

	@BeforeEach
	void storylines() {
		this.criteriaId = this.relations.registerCriteria(CRITERIA, NOW);
		this.a = storyline();
		this.b = storyline();
		this.c = storyline();
	}

	@Test
	void linkingAPairReplacesItsRelation() {
		this.relations.link(new Relation(this.a, this.b, RelationKind.CONTAINS), 0.7, 41, this.criteriaId, NOW);
		this.relations.link(new Relation(this.b, this.a, RelationKind.FOLLOWS), 0.8, 42, this.criteriaId,
				NOW.plusSeconds(60));

		assertThat(mine(this.relations.all())).containsExactly(new Relation(this.b, this.a, RelationKind.FOLLOWS));
		assertThat(this.relations.of(this.a)).containsExactly(
				new StoredRelation(new Relation(this.b, this.a, RelationKind.FOLLOWS), NOW.plusSeconds(60)));
		Map<String, Object> row = this.jdbc
			.sql("select probability, judgement_id, criteria_id from storyline_relations where target_id = :a")
			.param("a", this.a)
			.query()
			.singleRow();
		assertThat(row).containsEntry("probability", 0.8)
			.containsEntry("judgement_id", 42L)
			.containsEntry("criteria_id", this.criteriaId);
	}

	@Test
	void unlinkingIgnoresTheDirectionAndAMissingRelation() {
		this.relations.link(new Relation(this.a, this.b, RelationKind.CONTAINS), 0.7, 41, this.criteriaId, NOW);

		this.relations.unlink(this.b, this.a);
		this.relations.unlink(this.b, this.c);

		assertThat(mine(this.relations.all())).isEmpty();
	}

	@Test
	void theTableRefusesASelfRelation() {
		assertThatThrownBy(() -> insert("CONTAINS", this.a, this.a)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("storyline_relations_not_self");
	}

	@Test
	void theTableRefusesASecondRelationOfAPairWhateverItsKindAndDirection() {
		insert("CONTAINS", this.a, this.b);
		assertThatThrownBy(() -> insert("FOLLOWS", this.b, this.a)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("storyline_relations_pair_idx");
	}

	@Test
	void theTableRefusesASecondParentButTakesSeveralPredecessors() {
		long d = storyline();
		insert("CONTAINS", this.a, this.c);
		// 이어짐은 앞이 여럿이어도 된다
		insert("FOLLOWS", this.a, d);
		insert("FOLLOWS", this.b, d);
		assertThatThrownBy(() -> insert("CONTAINS", this.b, this.c)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("storyline_relations_parent_idx");
	}

	@Test
	void readsOnlyRelationsBetweenStorylinesThatAreNotMerged() {
		this.relations.link(new Relation(this.a, this.b, RelationKind.CONTAINS), 0.7, 41, this.criteriaId, NOW);
		this.relations.link(new Relation(this.b, this.c, RelationKind.FOLLOWS), 0.7, 42, this.criteriaId, NOW);
		this.jdbc.sql("update storylines set merged_into = :a, merged_at = now() where id = :c")
			.param("a", this.a)
			.param("c", this.c)
			.update();

		assertThat(mine(this.relations.all())).containsExactly(new Relation(this.a, this.b, RelationKind.CONTAINS));
	}

	@Test
	void movesARelationKeepingItsJudgementAndRemovesTheOthers() {
		Relation moving = new Relation(this.b, this.c, RelationKind.CONTAINS);
		Relation dropped = new Relation(this.a, this.b, RelationKind.FOLLOWS);
		this.relations.link(moving, 0.7, 41, this.criteriaId, NOW);
		this.relations.link(dropped, 0.9, 42, this.criteriaId, NOW);

		this.relations.applyMove(new RelationMove.Plan(List.of(dropped),
				List.of(new RelationMove.Moved(moving, new Relation(this.a, this.c, RelationKind.CONTAINS)))),
				NOW.plusSeconds(300));

		assertThat(this.relations.of(this.a)).containsExactly(
				new StoredRelation(new Relation(this.a, this.c, RelationKind.CONTAINS), NOW.plusSeconds(300)));
		assertThat(this.relations.of(this.b)).isEmpty();
		assertThat(this.jdbc.sql("select judgement_id from storyline_relations where source_id = :a")
			.param("a", this.a)
			.query(Long.class)
			.single()).isEqualTo(41L);
	}

	@Test
	void keepsOneAskedRowPerPairAndForgetsThoseOfAStoryline() {
		this.relations.recordAsked(this.b, this.a, 3, 41L, this.criteriaId, RelationOutcome.LOW_PROBABILITY, NOW);
		this.relations.recordAsked(this.a, this.b, 6, null, this.criteriaId, RelationOutcome.NOTHING_TO_ASK,
				NOW.plusSeconds(60));
		this.relations.recordAsked(this.a, this.c, 4, 43L, this.criteriaId, RelationOutcome.LINKED, NOW);

		assertThat(this.relations.askedAtSmallerEvents(this.a)).isEqualTo(Map.of(this.b, 6, this.c, 4));
		assertThat(this.relations.askedAtSmallerEvents(this.b)).isEqualTo(Map.of(this.a, 6));
		Map<String, Object> row = this.jdbc.sql("""
				select outcome, judgement_id, asked_at from storyline_relation_asked where low_id = :a and high_id = :b
				""").param("a", this.a).param("b", this.b).query().singleRow();
		assertThat(row).containsEntry("outcome", "NOTHING_TO_ASK").containsEntry("judgement_id", null);

		this.relations.forgetAsked(this.b);

		assertThat(this.relations.askedAtSmallerEvents(this.a)).isEqualTo(Map.of(this.c, 4));
	}

	@Test
	void registersTheSameCriteriaOnce() {
		assertThat(this.relations.registerCriteria(CRITERIA, NOW.plusSeconds(60))).isEqualTo(this.criteriaId);
		assertThat(this.relations.registerCriteria(new RelationCriteria(0.61, 0.20, 3, 0.88, Duration.ofDays(14)), NOW))
			.isNotEqualTo(this.criteriaId);
	}

	@Test
	void deletingAStorylineTakesItsRelationsAskedRowsAndQueueRow() {
		this.relations.link(new Relation(this.a, this.b, RelationKind.CONTAINS), 0.7, 41, this.criteriaId, NOW);
		this.relations.link(new Relation(this.c, this.b, RelationKind.FOLLOWS), 0.7, 42, this.criteriaId, NOW);
		this.relations.recordAsked(this.a, this.b, 3, 41L, this.criteriaId, RelationOutcome.LINKED, NOW);
		this.queue.enqueue(this.b, NOW);
		this.queue.enqueue(this.a, NOW);

		this.jdbc.sql("delete from storylines where id = :b").param("b", this.b).update();

		assertThat(mine(this.relations.all())).isEmpty();
		assertThat(this.relations.askedAtSmallerEvents(this.a)).isEmpty();
		assertThat(queued()).containsExactly(this.a);
	}

	@Test
	void theQueueCountsNotificationsAndLocksInStorylineOrder() {
		this.queue.enqueue(this.c, NOW);
		this.queue.enqueue(this.a, NOW);
		this.queue.enqueue(this.c, NOW.plusSeconds(60));

		List<PendingStoryline> due = this.queue.lockDue(NOW, 100).stream().filter(this::mine).toList();

		assertThat(due).containsExactly(new PendingStoryline(this.a, 0, 0, null),
				new PendingStoryline(this.c, 1, 0, null));
		assertThat(this.queue.lockDue(NOW, 100).stream().filter(this::mine).limit(1).toList()).hasSize(1);
	}

	@Test
	void aLeaseHidesTheRowUntilItExpiresAndGuardsTheWrites() {
		this.queue.enqueue(this.a, NOW);
		PendingStoryline claimed = new PendingStoryline(this.a, 0, 0, null).claim(TOKEN, NOW);
		this.queue.saveLocked(claimed);

		assertThat(this.queue.lockDue(NOW.plusSeconds(1199), 100).stream().filter(this::mine).toList()).isEmpty();
		// 만료 시각 포함
		assertThat(this.queue.lockDue(NOW.plusSeconds(1200), 100).stream().filter(this::mine).toList())
			.containsExactly(new PendingStoryline(this.a, 0, 0, new LinkingLease(TOKEN, NOW.plusSeconds(1200))));

		UUID other = UUID.fromString("00000000-0000-0000-0000-00000000000d");
		assertThat(this.queue.releaseIfLeased(this.a, other)).isFalse();
		assertThat(this.queue.recordFailureIfLeased(this.a, other)).isEmpty();
		// 처리 중에 다시 들어왔다 — 다시 알림 수가 달라 지우지 않는다
		this.queue.enqueue(this.a, NOW.plusSeconds(10));
		assertThat(this.queue.deleteIfUnchanged(this.a, TOKEN, 0)).isFalse();
		assertThat(this.queue.recordFailureIfLeased(this.a, TOKEN)).hasValue(1);
		assertThat(this.queue.lockDue(NOW, 100).stream().filter(this::mine).toList())
			.containsExactly(new PendingStoryline(this.a, 1, 1, null));

		this.queue.saveLocked(new PendingStoryline(this.a, 1, 1, null).claim(TOKEN, NOW));
		assertThat(this.queue.releaseIfLeased(this.a, TOKEN)).isTrue();
		this.queue.saveLocked(new PendingStoryline(this.a, 1, 1, null).claim(TOKEN, NOW));
		assertThat(this.queue.deleteIfUnchanged(this.a, TOKEN, 1)).isTrue();
		assertThat(queued()).isEmpty();

		this.queue.enqueue(this.b, NOW);
		this.queue.delete(this.b);
		assertThat(queued()).isEmpty();
	}

	/** 이 시험의 스토리라인 사이 관계만 — 다른 시험이 남긴 행을 거른다 */
	private List<Relation> mine(List<Relation> relations) {
		List<Long> ids = List.of(this.a, this.b, this.c);
		return relations.stream()
			.filter(relation -> ids.contains(relation.sourceId()) && ids.contains(relation.targetId()))
			.toList();
	}

	private boolean mine(PendingStoryline row) {
		return List.of(this.a, this.b, this.c).contains(row.storylineId());
	}

	private List<Long> queued() {
		return this.jdbc.sql("""
				select storyline_id from storyline_relation_queue where storyline_id in (:a, :b, :c) order by storyline_id
				""").param("a", this.a).param("b", this.b).param("c", this.c).query(Long.class).list();
	}

	private void insert(String kind, long source, long target) {
		this.jdbc.sql("""
				insert into storyline_relations (kind, source_id, target_id, probability, judgement_id, criteria_id,
				    linked_at)
				values (:kind, :source, :target, 0.7, 1, :criteria, now())
				""")
			.param("kind", kind)
			.param("source", source)
			.param("target", target)
			.param("criteria", this.criteriaId)
			.update();
	}

	private long storyline() {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				    representative_event_id, created_at, updated_at)
				values ('relation-m', cast('[1,0]' as halfvec), 1, :at, :at, 1, :at, :at)
				returning id
				""").param("at", NOW.atOffset(ZoneOffset.UTC)).query(Long.class).single();
	}

}
