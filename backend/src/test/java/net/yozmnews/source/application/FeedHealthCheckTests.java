package net.yozmnews.source.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.poll.FeedStatus;
import net.yozmnews.source.domain.poll.FeedStatusRepository;
import org.jobrunr.scheduling.JobScheduler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 피드 건강 검사 — 등록한 소스의 수집 상태를 SQL 로 맞추고, 고정 시각으로 검사를 직접 부른다. 커밋 뒤의 로그를 보므로 테스트를
 * 트랜잭션으로 감싸지 않는다.
 * <p>
 * 지키는 규칙: source-R30 · source-R31 · source-R47 · source-R49
 */
@Import({ TestcontainersConfiguration.class, RegistryTestCatalog.class })
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class FeedHealthCheckTests {

	private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");

	private static final String FEED_PROFILE = """
			source:
			  name: 건강일보%s
			  homepage: https://health%s.example
			  language: ko
			termsReviewedAt: 2026-10-01
			feeds:
			  - key: a
			    url: https://health.example/a.xml
			    section: 전체
			  - key: b
			    url: https://health.example/b.xml
			    section: 전체
			  - key: c
			    url: https://health.example/c.xml
			    section: 전체
			  - key: d
			    url: https://health.example/d.xml
			    section: 전체
			""";

	@Autowired
	SourceRegistration registration;

	@Autowired
	SourceOperations operations;

	@Autowired
	FeedStatusRepository statuses;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JobScheduler jobs;

	@Autowired
	JdbcClient jdbc;

	private final AtomicReference<Instant> clock = new AtomicReference<>(NOW);

	private FeedHealthCheck check;

	@BeforeEach
	void reset() {
		this.jobs.deleteRecurringJob("poll-due-feeds");
		this.jobs.deleteRecurringJob("feed-health-check");
		new RegistryRows(this.jdbc).deleteAll();
		this.clock.set(NOW);
		this.check = new FeedHealthCheck(this.statuses, this.transaction, this.clock::get);
	}

	@Test
	void judgesEnabledFeedsWritesOnlyChangesAndLogsThemAfterCommit(CapturedOutput output) {
		SourceId source = register("1");
		FeedId a = enabledFeed(source, "a");
		FeedId b = enabledFeed(source, "b");
		FeedId c = enabledFeed(source, "c");
		FeedId d = feedOf(source, "d");
		SourceId disabledSource = register("2");
		FeedId e = enabledFeed(disabledSource, "a");
		this.operations.disableSource(disabledSource);
		arrange(a, 5, "2026-10-29T00:00:00Z", "2026-10-29T00:00:00Z");
		arrange(b, 0, "2026-10-29T00:00:00Z", "2026-10-22T00:00:00Z");
		arrange(c, 0, "2026-10-29T00:00:00Z", "2026-10-29T00:00:00Z");
		arrange(d, 5, "2026-10-29T00:00:00Z", "2026-10-29T00:00:00Z");
		arrange(e, 5, "2026-10-29T00:00:00Z", "2026-10-29T00:00:00Z");

		List<FeedStatusChange> changes = this.check.run();

		assertThat(changes).extracting(change -> change.state().feedId(), FeedStatusChange::from, FeedStatusChange::to,
				FeedStatusChange::at)
			.containsExactly(
					tuple(a, FeedStatus.ACTIVE, FeedStatus.DEGRADED, NOW),
					tuple(b, FeedStatus.ACTIVE, FeedStatus.STALE, NOW));
		assertThat(status(a)).isEqualTo("DEGRADED 2026-10-30T00:00:00Z");
		assertThat(status(b)).isEqualTo("STALE 2026-10-30T00:00:00Z");
		assertThat(status(c)).isEqualTo("ACTIVE null");
		assertThat(status(d)).isEqualTo("ACTIVE null");
		assertThat(status(e)).isEqualTo("ACTIVE null");
		assertThat(output.getOut()).contains("WARN").contains("피드 상태가 바뀌었다 — 피드 " + a.value()
				+ ": ACTIVE → DEGRADED (연속 실패 5회 · 그중 영구 0회, 마지막 실패 없음, 마지막 새 항목 2026-10-29T00:00:00Z, "
				+ "새 항목의 가장 늦은 발행 시각 2026-10-29T00:00:00Z)")
			.contains("피드 상태가 바뀌었다 — 피드 " + b.value() + ": ACTIVE → STALE (연속 실패 0회 · 그중 영구 0회, 마지막 실패 없음, "
					+ "마지막 새 항목 2026-10-22T00:00:00Z, 새 항목의 가장 늦은 발행 시각 2026-10-22T00:00:00Z)");
		assertThat(line(output, "피드 " + a.value() + ": ACTIVE → DEGRADED")).contains(" WARN ");
		assertThat(line(output, "피드 " + b.value() + ": ACTIVE → STALE")).contains(" WARN ");
		assertThat(output.getOut()).doesNotContain("피드 " + c.value() + ":", "피드 " + d.value() + ":",
				"피드 " + e.value() + ":");
	}

	@Test
	void aSecondRunWithTheSameInputChangesAndLogsNothing(CapturedOutput output) {
		FeedId a = enabledFeed(register("1"), "a");
		arrange(a, 5, "2026-10-29T00:00:00Z", "2026-10-29T00:00:00Z");
		this.check.run();
		int logged = output.getOut().length();

		List<FeedStatusChange> second = this.check.run();
		String afterSecond = status(a);
		this.clock.set(Instant.parse("2026-10-30T01:00:00Z"));
		List<FeedStatusChange> later = this.check.run();

		assertThat(second).isEmpty();
		assertThat(afterSecond).isEqualTo("DEGRADED 2026-10-30T00:00:00Z");
		assertThat(later).isEmpty();
		assertThat(status(a)).isEqualTo("DEGRADED 2026-10-30T00:00:00Z");
		assertThat(output.getOut().substring(logged)).doesNotContain("피드 상태가 바뀌었다");
	}

	@Test
	void aRecoveredFeedReturnsToActiveAndIsLoggedAtInfo(CapturedOutput output) {
		FeedId a = enabledFeed(register("1"), "a");
		arrange(a, 5, "2026-10-29T00:00:00Z", "2026-10-29T00:00:00Z");
		this.check.run();
		arrange(a, 0, "2026-10-30T00:30:00Z", "2026-10-30T00:30:00Z");
		// 성공은 마지막 실패를 지우지 않는다
		this.jdbc.sql("""
				update feed_poll_states set last_error_reason = 'NOT_FOUND', last_error_message = '문구',
				last_error_status = 404, last_error_at = '2026-10-29T12:00:00Z' where feed_id = :feed
				""").param("feed", a.value()).update();
		this.clock.set(Instant.parse("2026-10-30T01:00:00Z"));

		List<FeedStatusChange> changes = this.check.run();

		assertThat(changes).extracting(FeedStatusChange::to).containsExactly(FeedStatus.ACTIVE);
		assertThat(status(a)).isEqualTo("ACTIVE 2026-10-30T01:00:00Z");
		assertThat(line(output, "피드 " + a.value() + ": DEGRADED → ACTIVE")).contains(" INFO ")
			.contains("(연속 실패 0회 · 그중 영구 0회, 마지막 실패 NOT_FOUND(PERMANENT), 마지막 새 항목 2026-10-30T00:30:00Z, "
					+ "새 항목의 가장 늦은 발행 시각 2026-10-30T00:30:00Z)");
	}

	@Test
	void turningAFeedOffAndOnLeavesItsStatusToTheNextJudgement() {
		SourceId source = register("1");
		FeedId a = enabledFeed(source, "a");
		arrange(a, 5, "2026-10-29T00:00:00Z", "2026-10-29T00:00:00Z");
		this.check.run();

		this.operations.disableFeed(a);
		String afterDisabling = status(a);
		arrange(a, 0, "2026-10-30T00:30:00Z", "2026-10-30T00:30:00Z");
		this.clock.set(Instant.parse("2026-10-30T01:00:00Z"));
		this.check.run();
		String whileDisabled = status(a);
		this.operations.enableFeed(a);
		String afterEnabling = status(a);
		this.check.run();

		assertThat(afterDisabling).isEqualTo("DEGRADED 2026-10-30T00:00:00Z");
		assertThat(whileDisabled).isEqualTo("DEGRADED 2026-10-30T00:00:00Z");
		assertThat(afterEnabling).isEqualTo("DEGRADED 2026-10-30T00:00:00Z");
		assertThat(status(a)).isEqualTo("ACTIVE 2026-10-30T01:00:00Z");
	}

	private SourceId register(String n) {
		Registration registered = this.registration.registerOperatorProfile(FEED_PROFILE.formatted(n, n));
		assertThat(registered).isInstanceOf(Registration.Registered.class);
		SourceId source = ((Registration.Registered) registered).id();
		this.operations.enableSource(source);
		return source;
	}

	private FeedId feedOf(SourceId source, String key) {
		return new FeedId(new RegistryRows(this.jdbc).feedId(source.value(), key));
	}

	private FeedId enabledFeed(SourceId source, String key) {
		FeedId feed = feedOf(source, key);
		this.operations.enableFeed(feed);
		return feed;
	}

	/** 수집 상태의 판정 입력 — 새 항목의 발행 시각은 마지막 새 항목 시각과 같게 둔다 */
	private void arrange(FeedId feed, int failures, String lastSuccessAt, String lastNewItemAt) {
		this.jdbc.sql("""
				update feed_poll_states set consecutive_failures = :failures, consecutive_permanent_failures = 0,
				last_success_at = cast(:success as timestamptz), last_new_item_at = cast(:newItem as timestamptz),
				latest_published_at = cast(:newItem as timestamptz) where feed_id = :feed
				""")
			.param("failures", failures)
			.param("success", lastSuccessAt)
			.param("newItem", lastNewItemAt)
			.param("feed", feed.value())
			.update();
	}

	private String status(FeedId feed) {
		return this.jdbc.sql("select status, status_changed_at from feed_poll_states where feed_id = :feed")
			.param("feed", feed.value())
			.query((rs, row) -> {
				@Nullable OffsetDateTime changedAt = rs.getObject(2, OffsetDateTime.class);
				return rs.getString(1) + " " + ((changedAt != null) ? changedAt.toInstant() : null);
			})
			.single();
	}

	private static String line(CapturedOutput output, String fragment) {
		return output.getOut().lines().filter(line -> line.contains(fragment)).findFirst().orElseThrow();
	}

}
