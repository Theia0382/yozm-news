package net.yozmnews.llm.domain.provider;

/**
 * 키 하나의 공급자 클라이언트 — docs/specs/llm.md §4 "나가는 쪽" · llm-R6 · R14
 */
public interface LlmProviderClient extends AutoCloseable {

	/** 가벼운 요청 하나로 키가 쓸 수 있는지 본다. 예상한 실패는 {@link ProbeResult.Failed} 로 내고 예외로 던지지 않는다 */
	ProbeResult probe();

	/** 쥔 연결 · 스레드를 푼다. 여러 번 불러도 되고, 닫은 뒤의 요청은 보내지 않는다 */
	@Override
	void close();

}
