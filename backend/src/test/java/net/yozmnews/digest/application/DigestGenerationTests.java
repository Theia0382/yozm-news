package net.yozmnews.digest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.adapter.out.fake.FakeDigestGenerator;
import net.yozmnews.digest.domain.candidate.DigestCandidate;
import net.yozmnews.digest.domain.candidate.DigestCandidateRepository;
import net.yozmnews.digest.domain.candidate.SelectionReason;
import net.yozmnews.digest.domain.content.DigestSectionRepository;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.digest.domain.digest.DigestPeriod;
import net.yozmnews.digest.domain.digest.DigestRepository;
import net.yozmnews.digest.domain.digest.DigestStatus;
import net.yozmnews.digest.domain.digest.DigestTrigger;
import net.yozmnews.digest.domain.generator.DigestGenerator;
import net.yozmnews.digest.domain.generator.DigestRequest;
import net.yozmnews.digest.domain.generator.GeneratedOutput;
import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.event.EventArticles;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 다이제스트 생성 흐름 — 예약 · 시작 · 생성 · 읽기 · 인용 검증 · 저장이 실제 PostgreSQL 위에서 끝까지 간다.
 * <p>
 * 지키는 규칙: digest-R2 · digest-R8 · digest-R9 · digest-R12
 * <p>
 * 시험을 트랜잭션으로 감싸지 않는다 — 유스케이스가 트랜잭션을 둘로 나눈다. 생성기 공급자 · 시계는 유스케이스의 패키지 생성자로 넣는다(대본
 * 생성기 · 로컬 대체물 · 생성기 없음). 기사 행은 SQL 로 넣는다. 기대값은 손으로 쓴 리터럴이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestGenerationTests {

	private static final Instant T0 = Instant.parse("2026-11-15T22:45:00Z");

	private static final LlmModel MODEL = new LlmModel(Provider.ANTHROPIC, "claude-sonnet-5-5", 100_000, true);

	@Autowired
	DigestRepository digests;

	@Autowired
	DigestSectionRepository sections;

	@Autowired
	DigestCandidateRepository candidates;

	@Autowired
	ArticleDirectory articles;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	private final AtomicReference<Instant> now = new AtomicReference<>(T0);

	private final InstantSource clock = () -> this.now.get();

	private final List<Long> articleIds = new ArrayList<>();

	@BeforeEach
	void fiveArticles() {
		clear();
		for (int index = 1; index <= 5; index++) {
			this.articleIds.add(EventArticles.insert(this.jdbc, "다이제스트 시험 기사 " + index, null));
		}
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from digests").update();
		for (Long id : this.articleIds) {
			this.jdbc.sql("delete from article_feed_entries where article_id = :id").param("id", id).update();
			this.jdbc.sql("delete from articles where id = :id").param("id", id).update();
		}
		this.articleIds.clear();
	}

	private DigestGeneration generation(@Nullable DigestGenerator generator) {
		return new DigestGeneration(this.digests, this.sections, this.candidates, this.articles, () -> generator,
				this.transaction, this.clock);
	}

	private DigestId schedule(DigestGeneration generation) {
		return generation.schedule(new UserId(42), LocalDate.parse("2026-11-16"),
				new DigestPeriod(Instant.parse("2026-11-14T22:00:00Z"), Instant.parse("2026-11-15T22:00:00Z")),
				DigestTrigger.SCHEDULED);
	}

	private List<DigestCandidate> candidates(int count) {
		List<DigestCandidate> list = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			SelectionReason reason = (index == 1) ? SelectionReason.MAJOR_STORYLINE : SelectionReason.SUBSCRIBED;
			list.add(new DigestCandidate(new ArticleId(this.articleIds.get(index)), reason));
		}
		return list;
	}

	/** 대본 생성기 — 부를 때마다 수를 세고, 시계를 2초 넘기고, 요청으로 원문을 만든다 */
	private DigestGenerator scripted(AtomicInteger calls, Function<DigestRequest, String> text) {
		return request -> {
			calls.incrementAndGet();
			this.now.set(this.now.get().plusSeconds(2));
			return new GeneratedOutput(text.apply(request), new TokenUsage(1_200, 340));
		};
	}

	private Map<String, Object> row(DigestId id) {
		return this.jdbc.sql("""
				select status, provider, model, candidate_count, input_tokens, output_tokens, duration_ms, error_code,
				       error_message, completed_at is not null as completed
				from digests where id = :id
				""").param("id", id.value()).query().singleRow();
	}

	private long count(String sql, DigestId id) {
		return this.jdbc.sql(sql).param("id", id.value()).query(Long.class).single();
	}

	private List<String> candidateRows(DigestId id) {
		return this.jdbc.sql("select alias || ':' || article_id from digest_candidates where digest_id = :id order by alias")
			.param("id", id.value())
			.query(String.class)
			.list();
	}

	private List<String> itemRows(DigestId id) {
		return this.jdbc.sql("""
				select s.kind || ':' || s.position || ':' || i.position || ':' || i.article_id || ':' || i.selection_reason
				from digest_sections s join digest_items i on i.digest_section_id = s.id
				where s.digest_id = :id order by s.position, i.position
				""").param("id", id.value()).query(String.class).list();
	}

	private String a(int index) {
		return String.valueOf(this.articleIds.get(index - 1));
	}

	@Test
	void finishesEmptyWithoutCallingTheGeneratorWhenThereAreNoCandidates() {
		AtomicInteger calls = new AtomicInteger();
		DigestGeneration generation = generation(scripted(calls, request -> "{}"));
		DigestId id = schedule(generation);

		assertThat(generation.run(id, MODEL, List.of())).isEqualTo(DigestStatus.EMPTY);

		assertThat(calls).hasValue(0);
		Map<String, Object> row = row(id);
		assertThat(row).containsEntry("status", "EMPTY")
			.containsEntry("provider", "anthropic")
			.containsEntry("model", "claude-sonnet-5-5")
			.containsEntry("candidate_count", 0)
			.containsEntry("completed", true);
		assertThat(row.get("input_tokens")).isNull();
		assertThat(row.get("duration_ms")).isNull();
		assertThat(candidateRows(id)).isEmpty();
		assertThat(count("select count(*) from digest_sections where digest_id = :id", id)).isZero();
	}

	@Test
	void clearsTheEarlierCandidatesWhenRestartedWithoutCandidates() {
		DigestGeneration failing = generation(request -> {
			throw new IllegalStateException("공급자 장애");
		});
		DigestId id = schedule(failing);
		assertThatIllegalStateException().isThrownBy(() -> failing.run(id, MODEL, candidates(3)));
		assertThat(candidateRows(id)).hasSize(3);

		assertThat(failing.run(id, MODEL, List.of())).isEqualTo(DigestStatus.EMPTY);

		assertThat(candidateRows(id)).isEmpty();
		assertThat(row(id)).containsEntry("candidate_count", 0);
	}

	@Test
	void refusesToRunInsideATransaction() {
		AtomicInteger calls = new AtomicInteger();
		DigestGeneration generation = generation(scripted(calls, request -> "not json"));
		DigestId id = schedule(generation);

		assertThatIllegalStateException()
			.isThrownBy(() -> this.transaction.executeWithoutResult(status -> generation.run(id, MODEL, candidates(2))))
			.withMessage("다이제스트 생성을 트랜잭션 안에서 불렀다 — 생성기 호출은 트랜잭션 밖에서 한다");

		assertThat(calls).hasValue(0);
		assertThat(row(id)).containsEntry("status", "SCHEDULED");
		assertThat(candidateRows(id)).isEmpty();
	}

	@Test
	void storesTheLocalSubstitutesDigestForFiveCandidates(CapturedOutput log) {
		DigestGeneration generation = generation(new FakeDigestGenerator());
		DigestId id = schedule(generation);

		assertThat(generation.run(id, MODEL, candidates(5))).isEqualTo(DigestStatus.READY);

		assertThat(row(id)).containsEntry("status", "READY")
			.containsEntry("provider", "anthropic")
			.containsEntry("model", "claude-sonnet-5-5")
			.containsEntry("candidate_count", 5)
			.containsEntry("input_tokens", 0)
			.containsEntry("output_tokens", 0)
			.containsEntry("duration_ms", 0L)
			.containsEntry("completed", true);
		assertThat(candidateRows(id)).containsExactly("A1:" + a(1), "A2:" + a(2), "A3:" + a(3), "A4:" + a(4),
				"A5:" + a(5));
		assertThat(count("select count(*) from digest_sections where digest_id = :id", id)).isEqualTo(5);
		assertThat(itemRows(id)).containsExactly("TOP_STORY:1:0:" + a(1) + ":SUBSCRIBED",
				"TOP_STORY:2:0:" + a(2) + ":MAJOR_STORYLINE", "TOP_STORY:3:0:" + a(3) + ":SUBSCRIBED",
				"MORE_HEADLINES:4:0:" + a(4) + ":SUBSCRIBED", "MORE_HEADLINES:4:1:" + a(5) + ":SUBSCRIBED");
		// 대조 — 지운 것이 없으면 정보 로그가 없다
		assertThat(log.getOut()).doesNotContain("다이제스트 출력에서 지운 것이 있다");
	}

	@Test
	void failsUnreadableAndKeepsTheUsage() {
		DigestGeneration generation = generation(scripted(new AtomicInteger(), request -> "not json"));
		DigestId id = schedule(generation);

		assertThat(generation.run(id, MODEL, candidates(2))).isEqualTo(DigestStatus.FAILED);

		assertThat(row(id)).containsEntry("status", "FAILED")
			.containsEntry("error_code", "UNREADABLE_OUTPUT")
			.containsEntry("error_message", "JSON 이 아니다")
			.containsEntry("input_tokens", 1_200)
			.containsEntry("output_tokens", 340)
			.containsEntry("duration_ms", 2_000L);
		assertThat(count("select count(*) from digest_sections where digest_id = :id", id)).isZero();
		assertThat(candidateRows(id)).hasSize(2);
	}

	@Test
	void failsWithoutCitationWhenEveryAliasIsUnknown() {
		DigestGeneration generation = generation(scripted(new AtomicInteger(), request -> """
				{ "overview": "흐름", "top_stories": [ { "title": "t", "summary": "s", "refs": ["Z9"] } ], "more_headlines": [] }
				"""));
		DigestId id = schedule(generation);

		assertThat(generation.run(id, MODEL, candidates(2))).isEqualTo(DigestStatus.FAILED);

		assertThat(row(id)).containsEntry("error_code", "NO_CITATION")
			.containsEntry("error_message", "검증 뒤 인용이 하나도 남지 않았다");
		assertThat(count("select count(*) from digest_sections where digest_id = :id", id)).isZero();
	}

	@Test
	void failsWhenThereIsNoGenerator() {
		DigestGeneration generation = generation(null);
		DigestId id = schedule(generation);

		assertThat(generation.run(id, MODEL, candidates(2))).isEqualTo(DigestStatus.FAILED);

		Map<String, Object> row = row(id);
		assertThat(row).containsEntry("error_code", "NO_GENERATOR").containsEntry("error_message", "생성기가 없다");
		assertThat(row.get("input_tokens")).isNull();
		assertThat(row.get("duration_ms")).isNull();
	}

	@Test
	void leavesAFinishedDigestAlone() {
		AtomicInteger calls = new AtomicInteger();
		DigestGeneration generation = generation(scripted(calls, request -> "not json"));
		DigestId id = schedule(generation);
		generation.run(id, MODEL, candidates(2));

		assertThat(generation.run(id, MODEL, candidates(3))).isEqualTo(DigestStatus.FAILED);

		assertThat(calls).hasValue(1);
		assertThat(row(id)).containsEntry("candidate_count", 2);
	}

	@Test
	void startsAgainAfterTheGeneratorThrew() {
		DigestGeneration failing = generation(request -> {
			throw new IllegalStateException("공급자 장애");
		});
		DigestId id = schedule(failing);
		assertThatIllegalStateException().isThrownBy(() -> failing.run(id, MODEL, candidates(5)));
		assertThat(row(id)).containsEntry("status", "RUNNING").containsEntry("completed", false);

		assertThat(generation(new FakeDigestGenerator()).run(id, MODEL, candidates(5))).isEqualTo(DigestStatus.READY);

		assertThat(candidateRows(id)).hasSize(5);
		assertThat(count("select count(*) from digest_sections where digest_id = :id", id)).isEqualTo(5);
	}

	@Test
	void anEarlierRunWritesNothingWhenALaterRunFinishedFirst() {
		AtomicReference<DigestGeneration> self = new AtomicReference<>();
		AtomicReference<DigestId> digest = new AtomicReference<>();
		AtomicInteger calls = new AtomicInteger();
		FakeDigestGenerator fake = new FakeDigestGenerator();
		DigestGeneration generation = generation(request -> {
			if (calls.incrementAndGet() == 1) {
				// 앞 실행이 생성기 안에 있는 동안 뒤 실행이 같은 다이제스트를 처음부터 끝낸다 — 뒤 실행은 후보 둘
				assertThat(self.get().run(digest.get(), MODEL, candidates(2))).isEqualTo(DigestStatus.READY);
			}
			return fake.generate(request);
		});
		self.set(generation);
		DigestId id = schedule(generation);
		digest.set(id);

		assertThat(generation.run(id, MODEL, candidates(5))).isEqualTo(DigestStatus.READY);

		assertThat(calls).hasValue(2);
		assertThat(row(id)).containsEntry("candidate_count", 2);
		assertThat(candidateRows(id)).containsExactly("A1:" + a(1), "A2:" + a(2));
		// 뒤 실행의 개요 1 · 주요 뉴스 2 — 앞 실행의 섹션은 더해지지 않는다
		assertThat(count("select count(*) from digest_sections where digest_id = :id", id)).isEqualTo(3);
	}

	@Test
	void anEarlierRunWritesNothingWhenALaterRunRestartedIt() {
		AtomicReference<DigestGeneration> self = new AtomicReference<>();
		AtomicReference<DigestId> digest = new AtomicReference<>();
		AtomicInteger calls = new AtomicInteger();
		FakeDigestGenerator fake = new FakeDigestGenerator();
		DigestGeneration generation = generation(request -> {
			int call = calls.incrementAndGet();
			if (call == 1) {
				// 앞 실행이 생성기 안에 있는 동안 뒤 실행이 같은 다이제스트를 다시 시작하고 생성기 예외로 RUNNING 에 남긴다
				assertThatIllegalStateException()
					.isThrownBy(() -> self.get().run(digest.get(), MODEL, candidates(2)));
			}
			if (call == 2) {
				throw new IllegalStateException("공급자 장애");
			}
			return fake.generate(request);
		});
		self.set(generation);
		DigestId id = schedule(generation);
		digest.set(id);

		assertThat(generation.run(id, MODEL, candidates(5))).isEqualTo(DigestStatus.RUNNING);

		assertThat(calls).hasValue(2);
		assertThat(row(id)).containsEntry("status", "RUNNING").containsEntry("candidate_count", 2);
		assertThat(candidateRows(id)).containsExactly("A1:" + a(1), "A2:" + a(2));
		assertThat(count("select count(*) from digest_sections where digest_id = :id", id)).isZero();
	}

	@Test
	void doesNotCiteAnArticleDeletedBeforeTheEnd(CapturedOutput log) {
		DigestGeneration generation = generation(new FakeDigestGenerator());
		DigestId id = schedule(generation);
		long deleted = this.articleIds.get(1);
		this.jdbc.sql("delete from article_feed_entries where article_id = :id").param("id", deleted).update();
		this.jdbc.sql("delete from articles where id = :id").param("id", deleted).update();

		assertThat(generation.run(id, MODEL, candidates(5))).isEqualTo(DigestStatus.READY);

		assertThat(candidateRows(id)).containsExactly("A1:" + a(1), "A3:" + a(3), "A4:" + a(4), "A5:" + a(5));
		assertThat(itemRows(id)).containsExactly("TOP_STORY:1:0:" + a(1) + ":SUBSCRIBED",
				"TOP_STORY:2:0:" + a(3) + ":SUBSCRIBED", "MORE_HEADLINES:3:0:" + a(4) + ":SUBSCRIBED",
				"MORE_HEADLINES:3:1:" + a(5) + ":SUBSCRIBED");
		assertThat(log.getOut()).contains("다이제스트 출력에서 지운 것이 있다 — 다이제스트 " + id.value()
				+ ", 지운 별칭 1, 버린 항목 1, 상한으로 버린 수 0")
			.doesNotContain("로컬 대체물이 만든");
	}

}
