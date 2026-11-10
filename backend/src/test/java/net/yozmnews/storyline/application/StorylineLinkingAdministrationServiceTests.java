package net.yozmnews.storyline.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.storyline.LinkingFillResult;
import net.yozmnews.storyline.StorylineLinkingAdministration;
import net.yozmnews.storyline.StorylineRows;
import net.yozmnews.storyline.domain.queue.LinkingQueueRepository;
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
 * 스토리라인 잇기 채우기 — 일수 범위 1 ~ 180, 마지막 기사 시각이 기간 안인 합쳐지지 않은 사건(닫힌 사건 포함)을 첫 기사 시각을 순서 키로
 * 넣고, 이미 대기에 있던 사건은 다시 알림으로 센다. 시계는 다른 시험의 사건과 겹치지 않게 먼 시각(2033년)이다.
 * <p>
 * 지키는 규칙: storyline-R2 · storyline-R15
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class StorylineLinkingAdministrationServiceTests {

	private static final Instant NOW = Instant.parse("2033-02-01T00:00:00Z");

	@Autowired
	EventDirectory directory;

	@Autowired
	LinkingQueueRepository queue;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	@AfterEach
	void reset() {
		StorylineRows.clear(this.jdbc);
	}

	@Test
	void daysOutOfRangeDoNothing() {
		assertThat(service().fill(0)).isEqualTo(new LinkingFillResult.DaysOutOfRange(180));
		assertThat(service().fill(181)).isEqualTo(new LinkingFillResult.DaysOutOfRange(180));
		assertThat(StorylineLinkingAdministration.MAX_DAYS).isEqualTo(180);
	}

	@Test
	void eventsEndingInThePeriodAreQueuedByTheirFirstArticle() {
		// 기간 10일 — 2033-01-22T00:00Z 부터(포함)
		long closed = StorylineRows.event(this.jdbc, "CLOSED", "fill-m", "[1,0]", 1, "2033-01-10T00:00:00Z",
				"2033-01-22T00:00:00Z", Map.of());
		long queued = StorylineRows.event(this.jdbc, "OPEN", "fill-m", "[1,0]", 1, "2033-01-25T00:00:00Z",
				"2033-01-30T00:00:00Z", Map.of());
		StorylineRows.event(this.jdbc, "CLOSED", "fill-m", "[1,0]", 1, "2033-01-01T00:00:00Z", "2033-01-21T23:59:59Z",
				Map.of());
		long merged = StorylineRows.event(this.jdbc, "OPEN", "fill-m", "[1,0]", 1, "2033-01-29T00:00:00Z",
				"2033-01-29T00:00:00Z", Map.of());
		StorylineRows.merge(this.jdbc, merged, queued);
		this.queue.enqueue(queued, null, NOW);

		LinkingFillResult result = service().fill(10);

		assertThat(result).isEqualTo(new LinkingFillResult.Filled(2, 1));
		assertThat(row(closed)).containsEntry("order_at", "2033-01-10T00:00:00Z").containsEntry("notifications", 0);
		assertThat(row(queued)).containsEntry("order_at", "2033-01-25T00:00:00Z").containsEntry("notifications", 1);
		assertThat(this.jdbc.sql("select count(*) from storyline_linking_queue where event_id = :id")
			.param("id", merged)
			.query(Long.class)
			.single()).isZero();
	}

	private StorylineLinkingAdministrationService service() {
		return new StorylineLinkingAdministrationService(this.directory, this.queue, this.transaction,
				InstantSource.fixed(NOW));
	}

	private Map<String, Object> row(long event) {
		return this.jdbc.sql("""
				select notifications,
				       to_char(order_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as order_at
				from storyline_linking_queue where event_id = :event
				""").param("event", event).query().singleRow();
	}

}
