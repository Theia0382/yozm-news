package net.yozmnews.credential;

import java.util.Set;

import net.yozmnews.llm.Provider;
import net.yozmnews.platform.UserId;

/**
 * 사용자의 키 유무와 키가 있는 공급자 — docs/specs/credential.md §3 · credential-R18
 */
public interface LlmKeys {

	/** 그 사용자의 저장한 키가 하나라도 있는가 — 공급자 · 상태와 무관하다 */
	boolean present(UserId user);

	/** 그 사용자의 저장한 키가 있는 공급자 — 상태와 무관하다. 공급자 선언 순으로 돈다. 키가 없으면 빈 집합 */
	Set<Provider> providers(UserId user);

	/** 그 사용자의 상태가 {@code VALID} 인 키의 공급자 — 공급자 선언 순으로 돈다. 없으면 빈 집합 */
	Set<Provider> usable(UserId user);

}
