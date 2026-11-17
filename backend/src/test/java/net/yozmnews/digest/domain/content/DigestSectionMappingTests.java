package net.yozmnews.digest.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.SelectionReason;
import net.yozmnews.digest.domain.digest.Completion;
import net.yozmnews.digest.domain.digest.Digest;
import net.yozmnews.digest.domain.digest.DigestFailure;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.digest.DigestRepository;
import net.yozmnews.digest.domain.digest.DigestStatus;
import net.yozmnews.digest.domain.digest.DigestTrigger;
import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.digest.domain.output.CitedDigest;
import net.yozmnews.digest.domain.output.CitedHeadline;
import net.yozmnews.digest.domain.output.CitedStory;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 다이제스트 상태 · 내용 aggregate 의 매핑 — 실제 PostgreSQL 위의 저장 · 읽기 왕복, 인용의 순서 칸, 버전, 테이블 검사.
 * <p>
 * 지키는 규칙: digest-R1 · digest-R7
 * <p>
 * 슬라이스 시험이라 시험마다 트랜잭션이 되돌린다. 기대값은 손으로 쓴 리터럴이다.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DigestSectionMappingTests {

	private static final Instant T0 = Instant.parse("2026-11-15T22:45:00Z");

	private static final Instant T1 = Instant.parse("2026-11-15T22:46:00Z");

	private static final LlmModel MODEL = new LlmModel(Provider.OPENAI, "gpt-5.5-mini", 100_000, true);

	private static final DigestCandidate C11 = new DigestCandidate(new ArticleId(11), SelectionReason.SUBSCRIBED);

	private static final DigestCandidate C12 = new DigestCandidate(new ArticleId(12), SelectionReason.MAJOR_STORYLINE);

	@Autowired
	DigestRepository digests;

	@Autowired
	DigestSectionRepository sections;

	@Autowired
	JdbcClient jdbc;

	private Digest scheduled() {
		return this.digests.save(Digest.schedule(new UserId(42), LocalDate.parse("2026-11-16"),
				new DigestPeriod(Instant.parse("2026-11-14T22:00:00Z"), Instant.parse("2026-11-15T22:00:00Z")),
				DigestTrigger.SCHEDULED, T0));
	}

	@Test
	void roundTripsTheStateThroughEachTransition() {
		Digest scheduled = scheduled();
		assertThat(scheduled.version()).contains(0L);

		Digest running = this.digests.save(scheduled.start(MODEL, 2, T0));
		Digest ready = this.digests
			.save(running.complete(new Completion.Ready(new TokenUsage(1_200, 340), Duration.ofMillis(2_000)), T1));

		Digest read = this.digests.lock(ready.id().value()).orElseThrow();
		assertThat(read.status()).isEqualTo(DigestStatus.READY);
		assertThat(read.version()).contains(2L);
		assertThat(read.digestDate()).isEqualTo(LocalDate.parse("2026-11-16"));
		assertThat(read.period())
			.isEqualTo(new DigestPeriod(Instant.parse("2026-11-14T22:00:00Z"), Instant.parse("2026-11-15T22:00:00Z")));
		assertThat(read.provider()).contains("openai");
		assertThat(read.model()).contains("gpt-5.5-mini");
		assertThat(read.candidateCount()).contains(2);
		assertThat(read.usage()).contains(new TokenUsage(1_200, 340));
		assertThat(read.durationMs()).contains(2_000L);
		assertThat(read.startedAt()).contains(T0);
		assertThat(read.completedAt()).contains(T1);
	}

	@Test
	void storesADigestMadeWithAnOpenRouterModel() {
		LlmModel openRouter = new LlmModel(Provider.OPENROUTER, "openai/gpt-6.1-terra", 32_000, true);

		Digest running = this.digests.save(scheduled().start(openRouter, 2, T0));

		Digest read = this.digests.lock(running.id().value()).orElseThrow();
		assertThat(read.provider()).contains("openrouter");
		assertThat(read.model()).contains("openai/gpt-6.1-terra");
	}

	@Test
	void storesTheFailureCode() {
		Digest failed = this.digests.save(this.digests.save(scheduled().start(MODEL, 2, T0))
			.complete(new Completion.Failed(DigestFailure.UNREADABLE_OUTPUT, "JSON 이 아니다", null, null), T1));

		assertThat(this.jdbc.sql("select status, error_code, error_message from digests where id = :id")
			.param("id", failed.id().value())
			.query()
			.singleRow()).containsExactlyInAnyOrderEntriesOf(
					Map.of("status", "FAILED", "error_code", "UNREADABLE_OUTPUT", "error_message", "JSON 이 아니다"));
	}

	@Test
	void refusesAStaleVersion() {
		Digest scheduled = scheduled();
		this.digests.save(scheduled.start(MODEL, 2, T0));

		assertThatExceptionOfType(OptimisticLockingFailureException.class)
			.isThrownBy(() -> this.digests.save(scheduled.start(MODEL, 3, T1)));
	}

	@Test
	void roundTripsSectionsWithOrderedItems() {
		Digest digest = scheduled();
		CitedDigest cited = new CitedDigest("흐름", List.of(new CitedStory("하나", "요약", List.of(C12, C11))),
				List.of(new CitedHeadline(C11, "한 줄")));

		this.sections.saveAll(DigestSection.of(digest.id(), cited));

		assertThat(this.jdbc.sql("""
				select s.kind, s.position as section, s.title, s.body, i.position as item, i.article_id, i.note,
				       i.selection_reason
				from digest_sections s left join digest_items i on i.digest_section_id = s.id
				where s.digest_id = :digest
				order by s.position, i.position
				""").param("digest", digest.id().value()).query().listOfRows())
			.extracting(row -> List.of(String.valueOf(row.get("kind")), String.valueOf(row.get("section")),
					String.valueOf(row.get("title")), String.valueOf(row.get("body")), String.valueOf(row.get("item")),
					String.valueOf(row.get("article_id")), String.valueOf(row.get("note")),
					String.valueOf(row.get("selection_reason"))))
			.containsExactly(List.of("OVERVIEW", "0", "null", "흐름", "null", "null", "null", "null"),
					List.of("TOP_STORY", "1", "하나", "요약", "0", "12", "null", "MAJOR_STORYLINE"),
					List.of("TOP_STORY", "1", "하나", "요약", "1", "11", "null", "SUBSCRIBED"),
					List.of("MORE_HEADLINES", "2", "null", "null", "0", "11", "한 줄", "SUBSCRIBED"));

		List<DigestSection> read = new ArrayList<>();
		this.sections.findAll().forEach(read::add);
		DigestSection story = read.stream().filter(section -> section.kind() == SectionKind.TOP_STORY).findFirst()
			.orElseThrow();
		assertThat(story.items()).containsExactly(new DigestItem(12, null, SelectionReason.MAJOR_STORYLINE),
				new DigestItem(11, null, SelectionReason.SUBSCRIBED));
	}

	@Test
	void refusesAFailureWithoutACode() {
		Digest digest = scheduled();

		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> this.jdbc
			.sql("update digests set status = 'FAILED', completed_at = now() where id = :id")
			.param("id", digest.id().value())
			.update());
	}

}
