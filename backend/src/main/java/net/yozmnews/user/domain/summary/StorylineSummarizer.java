package net.yozmnews.user.domain.summary;

/**
 * 정리 생성기 포트 — 사용자의 LLM 키로 사용자 스토리라인 하나의 정리 원문을 만든다. docs/specs/user.md §4
 */
public interface StorylineSummarizer {

	/**
	 * 요청으로 출력 원문을 만든다 — 파싱 · 검증하지 않는다. 트랜잭션 밖에서 불린다. 예상한 실패는 {@link SummaryOutcome.Failed}, 예외는 예상
	 * 밖의 것만이다.
	 */
	SummaryOutcome summarize(SummaryRequest request);

}
