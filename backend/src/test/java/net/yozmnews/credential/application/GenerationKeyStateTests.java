package net.yozmnews.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.account.domain.account.Account;
import net.yozmnews.account.domain.account.AccountRepository;
import net.yozmnews.account.domain.account.AccountRole;
import net.yozmnews.account.domain.account.DisplayName;
import net.yozmnews.account.domain.account.EmailAddress;
import net.yozmnews.credential.CredentialInvalidated;
import net.yozmnews.credential.domain.crypto.EnvelopeCipher;
import net.yozmnews.credential.domain.crypto.KeyOwner;
import net.yozmnews.credential.domain.key.KeyHint;
import net.yozmnews.credential.domain.key.KeyStatus;
import net.yozmnews.credential.domain.key.LlmKeyRepository;
import net.yozmnews.credential.domain.key.StoredLlmKey;
import net.yozmnews.llm.GenerationRequest;
import net.yozmnews.llm.GenerationResult;
import net.yozmnews.llm.GenerationStop;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.llm.ScriptedProviderClients;
import net.yozmnews.llm.StoredGeneration;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 생성 호출의 결과가 저장한 키의 상태가 되는 길 — 앱 전체를 실제 PostgreSQL 로 띄워, 관문의 생성 호출이 공급 인터페이스로 키를 받고 결과를
 * 그 키에 알려 `credential` 의 전이가 일어나는지 본다. 공급자 포트만 키마다 답을 정하는 대역이다. 일시 장애는 마감 초과로 두어 다시 보내지
 * 않는다(다시 보내기는 관문 시험이 본다). 키의 주인은 실제 계정이다 — 무효 이벤트를 받는 알림 리스너가 그 계정에 알림을 남기고 발행을
 * 끝낸다. 시험이 트랜잭션을 열지 않고, 시험 뒤에 키 행을 지운다.
 * <p>
 * 지키는 규칙: llm-R19 · credential-R14 · credential-R15
 */
@Import({ TestcontainersConfiguration.class, ScriptedProviderClients.Config.class })
@SpringBootTest
@RecordApplicationEvents
class GenerationKeyStateTests {

	private static final Instant REGISTERED = Instant.parse("2026-11-14T06:00:00Z");

	private static final LlmModel MODEL = new LlmModel(Provider.ANTHROPIC, "claude-x", 32_000, true);

	@Autowired
	LlmClientFactory gateway;

	@Autowired
	ScriptedProviderClients provider;

	@Autowired
	LlmKeyRepository keys;

	@Autowired
	EnvelopeCipher cipher;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@Autowired
	AccountRepository accounts;

	@AfterEach
	void clear() {
		this.jdbc.sql("delete from llm_credentials").update();
	}

	@Test
	void anUnauthorizedGenerationMakesTheKeyInvalidOnce() {
		UserId user = account("gen-key-unauthorized@example.com");
		StoredLlmKey key = store(user, "sk-gen-unauthorized-0123456789");
		this.provider.generate("sk-gen-unauthorized-0123456789", failed(LlmFailureKind.INVALID_KEY));

		StoredGeneration result = this.gateway.generateStored(user, request());

		assertThat(result).isInstanceOfSatisfying(StoredGeneration.Done.class,
				done -> assertThat(done.result()).isInstanceOfSatisfying(GenerationResult.Failed.class,
						failed -> assertThat(failed.kind()).isEqualTo(LlmFailureKind.INVALID_KEY)));
		assertThat(status(key)).isEqualTo(KeyStatus.INVALID);
		assertThat(this.events.stream(CredentialInvalidated.class)).singleElement().satisfies(event -> {
			assertThat(event.user()).isEqualTo(user);
			assertThat(event.provider()).isEqualTo(Provider.ANTHROPIC);
			assertThat(event.keyId()).isEqualTo(key.id());
		});
		awaitPublicationsFinished(key);
	}

	@Test
	void aForbiddenGenerationAlsoMakesTheKeyInvalid() {
		UserId user = account("gen-key-forbidden@example.com");
		StoredLlmKey key = store(user, "sk-gen-forbidden-0123456789");
		this.provider.generate("sk-gen-forbidden-0123456789", failed(LlmFailureKind.PERMISSION_DENIED));

		this.gateway.generateStored(user, request());

		assertThat(status(key)).isEqualTo(KeyStatus.INVALID);
		assertThat(this.events.stream(CredentialInvalidated.class)).hasSize(1);
		awaitPublicationsFinished(key);
	}

	@Test
	void otherFailuresLeaveTheKeyAsItWas() {
		StoredLlmKey quota = store(new UserId(7), "sk-gen-quota-0123456789");
		StoredLlmKey late = store(new UserId(8), "sk-gen-late-0123456789");
		StoredLlmKey odd = store(new UserId(9), "sk-gen-odd-0123456789");
		this.provider.generate("sk-gen-quota-0123456789", failed(LlmFailureKind.QUOTA_EXHAUSTED));
		this.provider.generate("sk-gen-late-0123456789",
				new GenerationOutcome.Failed(LlmFailureKind.TRANSIENT, "시험 문구", null, true));
		this.provider.generate("sk-gen-odd-0123456789", failed(LlmFailureKind.UNEXPECTED));

		this.gateway.generateStored(new UserId(7), request());
		this.gateway.generateStored(new UserId(8), request());
		this.gateway.generateStored(new UserId(9), request());

		assertThat(status(quota)).isEqualTo(KeyStatus.VALID);
		assertThat(status(late)).isEqualTo(KeyStatus.VALID);
		assertThat(status(odd)).isEqualTo(KeyStatus.VALID);
		assertThat(validatedAt(quota)).isEqualTo(REGISTERED);
		assertThat(validatedAt(late)).isEqualTo(REGISTERED);
		assertThat(validatedAt(odd)).isEqualTo(REGISTERED);
		assertThat(this.events.stream(CredentialInvalidated.class)).isEmpty();
	}

	@Test
	void aSuccessfulGenerationMovesTheValidationTimeOfTheKey() {
		StoredLlmKey key = store(new UserId(7), "sk-gen-success-0123456789");
		this.provider.generate("sk-gen-success-0123456789",
				new GenerationOutcome.Answered("{}", 3, 4, GenerationStop.COMPLETE));
		Instant before = Instant.now();

		StoredGeneration result = this.gateway.generateStored(new UserId(7), request());

		assertThat(result).isInstanceOfSatisfying(StoredGeneration.Done.class,
				done -> assertThat(done.result()).isInstanceOf(GenerationResult.Generated.class));
		assertThat(status(key)).isEqualTo(KeyStatus.VALID);
		// 대조 — 등록 시각에서 이번 생성의 시각으로 옮겨 갔다
		assertThat(validatedAt(key)).isAfterOrEqualTo(before.minusSeconds(1)).isAfter(REGISTERED);
		assertThat(this.events.stream(CredentialInvalidated.class)).isEmpty();
	}

	@Test
	void aUserWithoutAStoredKeyIsNotSent() {
		int before = this.provider.generated().size();

		assertThat(this.gateway.generateStored(new UserId(77), request())).isEqualTo(new StoredGeneration.Missing());
		assertThat(this.provider.generated()).hasSize(before);
	}

	/** 알림 리스너가 그 키의 무효 이벤트를 처리해 발행을 끝냈다 — 끝낸 발행 행은 지워진다 */
	private void awaitPublicationsFinished(StoredLlmKey key) {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(this.jdbc.sql("""
				select count(*) from event_publication
				where event_type = :type and position(:keyId in serialized_event) > 0
				""")
			.param("type", CredentialInvalidated.class.getName())
			.param("keyId", "\"keyId\":" + key.id() + ",")
			.query(Long.class)
			.single()).isZero());
	}

	private UserId account(String email) {
		return this.accounts.save(Account.create(EmailAddress.parse(email).orElseThrow(), new DisplayName(email),
				AccountRole.USER, REGISTERED))
			.id();
	}

	private StoredLlmKey store(UserId user, String key) {
		KeyOwner owner = new KeyOwner(user, Provider.ANTHROPIC);
		SecretValue value = SecretValue.of(key);
		return this.keys
			.save(StoredLlmKey.register(owner, this.cipher.seal(value, owner), KeyHint.of(value), REGISTERED));
	}

	private KeyStatus status(StoredLlmKey key) {
		return KeyStatus.valueOf(this.jdbc.sql("select status from llm_credentials where id = ?")
			.param(key.id())
			.query(String.class)
			.single());
	}

	private Instant validatedAt(StoredLlmKey key) {
		return this.jdbc.sql("select validated_at from llm_credentials where id = ?")
			.param(key.id())
			.query(Instant.class)
			.single();
	}

	private static GenerationRequest request() {
		return new GenerationRequest(MODEL, "지시", "사용자", "{\"type\":\"object\"}", ReasoningEffort.LOW, 100);
	}

	private static GenerationOutcome failed(LlmFailureKind kind) {
		return new GenerationOutcome.Failed(kind, "시험 문구", null, false);
	}

}
