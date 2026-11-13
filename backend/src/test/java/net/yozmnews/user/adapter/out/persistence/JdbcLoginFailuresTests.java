package net.yozmnews.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.user.domain.throttle.EmailDigest;
import net.yozmnews.user.domain.throttle.FailureRecord;
import net.yozmnews.user.domain.throttle.ThrottledAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 실패 기록 저장소 — 종류마다 따로 잠그고 · 쓰고 · 지우며, 정리는 종류와 무관하게 마지막 실패가 기준 시각보다 이른 기록만 지운다(기준과 같은
 * 기록은 남는다).
 * <p>
 * 지키는 규칙: user-R19
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcLoginFailures.class })
class JdbcLoginFailuresTests {

	private static final Instant CUTOFF = Instant.parse("2026-11-12T09:00:00Z");

	private static final EmailDigest EARLIER = EmailDigest.of("earlier@example.com");

	private static final EmailDigest AT_CUTOFF = EmailDigest.of("at-cutoff@example.com");

	private static final EmailDigest LATER = EmailDigest.of("later@example.com");

	@Autowired
	JdbcLoginFailures failures;

	@Autowired
	JdbcClient jdbc;

	@Test
	void forgetsOnlyRecordsWhoseLastFailureIsBeforeTheCutoffWhateverTheirKind() {
		store(ThrottledAttempt.SIGN_IN, EARLIER, Instant.parse("2026-11-12T08:59:59Z"));
		store(ThrottledAttempt.RECOVERY, EARLIER, Instant.parse("2026-11-12T08:59:58Z"));
		store(ThrottledAttempt.SIGN_IN, AT_CUTOFF, Instant.parse("2026-11-12T09:00:00Z"));
		store(ThrottledAttempt.RECOVERY, LATER, Instant.parse("2026-11-12T09:00:01Z"));

		int forgotten = this.failures.forgetOlderThan(CUTOFF);

		assertThat(forgotten).isEqualTo(2);
		assertThat(rows(EARLIER)).isZero();
		assertThat(rows(AT_CUTOFF)).isOne();
		assertThat(rows(LATER)).isOne();
		assertThat(this.jdbc.sql("select count(*) from login_failures").query(Integer.class).single()).isEqualTo(2);
	}

	@Test
	void eachKindKeepsItsOwnRecordForTheSameEmail() {
		EmailDigest digest = EmailDigest.of("both@example.com");
		store(ThrottledAttempt.SIGN_IN, digest, Instant.parse("2026-11-12T09:00:00Z"));

		assertThat(this.failures.lock(ThrottledAttempt.RECOVERY, digest)).isEqualTo(FailureRecord.NONE);
		this.failures.forget(ThrottledAttempt.RECOVERY, digest);
		assertThat(this.failures.lock(ThrottledAttempt.SIGN_IN, digest))
			.isEqualTo(new FailureRecord(2, Instant.parse("2026-11-12T09:00:00Z")));
	}

	private void store(ThrottledAttempt attempt, EmailDigest digest, Instant lastFailedAt) {
		this.failures.lock(attempt, digest);
		this.failures.record(attempt, digest, new FailureRecord(2, lastFailedAt));
	}

	private int rows(EmailDigest digest) {
		return this.jdbc.sql("select count(*) from login_failures where email_digest = :digest")
			.param("digest", digest.value())
			.query(Integer.class)
			.single();
	}

}
