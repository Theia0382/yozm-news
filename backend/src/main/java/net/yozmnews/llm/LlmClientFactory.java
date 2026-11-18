package net.yozmnews.llm;

import java.time.Duration;

import net.yozmnews.llm.domain.retry.RetryPolicy;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;

/**
 * 모든 LLM 호출이 거치는 관문 — docs/specs/llm.md §3
 */
public interface LlmClientFactory {

	/**
	 * 생성 호출의 요청 하나 마감(헤더 · 본문 전부) — llm-R7. 측정 전의 잠정값이다. 허용 모델이 다이제스트 한 편을 다 쓰는 시간을 재서 고친다.
	 * 스트리밍 없이 응답 전체를 기다리므로 출력 전체의 생성 시간을 덮어야 한다.
	 */
	Duration GENERATION_DEADLINE = Duration.ofSeconds(180);

	/**
	 * 생성 호출 한 번({@link #generateStored})이 걸릴 수 있는 가장 긴 시간 — 일시 장애를 정해진 만큼 다시 보내고 그 사이를 가장 길게 기다린
	 * 경우(llm-R20). 부르는 쪽이 멈춘 생성을 가를 때 쓴다
	 */
	Duration LONGEST_GENERATION_CALL = RetryPolicy.longestCall(GENERATION_DEADLINE);

	/**
	 * 그 키로 공급자에 가벼운 요청 하나를 보내 키가 쓸 수 있는지 본다 — llm-R5 ~ R10. 예상한 실패는 예외가 아니라
	 * {@link KeyValidationResult.Rejected} 다. 키를 저장하지 않고, 결과 · 로그에 키를 남기지 않는다.
	 * @throws IllegalStateException 트랜잭션 안에서 불렀을 때 — 요청을 보내지 않는다
	 */
	KeyValidationResult validate(Provider provider, SecretValue key);

	/**
	 * 그 사용자 · 공급자의 저장한 키로 같은 확인을 하고 결과를 그 키에 알린다 — llm-R17.
	 * @throws IllegalStateException 트랜잭션 안에서 불렀을 때 — 요청을 보내지 않는다
	 */
	StoredKeyValidation validateStored(UserId user, Provider provider);

	/**
	 * 그 사용자의 저장한 키(요청한 모델의 공급자)로 생성 요청을 보내고 결과를 그 키에 알린다 — llm-R19 ~ R23. 일시 장애는 정해진 만큼 다시
	 * 보낸다. 예상한 실패는 예외가 아니라 {@link GenerationResult.Failed} 다. 결과 · 예외 문구 · 로그에 키 · 프롬프트 · 응답 본문을 남기지
	 * 않는다. 화면 요청 안에서 부르는 길이가 아니다 — 생성 요청 하나의 마감이 길고 다시 보낸다.
	 * @return 저장한 키가 없으면 {@link StoredGeneration.Missing} — 공급자에 보내지 않는다
	 * @throws IllegalStateException 트랜잭션 안에서 불렀을 때 — 요청을 보내지 않는다
	 */
	StoredGeneration generateStored(UserId user, GenerationRequest request);

}
