package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.DigestCandidateRepository;
import net.yozmnews.digest.domain.candidate.SelectionReason;
import net.yozmnews.digest.domain.content.DigestSection;
import net.yozmnews.digest.domain.content.DigestSectionRepository;
import net.yozmnews.digest.domain.digest.Digest;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.digest.DigestRepository;
import net.yozmnews.digest.domain.digest.DigestTrigger;
import net.yozmnews.digest.domain.output.CitedDigest;
import net.yozmnews.digest.domain.output.CitedStory;
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
 * 후보 행 보존 정리 — 만든 지 30일이 지난 다이제스트의 후보 행만 지우고, 다이제스트 · 섹션 · 인용은 남긴다.
 * <p>
 * 지키는 규칙: digest-R11
 * <p>
 * 실제 PostgreSQL 위에서 고정 시계로 경계 양쪽(30일 + 1초 전, 30일 − 1초 전)을 본다. 후보 행의 기사는 행만 있으면 되므로 기사 테이블에
 * 넣지 않는다(모듈 사이라 FK 가 없다).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class CandidateCleanupTests {

	@Autowired
	JobScheduler digestJobs;

	private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");

	@Autowired
	DigestRepository digests;

	@Autowired
	DigestSectionRepository sections;

	@Autowired
	DigestCandidateRepository candidates;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
	}

	private DigestId digestCreatedAt(Instant createdAt, long article) {
		DigestId id = this.digests.save(Digest.schedule(new UserId(42), LocalDate.parse("2026-10-17"),
				new DigestPeriod(createdAt.minusSeconds(86_400), createdAt), DigestTrigger.MANUAL, UUID.randomUUID(), createdAt)).id();
		DigestCandidate candidate = new DigestCandidate(new ArticleId(article), SelectionReason.SUBSCRIBED);
		this.candidates.replace(id, CandidateSet.of(List.of(candidate)));
		this.sections.saveAll(DigestSection.of(id,
				new CitedDigest("흐름", List.of(new CitedStory("t", "s", List.of(candidate))), List.of())));
		return id;
	}

	private long count(String sql, DigestId id) {
		return this.jdbc.sql(sql).param("id", id.value()).query(Long.class).single();
	}

	@BeforeEach
	void stopDigestRounds() {
		DigestRounds.stop(this.digestJobs);
	}

	@Test
	void removesOnlyTheCandidatesOfDigestsOlderThanThirtyDays() {
		// 30일 = 2,592,000초
		DigestId old = digestCreatedAt(Instant.parse("2026-10-16T23:59:59Z"), 901);
		DigestId recent = digestCreatedAt(Instant.parse("2026-10-17T00:00:01Z"), 902);

		int deleted = new CandidateCleanup(this.candidates, InstantSource.fixed(NOW)).run();

		assertThat(deleted).isEqualTo(1);
		assertThat(count("select count(*) from digest_candidates where digest_id = :id", old)).isZero();
		assertThat(count("select count(*) from digest_candidates where digest_id = :id", recent)).isEqualTo(1);
		assertThat(count("select count(*) from digests where id = :id", old)).isEqualTo(1);
		assertThat(count("select count(*) from digest_sections where digest_id = :id", old)).isEqualTo(2);
		assertThat(count("""
				select count(*) from digest_items i join digest_sections s on s.id = i.digest_section_id
				where s.digest_id = :id
				""", old)).isEqualTo(1);
	}

	@Test
	void removesNothingWhenEveryDigestIsRecent() {
		DigestId recent = digestCreatedAt(Instant.parse("2026-11-15T00:00:00Z"), 903);

		assertThat(new CandidateCleanup(this.candidates, InstantSource.fixed(NOW)).run()).isZero();
		assertThat(count("select count(*) from digest_candidates where digest_id = :id", recent)).isEqualTo(1);
	}

}
