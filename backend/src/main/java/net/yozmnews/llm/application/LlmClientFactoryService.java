package net.yozmnews.llm.application;

import java.util.Objects;

import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.HeaderValueShape;
import net.yozmnews.platform.SecretValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 공개 API {@link LlmClientFactory} 의 구현 — docs/specs/llm.md §4.
 * <p>
 * 트랜잭션 밖인지 보고(llm-R5), 키의 꼴을 보고(llm-R10), 포트로 클라이언트를 열어 확인 요청을 하고 닫는다(llm-R14). 포트가 던진 예상 밖의
 * 예외는 문구를 버리고 {@link LlmFailureKind#UNEXPECTED} 로 바꾼다(llm-R9). 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class LlmClientFactoryService implements LlmClientFactory {

	static final String IN_TRANSACTION = "LLM 공급자 호출을 트랜잭션 안에서 불렀다 — 공급자 호출은 트랜잭션 밖에서 한다";

	static final String MALFORMED_KEY = "키의 형식이 틀렸다";

	/** 예상 밖 예외의 실패 문구 머리 — 뒤에 예외 클래스 이름을 잇는다 */
	static final String UNEXPECTED_FAILURE = "LLM 키 확인 중 예상 못한 오류 — ";

	private static final Logger log = LoggerFactory.getLogger(LlmClientFactoryService.class);

	private final LlmProviderClients clients;

	LlmClientFactoryService(LlmProviderClients clients) {
		this.clients = Objects.requireNonNull(clients, "clients");
	}

	@Override
	public KeyValidationResult validate(Provider provider, SecretValue key) {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(key, "key");
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
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
			// 라이브러리 예외의 문구에는 공급자가 되돌려준 키 조각이 실릴 수 있다 — 클래스 이름만 남긴다
			String name = ex.getClass().getSimpleName();
			log.warn("{}{}", UNEXPECTED_FAILURE, name);
			return new KeyValidationResult.Rejected(LlmFailureKind.UNEXPECTED, UNEXPECTED_FAILURE + name);
		}
	}

}
