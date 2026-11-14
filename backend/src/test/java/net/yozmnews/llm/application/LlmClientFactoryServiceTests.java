package net.yozmnews.llm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 키 검증의 유스케이스 — 트랜잭션 밖에서만, 꼴이 틀린 키는 묻지 않고, 연 클라이언트를 닫고, 예상 밖 예외의 문구를 버린다.
 * <p>
 * 지키는 규칙: llm-R5 · llm-R9 · llm-R10 · llm-R14
 */
@ExtendWith(OutputCaptureExtension.class)
class LlmClientFactoryServiceTests {

	private static final SecretValue KEY = SecretValue.of("sk-service-test-0123456789");

	private final RecordingClients clients = new RecordingClients();

	private final LlmClientFactoryService service = new LlmClientFactoryService(this.clients);

	@Test
	void aReachableProviderMakesTheKeyValid() {
		this.clients.probe = ProbeResult.Reachable::new;

		assertThat(this.service.validate(Provider.ANTHROPIC, KEY)).isEqualTo(new KeyValidationResult.Valid());
		assertThat(this.clients.opened).containsExactly(Provider.ANTHROPIC);
		assertThat(this.clients.keys).containsExactly(KEY);
		assertThat(this.clients.closed).isEqualTo(1);
	}

	@Test
	void aFailedProbeIsRejectedWithItsKindAndMessageAndTheClientIsClosed() {
		this.clients.probe = () -> new ProbeResult.Failed(LlmFailureKind.QUOTA_EXHAUSTED, "상태 429");

		assertThat(this.service.validate(Provider.OPENAI, KEY))
			.isEqualTo(new KeyValidationResult.Rejected(LlmFailureKind.QUOTA_EXHAUSTED, "상태 429"));
		assertThat(this.clients.closed).isEqualTo(1);
	}

	@Test
	void aMalformedKeyIsRejectedWithoutAskingTheProvider() {
		assertThat(this.service.validate(Provider.OPENAI, SecretValue.of("sk-secret\r\nX-Injected: 1")))
			.isEqualTo(new KeyValidationResult.Rejected(LlmFailureKind.INVALID_KEY, "키의 형식이 틀렸다"));
		assertThat(this.service.validate(Provider.OPENAI, SecretValue.of("k".repeat(513))))
			.isEqualTo(new KeyValidationResult.Rejected(LlmFailureKind.INVALID_KEY, "키의 형식이 틀렸다"));
		assertThat(this.clients.opened).isEmpty();
	}

	@Test
	void theLongestKeyIsAsked() {
		this.clients.probe = ProbeResult.Reachable::new;

		assertThat(this.service.validate(Provider.OPENAI, SecretValue.of("k".repeat(512))))
			.isEqualTo(new KeyValidationResult.Valid());
		assertThat(this.clients.opened).containsExactly(Provider.OPENAI);
	}

	@Test
	void refusesToCallInsideATransaction() {
		TransactionSynchronizationManager.setActualTransactionActive(true);
		try {
			assertThatIllegalStateException().isThrownBy(() -> this.service.validate(Provider.OPENAI, KEY))
				.withMessage("LLM 공급자 호출을 트랜잭션 안에서 불렀다 — 공급자 호출은 트랜잭션 밖에서 한다");
		}
		finally {
			TransactionSynchronizationManager.setActualTransactionActive(false);
		}
		assertThat(this.clients.opened).isEmpty();
	}

	@Test
	void dropsTheMessageOfAnUnexpectedExceptionAndClosesTheClient(CapturedOutput output) {
		this.clients.probe = () -> {
			throw new IllegalArgumentException("Incorrect API key provided: sk-leaked-fragment-wxyz");
		};

		KeyValidationResult result = this.service.validate(Provider.OPENAI, KEY);

		assertThat(result).isEqualTo(new KeyValidationResult.Rejected(LlmFailureKind.UNEXPECTED,
				"LLM 키 확인 중 예상 못한 오류 — IllegalArgumentException"));
		assertThat(this.clients.closed).isEqualTo(1);
		// 대조 — 경고 로그가 캡처됐다. 아래의 부정 단언이 빈 출력 때문에 통과하는 것이 아니다
		assertThat(output.getAll()).contains("LLM 키 확인 중 예상 못한 오류 — IllegalArgumentException");
		assertThat(output.getAll()).doesNotContain("sk-leaked-fragment-wxyz", "Incorrect API key", KEY.reveal());
	}

	@Test
	void anExceptionWhileOpeningIsAlsoUnexpected() {
		this.clients.failOnOpen = true;

		assertThat(this.service.validate(Provider.OPENAI, KEY)).isEqualTo(new KeyValidationResult.Rejected(
				LlmFailureKind.UNEXPECTED, "LLM 키 확인 중 예상 못한 오류 — IllegalStateException"));
	}

	/** 연 공급자 · 키와 닫은 횟수를 적는 가짜 포트 */
	private static final class RecordingClients implements LlmProviderClients {

		private final List<Provider> opened = new ArrayList<>();

		private final List<SecretValue> keys = new ArrayList<>();

		private Supplier<ProbeResult> probe = ProbeResult.Reachable::new;

		private boolean failOnOpen;

		private int closed;

		@Override
		public LlmProviderClient open(Provider provider, SecretValue key) {
			if (this.failOnOpen) {
				throw new IllegalStateException("sk-open-failure");
			}
			this.opened.add(provider);
			this.keys.add(key);
			return new LlmProviderClient() {

				@Override
				public ProbeResult probe() {
					return RecordingClients.this.probe.get();
				}

				@Override
				public void close() {
					RecordingClients.this.closed++;
				}

			};
		}

	}

}
