package net.yozmnews.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.credential.CredentialInvalidated;
import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.crypto.KeyOwner;
import net.yozmnews.credential.domain.key.KeyHint;
import net.yozmnews.credential.domain.key.KeyStatus;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.credential.domain.key.StoredLlmKey;
import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결과를 쓰는 길과 교체 · 삭제가 겹칠 때 — 앱 전체를 실제 PostgreSQL 로 띄우고, 한 스레드가 트랜잭션 안에서 키 행을 잠근 동안 다른 스레드의
 * 결과 쓰기가 기다리는지와, 잠근 쪽이 커밋한 뒤 그 결과가 어디에 쓰이는지를 본다. 행은 커밋되고 시험 뒤에 지운다.
 * <p>
 * 지키는 규칙: credential-R11 · credential-R14
 */
@Import({ TestcontainersConfiguration.class, KeyReplacementLockTests.Invalidations.class })
@SpringBootTest
class KeyReplacementLockTests {

	private static final Instant NOW = Instant.parse("2026-11-14T06:00:00Z");

	private static final KeyOwner OWNER = new KeyOwner(new UserId(990_001), Provider.OPENAI);

	private static final KeyValidationResult INVALID = new KeyValidationResult.Rejected(LlmFailureKind.INVALID_KEY,
			"시험 문구");

	@Autowired
	StoredKeys storedKeys;

	@Autowired
	LlmKeyRepository keys;

	@Autowired
	EnvelopeCipher cipher;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Invalidations invalidations;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	/** 행을 잠근 채 머무는 쪽 */
	private final ExecutorService holder = Executors.newSingleThreadExecutor();

	@AfterEach
	void clear() {
		this.executor.shutdownNow();
		this.holder.shutdownNow();
		this.jdbc.sql("delete from llm_credentials where user_id = :id").param("id", OWNER.user().value()).update();
	}

	@Test
	void aResultWaitsForAReplacementAndIsDroppedOnceTheOldKeyIsGone() throws Exception {
		long oldId = store("sk-lock-old-0123456789abAAAA").id();
		CountDownLatch replaced = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		// 교체하는 트랜잭션 — 옛 행을 잠그고 지우고 새 행을 넣은 채 커밋을 미룬다
		Future<Long> replacement = this.holder.submit(() -> this.transaction.execute(status -> {
			this.keys.lockByOwner(OWNER.user().value(), OWNER.provider().key()).ifPresent(this.keys::delete);
			long newId = store("sk-lock-new-0123456789abBBBB").id();
			replaced.countDown();
			await(release);
			return newId;
		}));
		assertThat(replaced.await(10, TimeUnit.SECONDS)).isTrue();

		// 옛 키의 무효 결과를 쓰려는 쪽은 그 행의 잠금에서 기다린다
		Future<?> result = this.executor.submit(() -> this.storedKeys.checked(oldId, INVALID));
		assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> result.get(500, TimeUnit.MILLISECONDS));

		release.countDown();
		long newId = replacement.get(10, TimeUnit.SECONDS);
		result.get(10, TimeUnit.SECONDS);

		// 기다린 뒤 옛 행이 없어 아무것도 쓰지 않았다 — 새 키는 그대로 유효하고, 사라진 옛 키의 무효 이벤트도 나가지 않았다
		assertThat(this.keys.findById(oldId)).isEmpty();
		assertThat(this.keys.findById(newId).orElseThrow().status()).isEqualTo(KeyStatus.VALID);
		assertThat(this.invalidations.of(OWNER.user())).isEmpty();
	}

	@Test
	void aRemovalWaitsForAResultBeingWrittenAndThenRemovesTheRow() throws Exception {
		long id = store("sk-lock-remove-0123456789abAAAA").id();
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		// 결과를 쓰는 트랜잭션이 행을 잠근 채 머문다
		Future<?> writing = this.holder.submit(() -> this.transaction.executeWithoutResult(status -> {
			StoredLlmKey key = this.keys.lockById(id).orElseThrow();
			this.keys.save(key.checked(KeyStatus.INVALID, key.validatedAt()));
			locked.countDown();
			await(release);
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

		// 같은 주인의 교체 · 삭제가 쓰는 잠금 조회는 기다린다
		Future<?> removal = this.executor.submit(() -> this.transaction.executeWithoutResult(
				status -> this.keys.lockByOwner(OWNER.user().value(), OWNER.provider().key()).ifPresent(this.keys::delete)));
		assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> removal.get(500, TimeUnit.MILLISECONDS));

		release.countDown();
		writing.get(10, TimeUnit.SECONDS);
		removal.get(10, TimeUnit.SECONDS);

		assertThat(this.keys.findById(id)).isEmpty();
	}

	private StoredLlmKey store(String key) {
		SecretValue value = SecretValue.of(key);
		return this.keys.save(StoredLlmKey.register(OWNER, this.cipher.seal(value, OWNER), KeyHint.of(value), NOW));
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("놓아 주기를 기다리다 시간이 지났다");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	/** 낸 무효 이벤트를 적는 빈 — 발행한 스레드에서 바로 받는다. 이 시험의 컨텍스트에만 등록한다 */
	static class Invalidations {

		private final List<CredentialInvalidated> events = new CopyOnWriteArrayList<>();

		@EventListener
		void on(CredentialInvalidated event) {
			this.events.add(event);
		}

		List<CredentialInvalidated> of(UserId user) {
			return this.events.stream().filter(event -> event.user().equals(user)).toList();
		}

	}

}
