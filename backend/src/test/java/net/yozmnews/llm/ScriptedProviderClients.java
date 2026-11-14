package net.yozmnews.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 키마다 정해 둔 답을 내는 공급자 클라이언트 포트 — 키 처리기의 통합 시험이 실제 공급자 대신 쓴다. 답을 정하지 않은 키는 성공이다. 물은 키를
 * 적는다.
 */
public final class ScriptedProviderClients implements LlmProviderClients {

	private final Map<String, Supplier<ProbeResult>> answers = new ConcurrentHashMap<>();

	private final List<String> asked = new CopyOnWriteArrayList<>();

	/** 이 키로 물으면 이 결과를 낸다 */
	public void answer(String key, ProbeResult result) {
		this.answers.put(key, () -> result);
	}

	/** 이 키로 물으면 이 종류의 실패를 낸다 */
	public void fail(String key, LlmFailureKind kind) {
		answer(key, new ProbeResult.Failed(kind, "시험 문구 " + kind));
	}

	/** 이 키로 물으면 확인 요청이 이 예외를 던진다 */
	public void raise(String key, Supplier<RuntimeException> exception) {
		this.answers.put(key, () -> {
			throw exception.get();
		});
	}

	/** 이 키로 물으면 답하기 전에 이것을 한다 — 묻는 사이에 일어나는 일을 끼워 넣는다 */
	public void before(String key, Runnable action, ProbeResult result) {
		this.answers.put(key, () -> {
			action.run();
			return result;
		});
	}

	/** 물은 키 — 물은 순서대로 */
	public List<String> asked() {
		return List.copyOf(this.asked);
	}

	@Override
	public LlmProviderClient open(Provider provider, SecretValue key) {
		String value = key.reveal();
		return new LlmProviderClient() {

			@Override
			public ProbeResult probe() {
				ScriptedProviderClients.this.asked.add(value);
				return ScriptedProviderClients.this.answers
					.getOrDefault(value, ProbeResult.Reachable::new)
					.get();
			}

			@Override
			public void close() {
			}

		};
	}

	/** 포트의 구현을 이 대역으로 바꾼다 */
	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		ScriptedProviderClients scriptedProviderClients() {
			return new ScriptedProviderClients();
		}

	}

}
