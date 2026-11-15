package net.yozmnews.llm.application;

import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.StoredKey;
import net.yozmnews.llm.StoredKeySource;
import net.yozmnews.llm.StoredKeyValidation;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.HeaderValueShape;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 공개 API {@link LlmClientFactory} 의 구현 — docs/specs/llm.md §4.
 * <p>
 * 트랜잭션 밖인지 보고(llm-R5), 키의 꼴을 보고(llm-R10), 포트로 클라이언트를 열어 확인 요청을 하고 닫는다(llm-R14). 포트가 던진 예상 밖의
 * 예외는 문구를 버리고 {@link LlmFailureKind#UNEXPECTED} 로 바꾼다(llm-R9). 저장한 키의 검증은 공급 인터페이스로 키를 받아 같은 확인을 하고
 * 결과를 그 키에 알린다(llm-R17). 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class LlmClientFactoryService implements LlmClientFactory {

	static final String IN_TRANSACTION = "LLM 공급자 호출을 트랜잭션 안에서 불렀다 — 공급자 호출은 트랜잭션 밖에서 한다";

	static final String MALFORMED_KEY = "키의 형식이 틀렸다";

	/** 예상 밖 예외의 실패 문구 머리 — 뒤에 예외 클래스 이름을 잇는다 */
	static final String UNEXPECTED_FAILURE = "LLM 키 확인 중 예상 못한 오류 — ";

	private static final Logger log = LoggerFactory.getLogger(LlmClientFactoryService.class);

	private final LlmProviderClients clients;

	private final StoredKeySource storedKeys;

	LlmClientFactoryService(LlmProviderClients clients, StoredKeySource storedKeys) {
		this.clients = Objects.requireNonNull(clients, "clients");
		this.storedKeys = Objects.requireNonNull(storedKeys, "storedKeys");
	}

	@Override
	public KeyValidationResult validate(Provider provider, SecretValue key) {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(key, "key");
		requireNoTransaction();
		return check(provider, key);
	}

	@Override
	public StoredKeyValidation validateStored(UserId user, Provider provider) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(provider, "provider");
		requireNoTransaction();
		Optional<StoredKey> stored;
		try {
			stored = this.storedKeys.find(user, provider);
		}
		catch (RuntimeException ex) {
			return new StoredKeyValidation.Checked(unexpected(ex));
		}
		if (stored.isEmpty()) {
			return new StoredKeyValidation.Missing();
		}
		KeyValidationResult result = check(provider, stored.get().value());
		this.storedKeys.checked(stored.get().id(), result);
		return new StoredKeyValidation.Checked(result);
	}

	private static void requireNoTransaction() {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
	}

	/** 키의 꼴을 보고 공급자에 확인 요청 하나를 보낸다 — 주어진 키와 저장한 키의 검증이 함께 쓰는 걸음 */
	private KeyValidationResult check(Provider provider, SecretValue key) {
		if (HeaderValueShape.violation(key.reveal()).isPresent()) {
			return new KeyValidationResult.Rejected(LlmFailureKind.INVALID_KEY, MALFORMED_KEY);
		}
		try (LlmProviderClient client = this.clients.open(provider, key)) {
			return switch (client.probe()) {
				case ProbeResult.Reachable reachable -> new KeyValidationResult.Valid();
				case ProbeResult.Failed failed -> new KeyValidationResult.Rejected(failed.kind(), failed.message());
			};
		}
		catch (RuntimeException ex) {
			return unexpected(ex);
		}
	}

	/** 예상 밖의 예외를 결과로 바꾼다 — 예외의 문구에는 키 조각이 실릴 수 있어 클래스 이름만 남긴다 */
	private static KeyValidationResult.Rejected unexpected(RuntimeException ex) {
		String name = ex.getClass().getSimpleName();
		log.warn("{}{}", UNEXPECTED_FAILURE, name);
		return new KeyValidationResult.Rejected(LlmFailureKind.UNEXPECTED, UNEXPECTED_FAILURE + name);
	}

}
