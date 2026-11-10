package net.yozmnews.storyline.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.storyline.domain.queue.LinkingLease;
import net.yozmnews.storyline.domain.queue.PendingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 잇기 대기 — 넣기는 다시 알림 수를 올리고 순서 키를 바꾼다, 점유 순서(순서 키 없음이 먼저 · 시각 · 사건 ID), 점유 만료, 토큰과 다시
 * 알림 수가 모두 맞을 때만 지우기, 실패 수, 여럿 넣기의 새 행 수를 실제 PostgreSQL 에서 본다. 사건 ID 는 다른 시험과 겹치지 않게 크게 쓴다.
 * <p>
 * 지키는 규칙: storyline-R2 · storyline-R3 · storyline-R4
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcLinkingQueueRepository.class })
class JdbcLinkingQueueRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	private static final UUID TOKEN = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	@Autowired
	JdbcLinkingQueueRepository repository;

	@Autowired
	JdbcClient jdbc;

	@Test
	void enqueueAddsARowOrCountsANotificationAndTakesTheNewOrder() {
		this.repository.enqueue(9001, Instant.parse("2026-11-01T00:00:00Z"), NOW);
		assertThat(row(9001)).containsEntry("notifications", 0).containsEntry("order_at", "2026-11-01T00:00:00Z");

		this.repository.enqueue(9001, Instant.parse("2026-10-31T00:00:00Z"), NOW.plusSeconds(60));
		assertThat(row(9001)).containsEntry("notifications", 1).containsEntry("order_at", "2026-10-31T00:00:00Z");

		this.repository.enqueue(9001, null, NOW.plusSeconds(120));
		assertThat(row(9001)).containsEntry("notifications", 2).containsEntry("order_at", null);
	}

	@Test
	void lockDueOrdersByEventTimeWithNoTimeFirst() {
		this.repository.enqueue(9013, Instant.parse("2026-11-03T00:00:00Z"), NOW);
		this.repository.enqueue(9012, null, NOW);
		this.repository.enqueue(9011, Instant.parse("2026-11-01T00:00:00Z"), NOW);
		this.repository.enqueue(9014, Instant.parse("2026-11-01T00:00:00Z"), NOW);

		assertThat(this.repository.lockDue(NOW, 10).stream().map(PendingEvent::eventId).filter(id -> id > 9010).toList())
			.containsExactly(9012L, 9011L, 9014L, 9013L);
	}

	@Test
	void aLeaseHidesTheRowUntilItExpires() {
		this.repository.enqueue(9021, Instant.parse("2026-11-01T00:00:00Z"), NOW);
		PendingEvent due = only(9021);
		this.repository.saveLocked(due.claim(TOKEN, NOW));

		assertThat(this.repository.lockDue(NOW.plusSeconds(599), 100)).extracting(PendingEvent::eventId)
			.doesNotContain(9021L);
		// 만료 시각 포함
		assertThat(this.repository.lockDue(NOW.plusSeconds(600), 100)).filteredOn(row -> row.eventId() == 9021)
			.singleElement()
			.satisfies(row -> assertThat(row.lease()).isEqualTo(new LinkingLease(TOKEN, NOW.plusSeconds(600))));
	}

	@Test
	void deleteNeedsTheTokenAndTheNotificationsSeenAtClaim() {
		this.repository.enqueue(9031, Instant.parse("2026-11-01T00:00:00Z"), NOW);
		this.repository.saveLocked(only(9031).claim(TOKEN, NOW));

		assertThat(this.repository.deleteIfUnchanged(9031, UUID.randomUUID(), 0)).isFalse();
		// 처리 중에 다시 알림이 왔다 — 점유할 때의 수(0)와 달라 지우지 않는다
		this.repository.enqueue(9031, Instant.parse("2026-11-01T00:00:00Z"), NOW);
		assertThat(this.repository.deleteIfUnchanged(9031, TOKEN, 0)).isFalse();
		assertThat(row(9031)).containsEntry("notifications", 1);
		assertThat(this.repository.releaseIfLeased(9031, TOKEN)).isTrue();
		assertThat(row(9031)).containsEntry("lease_token", null);

		this.repository.saveLocked(only(9031).claim(TOKEN, NOW));
		assertThat(this.repository.deleteIfUnchanged(9031, TOKEN, 1)).isTrue();
		assertThat(this.jdbc.sql("select count(*) from storyline_linking_queue where event_id = 9031")
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void aFailureCountsUpAndReleasesTheLease() {
		this.repository.enqueue(9041, null, NOW);
		this.repository.saveLocked(only(9041).claim(TOKEN, NOW));

		assertThat(this.repository.recordFailureIfLeased(9041, UUID.randomUUID())).isEmpty();
		assertThat(this.repository.recordFailureIfLeased(9041, TOKEN)).hasValue(1);
		assertThat(row(9041)).containsEntry("failures", 1).containsEntry("lease_token", null);
		// 다시 넣어도 실패 수는 그대로다
		this.repository.enqueue(9041, null, NOW);
		assertThat(row(9041)).containsEntry("failures", 1);

		this.repository.delete(9041);
		assertThat(this.jdbc.sql("select count(*) from storyline_linking_queue where event_id = 9041")
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void enqueueAllCountsOnlyNewRows() {
		this.repository.enqueue(9052, Instant.parse("2026-11-02T00:00:00Z"), NOW);

		int inserted = this.repository.enqueueAll(Map.of(9053L, Instant.parse("2026-11-03T00:00:00Z"), 9052L,
				Instant.parse("2026-10-30T00:00:00Z"), 9051L, Instant.parse("2026-11-01T00:00:00Z")), NOW);

		assertThat(inserted).isEqualTo(2);
		assertThat(row(9052)).containsEntry("notifications", 1).containsEntry("order_at", "2026-10-30T00:00:00Z");
		assertThat(row(9051)).containsEntry("notifications", 0).containsEntry("order_at", "2026-11-01T00:00:00Z");
		assertThat(this.repository.enqueueAll(Map.of(), NOW)).isZero();
	}

	private PendingEvent only(long eventId) {
		return this.repository.lockDue(NOW, 1000)
			.stream()
			.filter(row -> row.eventId() == eventId)
			.findFirst()
			.orElseThrow();
	}

	private Map<String, Object> row(long eventId) {
		return this.jdbc.sql("""
				select notifications, failures, lease_token,
				       to_char(order_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as order_at
				from storyline_linking_queue where event_id = :event
				""").param("event", eventId).query().singleRow();
	}

}
