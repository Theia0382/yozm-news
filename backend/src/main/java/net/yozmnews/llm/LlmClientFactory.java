package net.yozmnews.llm;

import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;

/**
 * 모든 LLM 호출이 거치는 관문 — docs/specs/llm.md §3
 */
public interface LlmClientFactory {

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

}
