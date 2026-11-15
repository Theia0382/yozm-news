package net.yozmnews.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.credential.CredentialInvalidated;
import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.crypto.KeyEncryptionKeys;
import net.yozmnews.credential.domain.crypto.KeyOwner;
import net.yozmnews.credential.domain.key.KeyHint;
import net.yozmnews.credential.domain.key.KeyStatus;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.credential.domain.key.StoredLlmKey;
import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.StoredKey;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 저장한 키의 공급과 상태 전이 — 실제 PostgreSQL 에 봉인한 키를 두고, 건넨 평문 · 결과마다의 상태와 검증 시각 · 낸 이벤트를 손으로 적은 값과
 * 견준다. 이벤트는 기록하는 발행기로 받고 발행 때 트랜잭션이 열려 있었는지 함께 적는다. 시험이 트랜잭션을 열지 않고, 시험 뒤에 행을 지운다.
 * <p>
 * 지키는 규칙: credential-R2 · credential-R4 · credential-R13 · credential-R14 · credential-R15
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoredKeysTests {

	private static final Instant REGISTERED = Instant.parse("2026-11-14T06:00:00Z");

	/** 마이크로초 아래 자리가 있는 시각 — 무효 시각은 마이크로초로 잘린다 */
	private static final Instant CHECKED = Instant.parse("2026-11-14T07:30:00.123456789Z");

	private static final Instant CHECKED_MICROS = Instant.parse("2026-11-14T07:30:00.123456Z");

	private static final String KEY = "sk-test-0123456789abcdWXYZ";

	private static final KeyValidationResult VALID = new KeyValidationResult.Valid();

	@Autowired
	LlmKeyRepository keys;

	@Autowired
	PlatformTransactionManager transactions;

	@Autowired
	JdbcClient jdbc;

	private final EnvelopeCipher cipher = new EnvelopeCipher(keks());

	private final List<Object> published = new ArrayList<>();

	private final List<Boolean> publishedInTransaction = new ArrayList<>();

	private StoredKeys storedKeys;

	@BeforeEach
	void wire() {
		this.storedKeys = new StoredKeys(this.keys, this.cipher, event -> {
			this.published.add(event);
			this.publishedInTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
		}, new TransactionTemplate(this.transactions), InstantSource.fixed(CHECKED));
	}

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void handsOverTheStoredKeyInPlain() {
		StoredLlmKey stored = store(7, Provider.OPENAI, KEY);

		StoredKey found = this.storedKeys.find(new UserId(7), Provider.OPENAI).orElseThrow();

		assertThat(found.id()).isEqualTo(stored.id());
		assertThat(found.value().reveal()).isEqualTo(KEY);
	}

	@Test
	void findsNothingForAnotherProviderOrUser() {
		store(7, Provider.OPENAI, KEY);

		assertThat(this.storedKeys.find(new UserId(7), Provider.ANTHROPIC)).isEmpty();
		assertThat(this.storedKeys.find(new UserId(8), Provider.OPENAI)).isEmpty();
	}

	@Test
	void doesNotOpenARowMovedToAnotherUser() {
		StoredLlmKey alice = store(7, Provider.OPENAI, KEY);
		StoredLlmKey bob = store(8, Provider.OPENAI, "sk-test-bob-0123456789abcd");
		// 대조 — 옮기기 전에는 B 의 키가 열린다
		assertThat(this.storedKeys.find(new UserId(8), Provider.OPENAI).orElseThrow().value().reveal())
			.isEqualTo("sk-test-bob-0123456789abcd");

		this.jdbc.sql("update llm_credentials set ciphertext = :ciphertext, encrypted_dek = :dek where id = :id")
			.param("ciphertext", alice.sealed().ciphertext())
			.param("dek", alice.sealed().encryptedDek())
			.param("id", bob.id())
			.update();

		assertThatIllegalStateException().isThrownBy(() -> this.storedKeys.find(new UserId(8), Provider.OPENAI))
			.withMessage("저장한 키를 열지 못했다 — AEADBadTagException")
			.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(KEY));
		// A 의 키는 그대로 열린다
		assertThat(this.storedKeys.find(new UserId(7), Provider.OPENAI).orElseThrow().value().reveal()).isEqualTo(KEY);
	}

	@Test
	void anInvalidResultInvalidatesAValidKeyAndPublishesOnceInTheTransaction() {
		StoredLlmKey stored = store(7, Provider.ANTHROPIC, KEY);

		this.storedKeys.checked(stored.id(), rejected(LlmFailureKind.INVALID_KEY));

		StoredLlmKey read = this.keys.findById(stored.id()).orElseThrow();
		assertThat(read.status()).isEqualTo(KeyStatus.INVALID);
		// 검증 시각은 성공에만 고친다
		assertThat(read.validatedAt()).isEqualTo(REGISTERED);
		assertThat(this.published).containsExactly(
				new CredentialInvalidated(new UserId(7), Provider.ANTHROPIC, stored.id(), CHECKED_MICROS));
		assertThat(this.publishedInTransaction).containsExactly(true);
	}

	@Test
	void aDeniedResultAlsoInvalidates() {
		StoredLlmKey stored = store(7, Provider.OPENAI, KEY);

		this.storedKeys.checked(stored.id(), rejected(LlmFailureKind.PERMISSION_DENIED));

		assertThat(this.keys.findById(stored.id()).orElseThrow().status()).isEqualTo(KeyStatus.INVALID);
		assertThat(this.published).hasSize(1);
	}

	@Test
	void repeatedInvalidResultsPublishOnlyTheFirstTransition() {
		StoredLlmKey stored = store(7, Provider.OPENAI, KEY);

		this.storedKeys.checked(stored.id(), rejected(LlmFailureKind.INVALID_KEY));
		this.storedKeys.checked(stored.id(), rejected(LlmFailureKind.INVALID_KEY));
		this.storedKeys.checked(stored.id(), rejected(LlmFailureKind.PERMISSION_DENIED));

		assertThat(this.keys.findById(stored.id()).orElseThrow().status()).isEqualTo(KeyStatus.INVALID);
		assertThat(this.published).hasSize(1);
	}

	@Test
	void aSuccessKeepsAValidKeyValidAndMovesTheValidationTimeWithoutAnEvent() {
		StoredLlmKey stored = store(7, Provider.OPENAI, KEY);

		this.storedKeys.checked(stored.id(), VALID);

		StoredLlmKey read = this.keys.findById(stored.id()).orElseThrow();
		assertThat(read.status()).isEqualTo(KeyStatus.VALID);
		assertThat(read.validatedAt()).isEqualTo(CHECKED_MICROS);
		assertThat(read.createdAt()).isEqualTo(REGISTERED);
		assertThat(this.published).isEmpty();
	}

	@Test
	void aSuccessBringsAnInvalidKeyBackAndALaterInvalidResultPublishesAgain() {
		StoredLlmKey stored = store(7, Provider.OPENAI, KEY);
		this.storedKeys.checked(stored.id(), rejected(LlmFailureKind.INVALID_KEY));

		this.storedKeys.checked(stored.id(), VALID);

		assertThat(this.keys.findById(stored.id()).orElseThrow().status()).isEqualTo(KeyStatus.VALID);
		assertThat(this.published).hasSize(1);

		this.storedKeys.checked(stored.id(), rejected(LlmFailureKind.INVALID_KEY));

		assertThat(this.published).hasSize(2);
	}

	@Test
	void quotaTransientAndUnexpectedResultsChangeNothing() {
		StoredLlmKey stored = store(7, Provider.OPENAI, KEY);

		this.storedKeys.checked(stored.id(), rejected(LlmFailureKind.QUOTA_EXHAUSTED));
		this.storedKeys.checked(stored.id(), rejected(LlmFailureKind.TRANSIENT));
		this.storedKeys.checked(stored.id(), rejected(LlmFailureKind.UNEXPECTED));

		StoredLlmKey read = this.keys.findById(stored.id()).orElseThrow();
		assertThat(read.status()).isEqualTo(KeyStatus.VALID);
		assertThat(read.validatedAt()).isEqualTo(REGISTERED);
		assertThat(this.published).isEmpty();
	}

	@Test
	void aResultForAKeyThatIsGoneOrReplacedIsDropped() {
		StoredLlmKey old = store(7, Provider.OPENAI, KEY);
		// 교체 — 옛 행을 지우고 새 행을 넣는다. 새 키는 다른 ID 다
		this.keys.deleteById(old.id());
		StoredLlmKey replacement = store(7, Provider.OPENAI, "sk-test-new-0123456789abcd");

		this.storedKeys.checked(old.id(), rejected(LlmFailureKind.INVALID_KEY));

		assertThat(replacement.id()).isNotEqualTo(old.id());
		assertThat(this.keys.findById(replacement.id()).orElseThrow().status()).isEqualTo(KeyStatus.VALID);
		assertThat(this.published).isEmpty();
	}

	private StoredLlmKey store(long user, Provider provider, String key) {
		KeyOwner owner = new KeyOwner(new UserId(user), provider);
		SecretValue value = SecretValue.of(key);
		return this.keys
			.save(StoredLlmKey.register(owner, this.cipher.seal(value, owner), KeyHint.of(value), REGISTERED));
	}

	private static KeyValidationResult rejected(LlmFailureKind kind) {
		return new KeyValidationResult.Rejected(kind, "시험 문구");
	}

	private static KeyEncryptionKeys keks() {
		byte[] key = new byte[32];
		Arrays.fill(key, (byte) 1);
		return KeyEncryptionKeys.of(Map.of(1, key));
	}

}
