package net.yozmnews.llm.domain.provider;

import java.time.Duration;

import net.yozmnews.llm.Provider;
import net.yozmnews.platform.SecretValue;

/**
 * 공급자 클라이언트를 만드는 포트 — docs/specs/llm.md §4 "나가는 쪽" · llm-R7 · llm-R11
 */
public interface LlmProviderClients {

	/**
	 * 그 키의 클라이언트를 새로 만든다. 요청을 보내지 않는다. 부른 쪽이 닫는다.
	 *
	 * @param deadline 이 클라이언트가 보내는 요청 하나의 마감(헤더와 본문 전부) — 검증과 생성이 다르다
	 */
	LlmProviderClient open(Provider provider, SecretValue key, Duration deadline);

}
