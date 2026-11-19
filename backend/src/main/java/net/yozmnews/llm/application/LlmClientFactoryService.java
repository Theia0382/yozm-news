package net.yozmnews.llm.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import net.yozmnews.llm.GenerationRequest;
import net.yozmnews.llm.GenerationResult;
import net.yozmnews.llm.KeyValidationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.StoredGeneration;
import net.yozmnews.llm.StoredKey;
import net.yozmnews.llm.StoredKeySource;
import net.yozmnews.llm.StoredKeyValidation;
import net.yozmnews.llm.domain.budget.OutputBudget;
import net.yozmnews.llm.domain.provider.GenerationCall;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.llm.domain.provider.LlmProviderClients;
import net.yozmnews.llm.domain.provider.ProbeResult;
import net.yozmnews.llm.domain.retry.RetryPolicy;
import net.yozmnews.platform.AppClock;
import net.yozmnews.platform.HeaderValueShape;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 공개 API {@link LlmClientFactory} 의 구현 — docs/specs/llm.md §4.
 * <p>
 * 트랜잭션 밖인지 보고(llm-R5), 키의 꼴을 보고(llm-R10), 포트로 클라이언트를 열어 요청하고 닫는다(llm-R14). 포트가 던진 예상 밖의 예외는
 * 문구를 버리고 {@link LlmFailureKind#UNEXPECTED} 로 바꾼다(llm-R9). 저장한 키의 검증과 생성 호출은 공급 인터페이스로 키를 받아 요청하고
 * 결과를 그 키에 알린다(llm-R17 · R19) — 두 길이 받기 · 알리기의 걸음 하나를 쓴다. 생성 호출은 일시 장애를 정해진 만큼 다시
 * 보낸다(llm-R20). 트랜잭션 · 역할 애너테이션이 없다(두 역할 공통).
 */
@Service
class LlmClientFactoryService implements LlmClientFactory {

	static final String IN_TRANSACTION = "LLM 공급자 호출을 트랜잭션 안에서 불렀다 — 공급자 호출은 트랜잭션 밖에서 한다";

	static final String MALFORMED_KEY = "키의 형식이 틀렸다";

	/** 키 검증의 요청 하나 마감(헤더 · 본문 전부) — 화면 요청 안에서 불러도 되는 길이다 */
	static final Duration VALIDATION_DEADLINE = Duration.ofSeconds(15);

	/** 예상 밖 예외의 실패 문구 머리(키 확인) — 뒤에 예외 클래스 이름을 잇는다 */
	static final String UNEXPECTED_FAILURE = "LLM 키 확인 중 예상 못한 오류 — ";

	/** 예상 밖 예외의 실패 문구 머리(생성) — 뒤에 예외 클래스 이름을 잇는다 */
	static final String UNEXPECTED_GENERATION = "LLM 생성 중 예상 못한 오류 — ";

	/** 다시 보내기 전에 기다리다 중단됐을 때의 실패 문구 */
	static final String INTERRUPTED = "LLM 생성을 다시 보내기 전에 기다리다 중단됐다";

	private static final Logger log = LoggerFactory.getLogger(LlmClientFactoryService.class);

	private final LlmProviderClients clients;

	private final StoredKeySource storedKeys;

	private final AppClock clock;

	private final Sleeper sleeper;

	@Autowired
	LlmClientFactoryService(LlmProviderClients clients, StoredKeySource storedKeys, AppClock clock) {
		this(clients, storedKeys, clock, Thread::sleep);
	}

	LlmClientFactoryService(LlmProviderClients clients, StoredKeySource storedKeys, AppClock clock,
			Sleeper sleeper) {
		this.clients = Objects.requireNonNull(clients, "clients");
		this.storedKeys = Objects.requireNonNull(storedKeys, "storedKeys");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
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
		return this.<KeyValidationResult>withStoredKey(user, provider, key -> check(provider, key), result -> result,
				ex -> rejected(ex))
			.<StoredKeyValidation>map(StoredKeyValidation.Checked::new)
			.orElseGet(StoredKeyValidation.Missing::new);
	}

	@Override
	public StoredGeneration generateStored(UserId user, GenerationRequest request) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(request, "request");
		requireNoTransaction();
		Instant start = this.clock.instant();
		Provider provider = request.model().provider();
		return this.<GenerationResult>withStoredKey(user, provider, key -> generate(provider, key, request, start),
				LlmClientFactoryService::reported,
				ex -> new GenerationResult.Failed(LlmFailureKind.UNEXPECTED, unexpected(ex, UNEXPECTED_GENERATION),
						elapsed(start)))
			.<StoredGeneration>map(StoredGeneration.Done::new)
			.orElseGet(StoredGeneration.Missing::new);
	}

	private static void requireNoTransaction() {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(IN_TRANSACTION);
		}
	}

	/**
	 * 저장한 키로 걸음 하나를 하고 그 결과를 키에 알린다 — 검증과 생성이 함께 쓰는 순서(llm-R17 · R19).
	 * <p>
	 * 키가 없으면 빈 값이다. 키를 받다가 던진 예외는 {@code unopened} 의 결과로 바꾸고 알리지 않는다. 알리다 던진 예외는 그대로 올린다 — 결과를
	 * 알리지 못한 걸음을 끝난 것으로 내지 않는다.
	 */
	private <R> Optional<R> withStoredKey(UserId user, Provider provider, Function<SecretValue, R> step,
			Function<R, KeyValidationResult> report, Function<RuntimeException, R> unopened) {
		Optional<StoredKey> stored;
		try {
			stored = this.storedKeys.find(user, provider);
		}
		catch (RuntimeException ex) {
			return Optional.of(unopened.apply(ex));
		}
		if (stored.isEmpty()) {
			return Optional.empty();
		}
		R result = step.apply(stored.get().value());
		this.storedKeys.checked(stored.get().id(), report.apply(result));
		return Optional.of(result);
	}

	/** 키의 꼴을 보고 공급자에 확인 요청 하나를 보낸다 — 주어진 키와 저장한 키의 검증이 함께 쓰는 걸음 */
	private KeyValidationResult check(Provider provider, SecretValue key) {
		if (HeaderValueShape.violation(key.reveal()).isPresent()) {
			return new KeyValidationResult.Rejected(LlmFailureKind.INVALID_KEY, MALFORMED_KEY);
		}
		try (LlmProviderClient client = this.clients.open(provider, key, VALIDATION_DEADLINE)) {
			return switch (client.probe()) {
				case ProbeResult.Reachable reachable -> new KeyValidationResult.Valid();
				case ProbeResult.Failed failed -> new KeyValidationResult.Rejected(failed.kind(), failed.message());
			};
		}
		catch (RuntimeException ex) {
			return rejected(ex);
		}
	}

	/**
	 * 키의 꼴을 보고 생성 요청을 보낸다 — 일시 장애는 다시 보내기 정책이 정한 만큼 기다렸다가 같은 클라이언트로 다시 보낸다(llm-R19 · R20).
	 */
	private GenerationResult generate(Provider provider, SecretValue key, GenerationRequest request, Instant start) {
		if (HeaderValueShape.violation(key.reveal()).isPresent()) {
			return new GenerationResult.Failed(LlmFailureKind.INVALID_KEY, MALFORMED_KEY, elapsed(start));
		}
		LlmModel model = request.model();
		GenerationCall call = new GenerationCall(model.id(), model.structuredOutput(), request.instructions(),
				request.userContent(), request.outputSchema(), request.effort(),
				OutputBudget.total(request.maxBodyTokens(), request.effort()));
		try (LlmProviderClient client = this.clients.open(provider, key, LlmClientFactory.GENERATION_DEADLINE)) {
			int sent = 0;
			while (true) {
				GenerationOutcome outcome = client.generate(call);
				sent++;
				switch (outcome) {
					case GenerationOutcome.Answered answered -> {
						return new GenerationResult.Generated(answered.text(), answered.inputTokens(),
								answered.outputTokens(), elapsed(start), answered.stop(), call.enforcesSchema());
					}
					case GenerationOutcome.Failed failed -> {
						Optional<Duration> wait = RetryPolicy.next(sent, failed.kind(), failed.retryAfter(),
								failed.deadlinePassed());
						if (wait.isEmpty()) {
							return new GenerationResult.Failed(failed.kind(), failed.message(), elapsed(start));
						}
						try {
							this.sleeper.sleep(wait.get());
						}
						catch (InterruptedException ex) {
							Thread.currentThread().interrupt();
							return new GenerationResult.Failed(LlmFailureKind.TRANSIENT, INTERRUPTED, elapsed(start));
						}
					}
				}
			}
		}
		catch (RuntimeException ex) {
			return new GenerationResult.Failed(LlmFailureKind.UNEXPECTED, unexpected(ex, UNEXPECTED_GENERATION),
					elapsed(start));
		}
	}

	/** 생성 결과를 키에 알릴 꼴로 — 공급자가 답했으면 그 키가 동작한 것이다 */
	private static KeyValidationResult reported(GenerationResult result) {
		return switch (result) {
			case GenerationResult.Generated generated -> new KeyValidationResult.Valid();
			case GenerationResult.Failed failed -> new KeyValidationResult.Rejected(failed.kind(), failed.message());
		};
	}

	/** 생성 시작부터 시계가 넘긴 시간을 뺀 경과 — 0 이상이다 */
	private Duration elapsed(Instant start) {
		return this.clock.elapsedSince(start);
	}

	private static KeyValidationResult.Rejected rejected(RuntimeException ex) {
		return new KeyValidationResult.Rejected(LlmFailureKind.UNEXPECTED, unexpected(ex, UNEXPECTED_FAILURE));
	}

	/** 예상 밖의 예외를 실패 문구로 바꾼다 — 예외의 문구에는 키 조각이 실릴 수 있어 클래스 이름만 남긴다 */
	private static String unexpected(RuntimeException ex, String head) {
		String name = ex.getClass().getSimpleName();
		log.warn("{}{}", head, name);
		return head + name;
	}

}
