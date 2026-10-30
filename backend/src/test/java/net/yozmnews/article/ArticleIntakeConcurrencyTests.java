package net.yozmnews.article;

import static net.yozmnews.article.Fetched.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.article.ArticleRows.Entry;
import net.yozmnews.article.application.ArticleIntake;
import net.yozmnews.article.domain.ArticleRepository;
import net.yozmnews.article.domain.IncomingItem;
import net.yozmnews.article.domain.MatchKind;
import net.yozmnews.source.ArticleFetched;
import net.yozmnews.source.SourceId;
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
 * 동시에 들어온 항목의 직렬화 — 테스트 스레드의 트랜잭션이 기사를 넣고 커밋하지 않은 채로 다른 스레드의 처리를 지켜본다.
 * 기다리면 그 기사를 보고 붙고, 기다리지 않으면 커밋 전의 기사를 보지 못해 새 기사를 만든다.
 * <p>
 * 지키는 규칙: article-R9
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "jobrunr.background-job-server.enabled=false")
class ArticleIntakeConcurrencyTests {

	private static final Instant T = Instant.parse("2026-10-20T00:00:00Z");

	private static final String TITLE = "국회 본회의 통과";

	/** 잠금을 기다리는 처리가 끝나지 않았다고 볼 시간 — 기다리지 않는 처리는 이보다 훨씬 빨리 끝난다 */
	private static final long BLOCKED_MILLIS = 500;

	private static final long DONE_SECONDS = 10;

	@Autowired
	ArticleIntake intake;

	@Autowired
	ArticleRepository repository;

	@Autowired
	TransactionTemplate transaction;

	ArticleRows rows;

	ExecutorService executor = Executors.newSingleThreadExecutor();

	@BeforeEach
	void setUp(@Autowired JdbcClient jdbc) {
		this.rows = new ArticleRows(jdbc);
		this.rows.deleteAll();
	}

	@AfterEach
	void tearDown() {
		this.executor.shutdownNow();
	}

	@Test
	void sameSourceWaitsForTheSourceLockAndThenJoins() throws Exception {
		Future<?> waiting = this.transaction.execute((status) -> {
			this.repository.lockSource(new SourceId(1));
			storeUncommitted(item(1, 11, "https://a.example/news/1", null, TITLE, T));
			Future<?> pending = this.executor
				.submit(() -> this.intake.accept(item(1, 12, "https://a.example/section/9", null, TITLE, T)));
			assertBlocked(pending);
			return pending;
		});

		waiting.get(DONE_SECONDS, TimeUnit.SECONDS);
		long id = this.rows.articleIds().getFirst();
		assertThat(this.rows.articleIds()).containsExactly(id);
		assertThat(this.rows.entries()).containsExactly(new Entry(id, 11, "NEW"), new Entry(id, 12, "TITLE"));
	}

	@Test
	void anotherSourceDoesNotWait() throws Exception {
		this.transaction.executeWithoutResult((status) -> {
			this.repository.lockSource(new SourceId(1));
			storeUncommitted(item(1, 11, "https://a.example/news/1", null, TITLE, T));
			Future<?> other = this.executor
				.submit(() -> this.intake.accept(item(2, 21, "https://b.example/news/1", null, TITLE, T)));
			try {
				other.get(DONE_SECONDS, TimeUnit.SECONDS);
			}
			catch (Exception ex) {
				throw new AssertionError("다른 소스의 처리가 소스 잠금을 기다렸다", ex);
			}
		});

		assertThat(this.rows.articleIds()).hasSize(2);
	}

	@Test
	void sameUrlFromAnotherSourceWaitsForTheUniqueKeyAndThenJoins() throws Exception {
		Future<?> waiting = this.transaction.execute((status) -> {
			storeUncommitted(item(1, 11, "https://a.example/news/1", null, TITLE, T));
			Future<?> pending = this.executor
				.submit(() -> this.intake.accept(item(2, 21, "HTTPS://A.example/news/1#c", null, "다른 제목", T)));
			assertBlocked(pending);
			return pending;
		});

		waiting.get(DONE_SECONDS, TimeUnit.SECONDS);
		long id = this.rows.articleIds().getFirst();
		assertThat(this.rows.articleIds()).containsExactly(id);
		assertThat(this.rows.entries()).containsExactly(new Entry(id, 11, "NEW"), new Entry(id, 21, "URL"));
	}

	private void storeUncommitted(ArticleFetched event) {
		IncomingItem item = IncomingItem.from(event);
		this.repository.insertEntryIfAbsent(this.repository.insertIfAbsent(item).orElseThrow(), item, MatchKind.NEW);
	}

	private static void assertBlocked(Future<?> pending) {
		assertThatExceptionOfType(TimeoutException.class)
			.isThrownBy(() -> pending.get(BLOCKED_MILLIS, TimeUnit.MILLISECONDS));
	}

}
