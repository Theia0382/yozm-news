package net.yozmnews.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.yozmnews.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 선호 버전 울타리 — 공유 잠금을 쥔 트랜잭션이 열려 있는 동안 배타 지나기는 끝나지 않고, 그 트랜잭션이 끝나면 끝난다. 연결 둘을 쓰므로 시험이
 * 트랜잭션을 열지 않는다.
 * <p>
 * 지키는 규칙: user-R43 · user-R47
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JdbcPreferenceVersionFence.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcPreferenceVersionFenceTests {

	@Autowired
	JdbcPreferenceVersionFence fence;

	@Autowired
	PlatformTransactionManager transactions;

	@Test
	void passingWaitsForASharedHolderAndEndsWhenItCommits() throws Exception {
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> new TransactionTemplate(this.transactions)
			.executeWithoutResult(status -> {
				this.fence.enterShared();
				entered.countDown();
				await(release);
			}));
		assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

		CompletableFuture<Void> passing = CompletableFuture.runAsync(this.fence::passExclusive);

		org.assertj.core.api.Assertions.assertThatExceptionOfType(TimeoutException.class)
			.isThrownBy(() -> passing.get(500, TimeUnit.MILLISECONDS));
		release.countDown();
		holder.get(10, TimeUnit.SECONDS);
		passing.get(10, TimeUnit.SECONDS);
		assertThat(passing).isDone();
	}

	@Test
	void passingWithoutAHolderEndsAtOnceAndAnotherSharedHolderIsNotBlocked() throws Exception {
		CompletableFuture.runAsync(this.fence::passExclusive).get(5, TimeUnit.SECONDS);

		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> first = CompletableFuture.runAsync(() -> new TransactionTemplate(this.transactions)
			.executeWithoutResult(status -> {
				this.fence.enterShared();
				entered.countDown();
				await(release);
			}));
		assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
		// 공유끼리는 서로 막지 않는다 — 가입 둘이 동시에 기본값을 쓴다
		CompletableFuture.runAsync(() -> new TransactionTemplate(this.transactions)
			.executeWithoutResult(status -> this.fence.enterShared())).get(5, TimeUnit.SECONDS);
		release.countDown();
		first.get(10, TimeUnit.SECONDS);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

}
