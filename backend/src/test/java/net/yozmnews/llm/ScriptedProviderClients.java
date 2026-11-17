package net.yozmnews.llm;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import net.yozmnews.llm.domain.provider.GenerationCall;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.platform.SecretValue;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 키마다 정해 둔 답을 내는 공급자 클라이언트 포트 — 키 처리기 · 생성 호출의 통합 시험이 실제 공급자 대신 쓴다. 답을 정하지 않은 키는
 * 확인이 성공하고 생성이 빈 JSON 객체로 끝까지 답한다. 물은 키 · 생성 요청 · 연 마감을 적는다.
 */
public final class ScriptedProviderClients implements LlmProviderClients {

	private final Map<String, Supplier<ProbeResult>> answers = new ConcurrentHashMap<>();

	private final Map<String, Deque<GenerationOutcome>> generations = new ConcurrentHashMap<>();

	private final List<String> asked = new CopyOnWriteArrayList<>();

	private final List<GenerationCall> generated = new CopyOnWriteArrayList<>();

	private final List<Duration> deadlines = new CopyOnWriteArrayList<>();

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

	/** 이 키의 생성 요청에 차례로 이 결과들을 낸다 — 다 쓰면 마지막 결과를 되풀이한다 */
	public void generate(String key, GenerationOutcome... outcomes) {
		this.generations.put(key, new ArrayDeque<>(List.of(outcomes)));
	}

	/** 물은 키 — 물은 순서대로 */
	public List<String> asked() {
		return List.copyOf(this.asked);
	}

	/** 받은 생성 요청 — 받은 순서대로 */
	public List<GenerationCall> generated() {
		return List.copyOf(this.generated);
	}

	/** 클라이언트를 연 마감 — 연 순서대로 */
	public List<Duration> deadlines() {
		return List.copyOf(this.deadlines);
	}

	@Override
	public LlmProviderClient open(Provider provider, SecretValue key, Duration deadline) {
		String value = key.reveal();
		this.deadlines.add(deadline);
		return new LlmProviderClient() {

			@Override
			public ProbeResult probe() {
				ScriptedProviderClients.this.asked.add(value);
				return ScriptedProviderClients.this.answers
					.getOrDefault(value, ProbeResult.Reachable::new)
					.get();
			}

			@Override
			public GenerationOutcome generate(GenerationCall call) {
				ScriptedProviderClients.this.generated.add(call);
				Deque<GenerationOutcome> outcomes = ScriptedProviderClients.this.generations.get(value);
				if (outcomes == null || outcomes.isEmpty()) {
					return new GenerationOutcome.Answered("{}", 0, 0, GenerationStop.COMPLETE);
				}
				return (outcomes.size() > 1) ? outcomes.poll() : outcomes.peek();
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
