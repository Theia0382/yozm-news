package net.yozmnews.digest.application;

import static net.yozmnews.Latches.awaitQuietly;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.article.ArticlesRemoved;
import net.yozmnews.digest.adapter.out.fake.FakeDigestGenerator;
import net.yozmnews.digest.domain.candidate.CandidateSet;
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
import net.yozmnews.digest.domain.removal.DigestContentCleanup;
import net.yozmnews.digest.domain.removal.SectionCitations;
import net.yozmnews.event.EventArticles;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;
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
 * 지운 기사 따르기 — 후보 · 인용 행과 인용이 다 지워진 섹션, 인용 섹션이 남지 않는 다이제스트의 개요를 지우고 다이제스트 행은 남긴다. 생성과
 * 엇갈린 지우기도 지운 기사를 남기지 않는다.
 * <p>
 * 지키는 규칙: digest-R9 · digest-R10
 * <p>
 * 시험을 트랜잭션으로 감싸지 않는다 — 엇갈림(생성과 지우기, 나뉘어 발행된 지우기 둘)은 두 스레드의 커밋 순서로 본다. 리스너는 직접 부른다(사건 묶기의 지운 기사 시험과 같은
 * 방식). 다이제스트는 로컬 대체물로 만든다 — 개요, 주요 뉴스 A1 · A2 · A3, 추가 헤드라인 A4 · A5. 기사 행은 SQL 로 넣는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class DigestArticleRemovalTests {

	private static final LlmModel MODEL = new LlmModel(Provider.OPENAI, "gpt-5.5-mini", 100_000, true);

	private static final InstantSource CLOCK = InstantSource.fixed(Instant.parse("2026-11-15T22:45:00Z"));

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
	DigestArticleRemoval removal;

	@Autowired
	DigestContentCleanup content;

	@Autowired
	JdbcClient jdbc;

	private final List<Long> articleIds = new ArrayList<>();

	@BeforeEach
	void fiveArticles() {
		clear();
		for (int index = 1; index <= 5; index++) {
			this.articleIds.add(EventArticles.insert(this.jdbc, "지운 기사 시험 기사 " + index, null));
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

	private DigestGeneration generation(@Nullable DigestGenerator generator, DigestCandidateRepository repository) {
		return new DigestGeneration(this.digests, this.sections, repository, this.articles, () -> generator,
				this.transaction, CLOCK);
	}

	private DigestId ready(int... indexes) {
		DigestGeneration generation = generation(new FakeDigestGenerator(), this.candidates);
		DigestId id = schedule(generation);
		assertThat(generation.run(id, MODEL, candidates(indexes))).isEqualTo(DigestStatus.READY);
		return id;
	}

	private DigestId schedule(DigestGeneration generation) {
		return generation.schedule(new UserId(42), LocalDate.parse("2026-11-16"),
				new DigestPeriod(Instant.parse("2026-11-14T22:00:00Z"), Instant.parse("2026-11-15T22:00:00Z")),
				DigestTrigger.SCHEDULED);
	}

	private List<DigestCandidate> candidates(int... indexes) {
		List<DigestCandidate> list = new ArrayList<>();
		for (int index : indexes) {
			list.add(new DigestCandidate(new ArticleId(this.articleIds.get(index - 1)), SelectionReason.SUBSCRIBED));
		}
		return list;
	}

	private ArticleId article(int index) {
		return new ArticleId(this.articleIds.get(index - 1));
	}

	private String a(int index) {
		return String.valueOf(this.articleIds.get(index - 1));
	}

	private void remove(int... indexes) {
		List<ArticleId> removed = new ArrayList<>();
		for (int index : indexes) {
			removed.add(article(index));
		}
		new DigestArticleRemovalListener(this.removal).on(new ArticlesRemoved(removed));
	}

	private List<String> sectionRows(DigestId id) {
		return this.jdbc.sql("""
				select s.kind || ':' || s.position || coalesce(':' || string_agg(i.article_id::text, ',' order by i.position), '')
				from digest_sections s left join digest_items i on i.digest_section_id = s.id
				where s.digest_id = :id group by s.id, s.kind, s.position order by s.position
				""").param("id", id.value()).query(String.class).list();
	}

	private List<String> candidateRows(DigestId id) {
		return this.jdbc.sql("select alias || ':' || article_id from digest_candidates where digest_id = :id order by alias")
			.param("id", id.value())
			.query(String.class)
			.list();
	}

	private String status(DigestId id) {
		return this.jdbc.sql("select status from digests where id = :id").param("id", id.value()).query(String.class).single();
	}

	@Test
	void removesTheCitationsAndTheStoryLeftWithoutCitations() {
		DigestId id = ready(1, 2, 3, 4, 5);

		remove(2);

		assertThat(sectionRows(id)).containsExactly("OVERVIEW:0", "TOP_STORY:1:" + a(1), "TOP_STORY:3:" + a(3),
				"MORE_HEADLINES:4:" + a(4) + "," + a(5));
		assertThat(candidateRows(id)).containsExactly("A1:" + a(1), "A3:" + a(3), "A4:" + a(4), "A5:" + a(5));
		assertThat(status(id)).isEqualTo("READY");
	}

	@Test
	void keepsTheHeadlinesWhileOneRemains() {
		DigestId id = ready(1, 2, 3, 4, 5);

		remove(4);

		assertThat(sectionRows(id)).containsExactly("OVERVIEW:0", "TOP_STORY:1:" + a(1), "TOP_STORY:2:" + a(2),
				"TOP_STORY:3:" + a(3), "MORE_HEADLINES:4:" + a(5));
	}

	@Test
	void removesTheOverviewWhenNoCitedSectionRemainsButKeepsTheDigest() {
		DigestId id = ready(1, 2, 3, 4, 5);

		remove(1, 2, 3, 4, 5);

		assertThat(sectionRows(id)).isEmpty();
		assertThat(candidateRows(id)).isEmpty();
		assertThat(status(id)).isEqualTo("READY");
	}

	@Test
	void leavesADigestThatDoesNotCiteTheRemovedArticle() {
		DigestId citing = ready(1, 2);
		DigestId other = ready(5);

		remove(1, 2);

		assertThat(sectionRows(citing)).isEmpty();
		assertThat(sectionRows(other)).containsExactly("OVERVIEW:0", "TOP_STORY:1:" + a(5));
		assertThat(candidateRows(other)).containsExactly("A1:" + a(5));
	}

	@Test
	void givesTheSameResultForTheSameRemovalTwice() {
		DigestId id = ready(1, 2, 3, 4, 5);

		remove(2);
		remove(2);

		assertThat(sectionRows(id)).containsExactly("OVERVIEW:0", "TOP_STORY:1:" + a(1), "TOP_STORY:3:" + a(3),
				"MORE_HEADLINES:4:" + a(4) + "," + a(5));
	}

	@Test
	void aRemovalWaitsForAnInFlightGenerationAndThenTakesItsCitationOut() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		DigestGeneration generation = generation(new FakeDigestGenerator(),
				new Pausing(this.candidates, locked, resume));
		DigestId id = schedule(generation);
		try {
			CompletableFuture<DigestStatus> running = CompletableFuture
				.supplyAsync(() -> generation.run(id, MODEL, candidates(1, 2, 3, 4, 5)));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			// 트랜잭션 ② 가 후보 행을 공유 잠금한 채 커밋 전이다 — 지우기는 후보 행에서 기다린다
			CompletableFuture<Void> removing = CompletableFuture.runAsync(() -> remove(1));
			Thread.sleep(500);
			assertThat(removing).as("생성이 후보 행을 잡은 동안 지우기가 기다린다").isNotDone();
			resume.countDown();
			assertThat(running.get(10, TimeUnit.SECONDS)).isEqualTo(DigestStatus.READY);
			removing.get(10, TimeUnit.SECONDS);
		}
		finally {
			resume.countDown();
		}

		assertThat(sectionRows(id)).containsExactly("OVERVIEW:0", "TOP_STORY:2:" + a(2), "TOP_STORY:3:" + a(3),
				"MORE_HEADLINES:4:" + a(4) + "," + a(5));
		assertThat(candidateRows(id)).containsExactly("A2:" + a(2), "A3:" + a(3), "A4:" + a(4), "A5:" + a(5));
	}

	@Test
	void aGenerationFinishedAfterARemovalDoesNotCiteTheRemovedArticle() {
		FakeDigestGenerator fake = new FakeDigestGenerator();
		DigestGeneration generation = generation(request -> {
			remove(1);
			return fake.generate(request);
		}, this.candidates);
		DigestId id = schedule(generation);

		assertThat(generation.run(id, MODEL, candidates(1, 2, 3, 4, 5))).isEqualTo(DigestStatus.READY);

		assertThat(sectionRows(id)).containsExactly("OVERVIEW:0", "TOP_STORY:1:" + a(2), "TOP_STORY:2:" + a(3),
				"MORE_HEADLINES:3:" + a(4) + "," + a(5));
		assertThat(candidateRows(id)).containsExactly("A2:" + a(2), "A3:" + a(3), "A4:" + a(4), "A5:" + a(5));
	}

	@Test
	void twoRemovalsOfTheSameDigestAtOnceStillDeleteTheSectionTheyEmptyTogether() throws Exception {
		DigestId id = ready(1, 2, 3, 4, 5);
		CountDownLatch read = new CountDownLatch(1);
		CountDownLatch resume = new CountDownLatch(1);
		DigestArticleRemoval pausing = new DigestArticleRemoval(this.candidates,
				new PausingContent(this.content, read, resume));
		try {
			// 나뉘어 발행된 지운 기사 이벤트 둘 — 헤드라인 묶음의 A4 와 A5 를 하나씩 지운다
			// 시험이 만든 유스케이스는 프록시가 아니라 트랜잭션을 시험이 연다
			CompletableFuture<Void> first = CompletableFuture.runAsync(
					() -> this.transaction.executeWithoutResult(status -> pausing.apply(List.of(article(4)))));
			assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
			CompletableFuture<Void> second = CompletableFuture.runAsync(() -> this.removal.apply(List.of(article(5))));
			Thread.sleep(500);
			assertThat(second).as("첫 지우기가 다이제스트 행을 잡은 동안 둘째가 기다린다").isNotDone();
			resume.countDown();
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);
		}
		finally {
			resume.countDown();
		}

		assertThat(sectionRows(id)).containsExactly("OVERVIEW:0", "TOP_STORY:1:" + a(1), "TOP_STORY:2:" + a(2),
				"TOP_STORY:3:" + a(3));
	}

	/** 섹션을 읽은 뒤(커밋 전) 신호를 보내고 풀릴 때까지 기다린다 */
	private record PausingContent(DigestContentCleanup delegate, CountDownLatch read, CountDownLatch resume)
			implements DigestContentCleanup {

		@Override
		public List<Long> lockDigestsReferencing(Collection<ArticleId> removed) {
			return this.delegate.lockDigestsReferencing(removed);
		}

		@Override
		public List<SectionCitations> sectionsOfDigestsCiting(Collection<ArticleId> removed) {
			List<SectionCitations> sections = this.delegate.sectionsOfDigestsCiting(removed);
			this.read.countDown();
			awaitQuietly(this.resume);
			return sections;
		}

		@Override
		public void deleteItems(Collection<ArticleId> removed) {
			this.delegate.deleteItems(removed);
		}

		@Override
		public void deleteSections(Collection<Long> sectionIds) {
			this.delegate.deleteSections(sectionIds);
		}

	}

	/** 후보 행을 공유 잠금한 뒤(커밋 전) 신호를 보내고 풀릴 때까지 기다린다 */
	private record Pausing(DigestCandidateRepository delegate, CountDownLatch locked, CountDownLatch resume)
			implements DigestCandidateRepository {

		@Override
		public void replace(DigestId digest, CandidateSet candidates) {
			this.delegate.replace(digest, candidates);
		}

		@Override
		public Set<ArticleId> lockArticles(DigestId digest) {
			Set<ArticleId> kept = this.delegate.lockArticles(digest);
			this.locked.countDown();
			awaitQuietly(this.resume);
			return kept;
		}

		@Override
		public void delete(DigestId digest, Collection<ArticleId> articles) {
			this.delegate.delete(digest, articles);
		}

		@Override
		public void deleteByArticles(Collection<ArticleId> articles) {
			this.delegate.deleteByArticles(articles);
		}

		@Override
		public int deleteOfDigestsCreatedBefore(Instant cutoff) {
			return this.delegate.deleteOfDigestsCreatedBefore(cutoff);
		}

	}

}
