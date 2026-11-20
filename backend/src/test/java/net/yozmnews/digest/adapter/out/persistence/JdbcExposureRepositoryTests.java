package net.yozmnews.digest.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.digest.DigestRounds;
import net.yozmnews.digest.DigestRows;
import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.digest.domain.interest.ExposureRow;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/**
 * 노출 기록 읽기 — 그 사용자의 READY 다이제스트 모두(같은 날짜의 덮인 것 포함)의 섹션 사건 행, 행마다 몫 1 ÷ 그 섹션의 사건 행 수, 끝낸
 * 시각의 반열린 범위.
 * <p>
 * 실제 PostgreSQL 위에서 다이제스트 · 섹션 · 섹션 사건 행을 직접 넣는다. 섹션 사건의 사건은 행만 있으면 되므로 사건 테이블에 넣지 않는다(모듈
 * 사이라 FK 가 없다).
 * <p>
 * 지키는 규칙: digest-R24
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class JdbcExposureRepositoryTests {

	@Autowired
	JobScheduler digestJobs;

	private static final long USER = 42;

	private static final long OTHER = 43;

	private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant TO = Instant.parse("2026-11-10T00:00:00Z");

	@Autowired
	ExposureRepository exposures;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
	}

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@Test
	void eachEventRowOfAnItemTakesOneOverTheNumberOfItsEvents() {
		// 항목 {901, 902, 903} · 항목 {904, 905} 를 한 다이제스트에서 — 몫은 섹션마다 1/3 · 1/2 다
		long digest = DigestRows.digest(this.jdbc, USER, "2026-11-02", "2026-11-02T06:00:00Z");
		DigestRows.item(this.jdbc, digest, 901, 902, 903);
		DigestRows.item(this.jdbc, digest, 904, 905);

		List<ExposureRow> rows = this.exposures.exposures(new UserId(USER), FROM, TO);

		assertThat(rows).extracting(row -> row.event().value()).containsExactly(901L, 902L, 903L, 904L, 905L);
		assertThat(rows).extracting(ExposureRow::share)
			.containsExactly(1.0 / 3, 1.0 / 3, 1.0 / 3, 0.5, 0.5);
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.digestId()).isEqualTo(digest);
			assertThat(row.exposedAt()).isEqualTo(Instant.parse("2026-11-02T06:00:00Z"));
		});
	}

	@Test
	void anEventAloneInItsItemTakesTheWholeShare() {
		// 대조 — 사건 하나인 항목 하나만 든 다이제스트는 몫 1(섹션 단위와 다이제스트 단위가 같은 값을 내는 입력)
		long digest = DigestRows.digest(this.jdbc, USER, "2026-11-02", "2026-11-02T06:00:00Z");
		DigestRows.item(this.jdbc, digest, 901);

		assertThat(this.exposures.exposures(new UserId(USER), FROM, TO)).first()
			.satisfies(row -> assertThat(row.share()).isCloseTo(1.0, within(1e-12)))
			.satisfies(row -> assertThat(row.event().value()).isEqualTo(901L));
	}

	@Test
	void everyReadyDigestOfTheUserCountsIncludingOnesCoveredOnTheSameDate() {
		// 11-02 — READY 둘: 앞(덮였다)은 사건 901, 뒤(보인다)는 사건 902
		long covered = DigestRows.digest(this.jdbc, USER, "2026-11-02", "2026-11-02T06:00:00Z");
		DigestRows.item(this.jdbc, covered, 901);
		long shown = DigestRows.digest(this.jdbc, USER, "2026-11-02", "2026-11-02T08:00:00Z");
		DigestRows.item(this.jdbc, shown, 902);
		// 다른 사용자의 READY(사건 903)는 이 사용자의 노출이 아니다
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, OTHER, "2026-11-02", "2026-11-02T06:00:00Z"), 903);
		// READY 가 아닌 다이제스트(사건 909)는 노출이 아니다
		long empty = DigestRows.digest(this.jdbc, USER, "2026-11-03", "2026-11-03T06:00:00Z");
		DigestRows.item(this.jdbc, empty, 909);
		this.jdbc.sql("update digests set status = 'EMPTY' where id = :id").param("id", empty).update();

		assertThat(this.exposures.exposures(new UserId(USER), FROM, TO)).containsExactly(
				new ExposureRow(covered, new EventId(901), Instant.parse("2026-11-02T06:00:00Z"), 1.0),
				new ExposureRow(shown, new EventId(902), Instant.parse("2026-11-02T08:00:00Z"), 1.0));
	}

	@Test
	void anEventShownByEachDigestIsARowPerDigestInCompletionOrder() {
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-03", "2026-11-03T06:00:00Z"), 904);
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-05", "2026-11-05T06:00:00Z"), 904);
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-04", "2026-11-04T06:00:00Z"), 904);

		assertThat(this.exposures.exposures(new UserId(USER), FROM, TO)).extracting(row -> row.exposedAt().toString())
			.containsExactly("2026-11-03T06:00:00Z", "2026-11-04T06:00:00Z", "2026-11-05T06:00:00Z");
	}

	@Test
	void theCompletionTimeRangeIsHalfOpen() {
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-01", "2026-11-01T00:00:00Z"), 905);
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-10-31", "2026-10-31T23:59:59Z"), 906);
		DigestRows.item(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-10", "2026-11-10T00:00:00Z"), 907);

		assertThat(this.exposures.exposures(new UserId(USER), FROM, TO)).extracting(row -> row.event().value())
			.containsExactly(905L);
	}

}
