package net.yozmnews.credential.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.crypto.KeyEncryptionKeys;
import net.yozmnews.credential.domain.key.KeyStatus;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.StoredKeyValidation;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 겹친 등록 — 실제 PostgreSQL 에서, 이 등록이 주인의 행이 없음을 본 뒤 저장하기 직전에 같은 주인의 다른 등록이 먼저 커밋되게 해 고유 제약을
 * 깨뜨리고, 한 번 다시 해 먼저 들어간 행을 지우고 자기 행을 넣는지 본다. 검증은 언제나 성공하는 대역이다. 시험이 트랜잭션을 열지 않고, 시험
 * 뒤에 행을 지운다.
 * <p>
 * 지키는 규칙: credential-R11
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KeyRegistrationRetryTests {

	private static final Instant NOW = Instant.parse("2026-11-14T06:00:00Z");

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
	void anOverlappingRegistrationIsRetriedOnceAndTakesThePlaceOfTheOneThatWentFirst() {
		AtomicInteger saves = new AtomicInteger();
		KeyRegistration registration = new KeyRegistration(alwaysValid(), racingOnTheFirstSave(saves), cipher(),
				new TransactionTemplate(this.transactions), InstantSource.fixed(NOW));

		RegistrationOutcome outcome = registration.register(new UserId(7), Provider.OPENAI,
				SecretValue.of("sk-mine-0123456789abcdMINE"));

		assertThat(outcome).isEqualTo(new RegistrationOutcome.Registered(
				new KeyView(Provider.OPENAI, "MINE", KeyStatus.VALID, NOW, NOW)));
		// 첫 저장은 고유 제약에 걸려 되돌려지고 둘째가 들어갔다 — 먼저 들어간 행(힌트 RACE)은 지워졌다
		assertThat(saves).hasValue(2);
		assertThat(this.jdbc.sql("select key_hint from llm_credentials where user_id = 7").query(String.class).list())
			.containsExactly("MINE");
	}

	/** 첫 저장 직전에 같은 주인의 행을 다른 트랜잭션으로 넣고 커밋하는 저장소 — 나머지는 실제 저장소 그대로다 */
	private LlmKeyRepository racingOnTheFirstSave(AtomicInteger saves) {
		TransactionTemplate other = new TransactionTemplate(this.transactions);
		other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return (LlmKeyRepository) Proxy.newProxyInstance(LlmKeyRepository.class.getClassLoader(),
				new Class<?>[] { LlmKeyRepository.class }, (proxy, method, args) -> {
					if (method.getName().equals("save") && saves.incrementAndGet() == 1) {
						other.executeWithoutResult(status -> this.jdbc.sql("""
								insert into llm_credentials (user_id, provider, ciphertext, encrypted_dek, kek_version,
								  key_hint, status, validated_at, created_at)
								values (7, 'openai', '\\x01'::bytea, '\\x01'::bytea, 1, 'RACE', 'VALID', now(), now())
								""").update());
					}
					try {
						return method.invoke(this.keys, args);
					}
					catch (InvocationTargetException ex) {
						throw ex.getCause();
					}
				});
	}

	private static LlmClientFactory alwaysValid() {
		return new LlmClientFactory() {

			@Override
			public KeyValidationResult validate(Provider provider, SecretValue key) {
				return new KeyValidationResult.Valid();
			}

			@Override
			public StoredKeyValidation validateStored(UserId user, Provider provider) {
				throw new UnsupportedOperationException("등록은 저장한 키의 길을 부르지 않는다");
			}

		};
	}

	private static EnvelopeCipher cipher() {
		byte[] key = new byte[32];
		Arrays.fill(key, (byte) 1);
		return new EnvelopeCipher(KeyEncryptionKeys.of(Map.of(1, key)));
	}

}
