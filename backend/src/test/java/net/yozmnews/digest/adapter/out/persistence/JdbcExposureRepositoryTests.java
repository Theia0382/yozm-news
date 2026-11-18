package net.yozmnews.digest.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.DigestRows;
import net.yozmnews.digest.domain.exposure.ExposureRepository;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import net.yozmnews.digest.DigestRounds;

/**
 * 노출 기록 읽기 — 그 사용자의 READY 다이제스트 모두(같은 날짜의 덮인 것 포함)의 인용, 기사마다 가장 늦게 끝낸 시각, 끝낸 시각의 반열린 범위.
 * <p>
 * 실제 PostgreSQL 위에서 다이제스트 · 섹션 · 인용 행을 직접 넣는다. 인용의 기사는 행만 있으면 되므로 기사 테이블에 넣지 않는다(모듈 사이라 FK 가
 * 없다).
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
	void everyReadyDigestOfTheUserCountsIncludingOnesCoveredOnTheSameDate() {
		// 11-02 — READY 둘: 앞(덮였다)은 기사 901, 뒤(보인다)는 기사 902
		DigestRows.cite(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-02", "2026-11-02T06:00:00Z"), 901);
		DigestRows.cite(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-02", "2026-11-02T08:00:00Z"), 902);
		// 다른 사용자의 READY(기사 903)는 이 사용자의 노출이 아니다
		DigestRows.cite(this.jdbc, DigestRows.digest(this.jdbc, OTHER, "2026-11-02", "2026-11-02T06:00:00Z"), 903);

		assertThat(this.exposures.latestExposures(new UserId(USER), FROM, TO)).isEqualTo(
				Map.of(new ArticleId(901), Instant.parse("2026-11-02T06:00:00Z"), new ArticleId(902),
						Instant.parse("2026-11-02T08:00:00Z")));
	}

	@Test
	void anArticleCitedTwiceTakesTheLatestTime() {
		DigestRows.cite(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-03", "2026-11-03T06:00:00Z"), 904);
		DigestRows.cite(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-05", "2026-11-05T06:00:00Z"), 904);
		DigestRows.cite(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-04", "2026-11-04T06:00:00Z"), 904);

		assertThat(this.exposures.latestExposures(new UserId(USER), FROM, TO))
			.isEqualTo(Map.of(new ArticleId(904), Instant.parse("2026-11-05T06:00:00Z")));
	}

	@Test
	void theCompletionTimeRangeIsHalfOpen() {
		DigestRows.cite(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-01", "2026-11-01T00:00:00Z"), 905);
		DigestRows.cite(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-10-31", "2026-10-31T23:59:59Z"), 906);
		DigestRows.cite(this.jdbc, DigestRows.digest(this.jdbc, USER, "2026-11-10", "2026-11-10T00:00:00Z"), 907);

		assertThat(this.exposures.latestExposures(new UserId(USER), FROM, TO))
			.isEqualTo(Map.of(new ArticleId(905), Instant.parse("2026-11-01T00:00:00Z")));
	}

}
