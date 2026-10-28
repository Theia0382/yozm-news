package net.yozmnews.source.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.source.SourceId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소스의 다음 요청 가능 시각 — 처음에는 비어 있고, 쓴 값을 소스마다 따로 돌려준다. 읽기는 그 소스의 행을 잠가, 같은 소스의 예약이
 * 한 줄로 선다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcSourceFetchSlotRepository.class })
class JdbcSourceFetchSlotRepositoryTests {

	@Autowired
	JdbcClient jdbc;

	@Autowired
	JdbcSourceFetchSlotRepository repository;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void aSourceThatNeverRequestedHasNoNextRequestTime() {
		assertThat(this.repository.lockNextFetchAt(source("alpha"))).isNull();
		assertThat(this.repository.lockNextFetchAt(source("beta"))).isNull();
	}

	@Test
	void keepsTheNextRequestTimeOfEachSourceApart() {
		SourceId alpha = source("alpha");
		SourceId beta = source("beta");
		this.repository.lockNextFetchAt(alpha);

		this.repository.saveNextFetchAt(alpha, Instant.parse("2026-10-28T10:00:10.123456Z"));

		assertThat(this.repository.lockNextFetchAt(alpha)).isEqualTo(Instant.parse("2026-10-28T10:00:10.123456Z"));
		assertThat(this.repository.lockNextFetchAt(beta)).isNull();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void readingHoldsTheRowOfTheSourceUntilTheReservationCommits() throws InterruptedException {
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		SourceId source = source("locking");
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			transaction.executeWithoutResult(status -> this.repository.lockNextFetchAt(source));
			CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
				this.repository.lockNextFetchAt(source);
				locked.countDown();
				awaitQuietly(release);
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			// 55P03 lock_not_available — 앞 트랜잭션이 행을 잡고 있어 기다리다 시간이 다 됐다
			assertThatExceptionOfType(DataAccessException.class)
				.isThrownBy(() -> transaction.executeWithoutResult(status -> {
					this.jdbc.sql("set local lock_timeout = '200ms'").update();
					this.repository.lockNextFetchAt(source);
				}))
				.withMessageContaining("55P03");

			release.countDown();
			holder.join();
		}
		finally {
			release.countDown();
			this.jdbc.sql("delete from source_fetch_slots where source_id = :source").param("source", source.value()).update();
			this.jdbc.sql("delete from sources where id = :source").param("source", source.value()).update();
		}
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private SourceId source(String presetKey) {
		return new SourceId(this.jdbc.sql("""
				insert into sources (preset_key, enabled, version, created_at) values (:key, false, 0, now()) returning id
				""").param("key", presetKey).query(Long.class).single());
	}

}
