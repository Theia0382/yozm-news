package net.yozmnews.llm;

import net.yozmnews.platform.SecretValue;

/**
 * 모든 LLM 호출이 거치는 관문 — docs/specs/llm.md §3.
 * <p>
 * 저장한 키로 호출하는 길은 아직 없다 — 그 길도 이 인터페이스를 거친다.
 */
public interface LlmClientFactory {

	/**
	 * 그 키로 공급자에 가벼운 요청 하나를 보내 키가 쓸 수 있는지 본다 — llm-R5 ~ R10. 예상한 실패는 예외가 아니라
	 * {@link KeyValidationResult.Rejected} 다. 키를 저장하지 않고, 결과 · 로그에 키를 남기지 않는다.
	 * @throws IllegalStateException 트랜잭션 안에서 불렀을 때 — 요청을 보내지 않는다
	 */
	KeyValidationResult validate(Provider provider, SecretValue key);

}
