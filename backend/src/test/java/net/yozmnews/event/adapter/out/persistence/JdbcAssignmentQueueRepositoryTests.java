package net.yozmnews.event.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.domain.assignment.PendingAssignment;
import net.yozmnews.event.domain.intake.IntakeLease;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 배정 대기 저장소 — 넣기 · 잠금 대상(판정 가능 시각 · 점유)과 순서 · 토큰 조건의 반영(지우기 · 풀기 · 재시도) · 지우기를 Flyway 가
 * 적용된 실제 PostgreSQL 에서 본다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcAssignmentQueueRepository.class })
class JdbcAssignmentQueueRepositoryTests {

	private static final Instant T = Instant.parse("2026-11-06T00:00:00Z");

	private static final UUID TOKEN = UUID.fromString("00000000-0000-4000-8000-000000000003");

	private static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-000000000004");

	@Autowired
	JdbcAssignmentQueueRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void enqueuesOnceAvailableAtOnceWithoutAttempts() {
		this.repository.enqueue(new ArticleId(1), T);
		this.repository.enqueue(new ArticleId(1), Instant.parse("2026-11-06T05:00:00Z"));

		assertThat(this.repository.lockDue(T, 10)).containsExactly(new PendingAssignment(new ArticleId(1), T, 0, T, null));
	}

	@Test
	void locksAvailableFreeAndExpiredRowsInAvailableOrderUpToTheLimit() {
		row(11, "2026-11-06T00:00:00Z", 0, null, null);
		row(12, "2026-11-05T23:00:00Z", 2, OTHER, "2026-11-06T00:00:00Z"); // 만료 경계 — 점유 가능
		row(13, "2026-11-06T00:00:01Z", 1, null, null); // 아직 판정 가능 시각 전
		row(14, "2026-11-05T22:00:00Z", 0, OTHER, "2026-11-06T00:00:01Z"); // 점유 중
		row(15, "2026-11-06T00:00:00Z", 0, null, null);

		assertThat(this.repository.lockDue(T, 2)).extracting(PendingAssignment::articleId)
			.containsExactly(new ArticleId(12), new ArticleId(11));
		assertThat(this.repository.lockDue(T, 10)).extracting(PendingAssignment::articleId)
			.containsExactly(new ArticleId(12), new ArticleId(11), new ArticleId(15));
	}

	@Test
	void savesAttemptsAvailabilityAndLeaseOfALockedRow() {
		row(21, "2026-11-06T00:00:00Z", 1, null, null);
		PendingAssignment claimed = this.repository.lockDue(T, 1).get(0).claim(TOKEN, T);

		this.repository.saveLocked(claimed);

		assertThat(this.repository.lockDue(Instant.parse("2026-11-06T00:20:00Z"), 1)).containsExactly(
				new PendingAssignment(new ArticleId(21), Instant.parse("2026-11-06T00:00:00Z"), 1, T,
						new IntakeLease(TOKEN, Instant.parse("2026-11-06T00:20:00Z"))));
	}

	@Test
	void tokenConditionedChangesNeedTheMatchingToken() {
		row(31, "2026-11-06T00:00:00Z", 0, TOKEN, "2026-11-06T00:20:00Z");
		row(32, "2026-11-06T00:00:00Z", 0, TOKEN, "2026-11-06T00:20:00Z");
		row(33, "2026-11-06T00:00:00Z", 0, TOKEN, "2026-11-06T00:20:00Z");

		assertThat(this.repository.deleteIfLeased(new ArticleId(31), OTHER)).isFalse();
		assertThat(this.repository.releaseIfLeased(new ArticleId(32), OTHER)).isFalse();
		assertThat(this.repository.retryIfLeased(new ArticleId(33), OTHER, 1, T)).isFalse();
		assertThat(this.repository.deleteIfLeased(new ArticleId(31), TOKEN)).isTrue();
		assertThat(this.repository.releaseIfLeased(new ArticleId(32), TOKEN)).isTrue();
		assertThat(this.repository.retryIfLeased(new ArticleId(33), TOKEN, 2, Instant.parse("2026-11-06T00:02:00Z")))
			.isTrue();

		assertThat(this.repository.lockDue(Instant.parse("2026-11-06T00:02:00Z"), 10)).containsExactly(
				new PendingAssignment(new ArticleId(32), T, 0, T, null),
				new PendingAssignment(new ArticleId(33), T, 2, Instant.parse("2026-11-06T00:02:00Z"), null));
	}

	@Test
	void deletesTheGivenArticlesOnly() {
		row(41, "2026-11-06T00:00:00Z", 0, null, null);
		row(42, "2026-11-06T00:00:00Z", 0, TOKEN, "2026-11-06T00:20:00Z");
		row(43, "2026-11-06T00:00:00Z", 0, null, null);

		this.repository.delete(List.of(new ArticleId(41), new ArticleId(42)));
		this.repository.delete(List.of());

		assertThat(this.jdbc.sql("select article_id from event_assignment_queue").query(Long.class).list())
			.containsExactly(43L);
	}

	private void row(long article, String availableAt, int attempts, @Nullable UUID token, @Nullable String expiresAt) {
		this.jdbc.sql("""
				insert into event_assignment_queue (article_id, enqueued_at, attempts, available_at, lease_token,
				                                    lease_expires_at)
				values (:article, :enqueued, :attempts, :available, :token, :expires)
				""")
			.param("article", article)
			.param("enqueued", T.atOffset(ZoneOffset.UTC))
			.param("attempts", attempts)
			.param("available", Instant.parse(availableAt).atOffset(ZoneOffset.UTC))
			.param("token", token, Types.OTHER)
			.param("expires", (expiresAt != null) ? Instant.parse(expiresAt).atOffset(ZoneOffset.UTC) : null,
					Types.TIMESTAMP_WITH_TIMEZONE)
			.update();
	}

}
