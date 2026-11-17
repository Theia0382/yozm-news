package net.yozmnews.llm.domain.provider;

/**
 * 키 하나의 공급자 클라이언트 — docs/specs/llm.md §4 "나가는 쪽" · llm-R6 · R14 · R19
 */
public interface LlmProviderClient extends AutoCloseable {

	/** 가벼운 요청 하나로 키가 쓸 수 있는지 본다. 예상한 실패는 {@link ProbeResult.Failed} 로 내고 예외로 던지지 않는다 */
	ProbeResult probe();

	/**
	 * 생성 요청 하나를 보낸다 — 다시 보내지 않는다. 예상한 실패는 {@link GenerationOutcome.Failed} 로 내고 예외로 던지지 않는다. 출력
	 * 스키마는 모델이 구조화 출력을 지원할 때만 그 공급자의 꼴로 맞춰 강제 인자로 보낸다.
	 */
	GenerationOutcome generate(GenerationCall call);

	/** 쥔 연결 · 스레드를 푼다. 여러 번 불러도 되고, 닫은 뒤의 요청은 보내지 않는다 */
	@Override
	void close();

}
