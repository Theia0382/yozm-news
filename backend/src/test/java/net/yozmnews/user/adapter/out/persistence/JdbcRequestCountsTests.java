package net.yozmnews.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.platform.GuardedAction;
import net.yozmnews.user.domain.guard.LimitAxis;
import net.yozmnews.user.domain.guard.LimitKey;
import net.yozmnews.user.domain.guard.LimitWindow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 요청 제한 기록 — 같은 키 · 창은 한 행에 더하고, 창이나 키가 다르면 1부터 센다. 정리는 끝이 기준보다 이른 창만 지운다(기준과 같은 끝은
 * 남는다).
 * <p>
 * 지키는 규칙: user-R39 · user-R42
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcRequestCounts.class })
class JdbcRequestCountsTests {

	private static final LimitKey KEY = LimitKey.of(GuardedAction.SIGN_IN, LimitAxis.CLIENT_ADDRESS, "192.0.2.1");

	private static final LimitKey OTHER = LimitKey.of(GuardedAction.SIGN_IN, LimitAxis.CLIENT_ADDRESS, "192.0.2.2");

	private static final LimitWindow TEN = new LimitWindow(Instant.parse("2026-11-14T10:00:00Z"),
			Instant.parse("2026-11-14T10:10:00Z"));

	private static final LimitWindow NEXT = new LimitWindow(Instant.parse("2026-11-14T10:10:00Z"),
			Instant.parse("2026-11-14T10:20:00Z"));

	@Autowired
	JdbcRequestCounts counts;

	@Autowired
	JdbcClient jdbc;

	@Test
	void sameKeyAndWindowAddsUpInOneRow() {
		assertThat(this.counts.increment(KEY, TEN)).isEqualTo(1);
		assertThat(this.counts.increment(KEY, TEN)).isEqualTo(2);
		assertThat(this.counts.increment(KEY, TEN)).isEqualTo(3);
		assertThat(rows()).isEqualTo(1);
	}

	@Test
	void anotherWindowOrKeyStartsFromOne() {
		this.counts.increment(KEY, TEN);
		this.counts.increment(KEY, TEN);

		assertThat(this.counts.increment(KEY, NEXT)).isEqualTo(1);
		assertThat(this.counts.increment(OTHER, TEN)).isEqualTo(1);
		assertThat(rows()).isEqualTo(3);
	}

	@Test
	void forgetsOnlyWindowsThatEndedBeforeTheCutoff() {
		this.counts.increment(KEY, TEN);
		this.counts.increment(OTHER, TEN);
		this.counts.increment(KEY, NEXT);

		assertThat(this.counts.forgetEndedBefore(Instant.parse("2026-11-14T10:10:00.001Z"))).isEqualTo(2);
		assertThat(rows()).isEqualTo(1);
		assertThat(this.counts.forgetEndedBefore(Instant.parse("2026-11-14T10:20:00Z"))).isZero();
		assertThat(rows()).isEqualTo(1);
	}

	private int rows() {
		return this.jdbc.sql("select count(*) from request_limit_counts").query(Integer.class).single();
	}

}
