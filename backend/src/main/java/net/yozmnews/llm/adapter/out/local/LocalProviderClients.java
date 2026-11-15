package net.yozmnews.llm.adapter.out.local;

import java.util.Objects;

import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;

/**
 * 공급자 클라이언트 포트의 로컬 대체물 — docs/specs/llm.md §4 · llm-R16.
 * <p>
 * 가짜 키({@value #PREFIX} 로 시작)는 공급자에 묻지 않고 접두 뒤의 낱말이 정한 결과를 낸다. 그 밖의 키는 넘겨받은 구현으로 넘긴다.
 */
final class LocalProviderClients implements LlmProviderClients {

	/** 가짜 키의 접두 — 실제 공급자 키의 접두와 겹치지 않는다 */
	static final String PREFIX = "yozm-local-";

	private final LlmProviderClients delegate;

	/**
	 * @param delegate 가짜 키가 아닌 키를 받을 구현 — 결선은 운영 구현을 넣는다
	 */
	LocalProviderClients(LlmProviderClients delegate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
	}

	@Override
	public LlmProviderClient open(Provider provider, SecretValue key) {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(key, "key");
		String value = key.reveal();
		if (!value.startsWith(PREFIX)) {
			return this.delegate.open(provider, key);
		}
		ProbeResult result = result(word(value));
		return new LlmProviderClient() {

			@Override
			public ProbeResult probe() {
				return result;
			}

			@Override
			public void close() {
			}

		};
	}

	/** 접두 뒤의 낱말 — 다음 {@code -} 또는 끝까지 */
	private static String word(String value) {
		String rest = value.substring(PREFIX.length());
		int end = rest.indexOf('-');
		return (end < 0) ? rest : rest.substring(0, end);
	}

	private static ProbeResult result(String word) {
		return switch (word) {
			case "valid" -> new ProbeResult.Reachable();
			case "invalid" -> failed(LlmFailureKind.INVALID_KEY);
			case "denied" -> failed(LlmFailureKind.PERMISSION_DENIED);
			case "quota" -> failed(LlmFailureKind.QUOTA_EXHAUSTED);
			case "transient" -> failed(LlmFailureKind.TRANSIENT);
			case "unexpected" -> failed(LlmFailureKind.UNEXPECTED);
			// 모르는 낱말은 무효 키다 — 오타가 성공으로 보이지 않게 한다
			default -> failed(LlmFailureKind.INVALID_KEY);
		};
	}

	private static ProbeResult failed(LlmFailureKind kind) {
		return new ProbeResult.Failed(kind, "로컬 대체물이 정한 결과 — " + kind);
	}

}
