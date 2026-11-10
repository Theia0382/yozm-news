package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.yozmnews.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * V24 의 제약 — 점유 둘은 함께, 옮긴 소속(MERGE)은 점수 · 기준이 없고 판정한 소속은 넷 모두 있다, 기준 범위, 스토리라인의 시각 순서.
 * <p>
 * 지키는 규칙: storyline-R3 · storyline-R14
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class StorylineMigrationTests {

	@Autowired
	JdbcClient jdbc;

	@Test
	void aLeaseIsTokenAndExpiryTogether() {
		assertThatThrownBy(() -> this.jdbc.sql("""
				insert into storyline_linking_queue (event_id, enqueued_at, lease_token)
				values (9901, now(), '00000000-0000-0000-0000-000000000001')
				""").update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aMovedMembershipHasNoScore() {
		long storyline = storyline();

		// 한 시험에 위반 하나 — 실패한 문장 뒤에는 시험 트랜잭션이 더 쓸 수 없다
		assertThatThrownBy(() -> this.jdbc.sql("""
				insert into storyline_members (storyline_id, event_id, linked_by, score, linked_at)
				values (:s, 9902, 'MERGE', 0.5, now())
				""").param("s", storyline).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aJudgedMembershipHasItsScoreAndCriteria() {
		long storyline = storyline();

		assertThatThrownBy(() -> this.jdbc.sql("""
				insert into storyline_members (storyline_id, event_id, linked_by, linked_at)
				values (:s, 9903, 'RULE', now())
				""").param("s", storyline).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theThresholdIsAboveZeroUpToOne() {
		assertThatThrownBy(() -> this.jdbc.sql("""
				insert into storyline_linking_criteria (window_seconds, threshold, created_at) values (60, 1.5, now())
				""").update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theWindowIsPositive() {
		assertThatThrownBy(() -> this.jdbc.sql("""
				insert into storyline_linking_criteria (window_seconds, threshold, created_at) values (0, 0.5, now())
				""").update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aStorylineRangeIsInOrder() {
		assertThatThrownBy(() -> this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('m', cast('[1,0]' as halfvec), 1, '2026-11-02T00:00:00Z', '2026-11-01T00:00:00Z', 1, now(), now())
				""").update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	private long storyline() {
		return this.jdbc.sql("""
				insert into storylines (model_version, centroid, article_count, first_article_at, last_article_at,
				                        representative_event_id, created_at, updated_at)
				values ('m', cast('[1,0]' as halfvec), 1, now(), now(), 1, now(), now()) returning id
				""").query(Long.class).single();
	}

}
