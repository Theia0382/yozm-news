package net.yozmnews.credential.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.crypto.KeyEncryptionKeys;
import net.yozmnews.credential.domain.crypto.KeyOwner;
import net.yozmnews.credential.domain.key.KeyHint;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.credential.domain.key.StoredLlmKey;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재암호화 — 실제 PostgreSQL 에 KEK 버전 1 로 봉인한 키를 두고, 버전 1 · 2 의 설정으로 한 회차를 돈 뒤 행과 로그를 본다. 시험이 트랜잭션을
 * 열지 않아 키마다의 트랜잭션이 실제로 커밋되고, 시험 뒤에 행을 지운다.
 * <p>
 * 지키는 규칙: credential-R7
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(OutputCaptureExtension.class)
class DekRewrapTests {

	private static final Instant NOW = Instant.parse("2026-11-14T06:00:00Z");

	private static final String KEY = "sk-test-0123456789abcdWXYZ";

	@Autowired
	LlmKeyRepository keys;

	@Autowired
	PlatformTransactionManager transactions;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void rewrapsKeysOffTheActiveVersionAndLeavesTheCiphertext(CapturedOutput output) {
		EnvelopeCipher old = new EnvelopeCipher(keks(1));
		StoredLlmKey first = store(old, 7);
		StoredLlmKey second = store(old, 8);
		EnvelopeCipher both = new EnvelopeCipher(keks(1, 2));
		StoredLlmKey current = store(both, 9);

		RewrapResult result = rewrap(both).run();

		assertThat(result).isEqualTo(new RewrapResult(2, 0, 0));
		assertThat(output.getOut()).contains("KEK 재암호화 — 다시 감쌈 2 · 실패 0 · 남음 0");
		StoredLlmKey rewrapped = this.keys.findById(first.id()).orElseThrow();
		assertThat(rewrapped.kekVersion()).isEqualTo(2);
		assertThat(rewrapped.sealed().ciphertext()).isEqualTo(first.sealed().ciphertext());
		assertThat(rewrapped.sealed().encryptedDek()).isNotEqualTo(first.sealed().encryptedDek());
		assertThat(rewrapped.hint()).isEqualTo("WXYZ");
		assertThat(rewrapped.validatedAt()).isEqualTo(NOW);
		// 옛 버전을 지운 설정으로도 열린다
		EnvelopeCipher onlyNew = new EnvelopeCipher(keks(2));
		assertThat(onlyNew.open(rewrapped.sealed(), rewrapped.owner()).reveal()).isEqualTo(KEY);
		assertThat(onlyNew.open(this.keys.findById(second.id()).orElseThrow().sealed(), second.owner()).reveal())
			.isEqualTo(KEY);
		// 이미 활성 버전인 키는 건드리지 않는다
		assertThat(this.keys.findById(current.id()).orElseThrow().sealed().encryptedDek())
			.isEqualTo(current.sealed().encryptedDek());
	}

	@Test
	void doesNothingAndLogsNothingWhenEveryKeyIsOnTheActiveVersion(CapturedOutput output) {
		EnvelopeCipher both = new EnvelopeCipher(keks(1, 2));
		StoredLlmKey current = store(both, 7);

		assertThat(rewrap(both).run()).isEqualTo(new RewrapResult(0, 0, 0));
		assertThat(rewrap(both).run()).isEqualTo(new RewrapResult(0, 0, 0));

		assertThat(output.getOut()).doesNotContain("KEK 재암호화");
		assertThat(this.keys.findById(current.id()).orElseThrow().sealed().encryptedDek())
			.isEqualTo(current.sealed().encryptedDek());
	}

	@Test
	void givesTheSameResultWhenRunAgain() {
		store(new EnvelopeCipher(keks(1)), 7);
		EnvelopeCipher both = new EnvelopeCipher(keks(1, 2));

		assertThat(rewrap(both).run()).isEqualTo(new RewrapResult(1, 0, 0));
		byte[] afterFirst = this.keys.findByUserIdAndProvider(7, "openai").orElseThrow().sealed().encryptedDek();
		assertThat(rewrap(both).run()).isEqualTo(new RewrapResult(0, 0, 0));

		assertThat(this.keys.findByUserIdAndProvider(7, "openai").orElseThrow().sealed().encryptedDek())
			.isEqualTo(afterFirst);
	}

	@Test
	void skipsAKeyItCannotOpenAndGoesOnToTheNext(CapturedOutput output) {
		EnvelopeCipher old = new EnvelopeCipher(keks(1));
		StoredLlmKey broken = store(old, 7);
		StoredLlmKey sound = store(old, 8);
		// 감싼 DEK 를 다른 주인의 것으로 바꿔 열리지 않게 한다
		this.jdbc.sql("update llm_credentials set encrypted_dek = :dek where id = :id")
			.param("dek", sound.sealed().encryptedDek())
			.param("id", broken.id())
			.update();

		RewrapResult result = rewrap(new EnvelopeCipher(keks(1, 2))).run();

		assertThat(result).isEqualTo(new RewrapResult(1, 1, 1));
		assertThat(output.getOut()).contains("KEK 재암호화 — 키 " + broken.id() + " 를 다시 감싸지 못했다: IllegalStateException")
			.contains("KEK 재암호화 — 다시 감쌈 1 · 실패 1 · 남음 1")
			.doesNotContain(KEY);
		assertThat(this.keys.findById(broken.id()).orElseThrow().kekVersion()).isEqualTo(1);
		assertThat(this.keys.findById(sound.id()).orElseThrow().kekVersion()).isEqualTo(2);
	}

	@Test
	void skipsAKeyDeletedAfterItWasListed(CapturedOutput output) {
		EnvelopeCipher old = new EnvelopeCipher(keks(1));
		StoredLlmKey gone = store(old, 7);
		StoredLlmKey kept = store(old, 8);
		DekRewrap rewrap = new DekRewrap(deletingAfterListing(gone.id()), new EnvelopeCipher(keks(1, 2)),
				new TransactionTemplate(this.transactions));

		assertThat(rewrap.run()).isEqualTo(new RewrapResult(1, 0, 0));

		assertThat(output.getOut()).contains("KEK 재암호화 — 다시 감쌈 1 · 실패 0 · 남음 0").doesNotContain("다시 감싸지 못했다");
		assertThat(this.keys.findById(gone.id())).isEmpty();
		assertThat(this.keys.findById(kept.id()).orElseThrow().kekVersion()).isEqualTo(2);
	}

	@Test
	void rewrapsAtMostABatchPerRun() {
		assertThat(DekRewrap.BATCH).isEqualTo(100);
		EnvelopeCipher old = new EnvelopeCipher(keks(1));
		for (int user = 1; user <= 101; user++) {
			store(old, user);
		}

		assertThat(rewrap(new EnvelopeCipher(keks(1, 2))).run()).isEqualTo(new RewrapResult(100, 0, 1));
	}

	private DekRewrap rewrap(EnvelopeCipher cipher) {
		return new DekRewrap(this.keys, cipher, new TransactionTemplate(this.transactions));
	}

	/** 대상을 찾은 직후 그 가운데 한 키의 행을 지우는 저장소 — 회차 도중의 삭제 · 교체를 흉내 낸다. 나머지는 실제 저장소 그대로다 */
	private LlmKeyRepository deletingAfterListing(long id) {
		return (LlmKeyRepository) Proxy.newProxyInstance(LlmKeyRepository.class.getClassLoader(),
				new Class<?>[] { LlmKeyRepository.class }, (proxy, method, args) -> {
					try {
						Object result = method.invoke(this.keys, args);
						if (method.getName().equals("idsNotOnVersion")) {
							this.jdbc.sql("delete from llm_credentials where id = :id").param("id", id).update();
						}
						return result;
					}
					catch (InvocationTargetException ex) {
						throw ex.getCause();
					}
				});
	}

	private StoredLlmKey store(EnvelopeCipher cipher, long user) {
		KeyOwner owner = new KeyOwner(new UserId(user), Provider.OPENAI);
		SecretValue key = SecretValue.of(KEY);
		return this.keys.save(StoredLlmKey.register(owner, cipher.seal(key, owner), KeyHint.of(key), NOW));
	}

	/** 버전마다 그 번호로 채운 32바이트 KEK */
	private static KeyEncryptionKeys keks(int... versions) {
		Map<Integer, byte[]> keys = new HashMap<>();
		for (int version : versions) {
			byte[] key = new byte[32];
			Arrays.fill(key, (byte) version);
			keys.put(version, key);
		}
		return KeyEncryptionKeys.of(keys);
	}

}
