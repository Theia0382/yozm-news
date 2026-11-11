package net.yozmnews.tag;

/**
 * 평가 대기의 건수 — docs/specs/tag.md §3
 *
 * @param waiting 실패로 남지 않은 대기 — 점유 중 포함
 * @param rejected 기사 거부로 실패로 남은 대기
 * @param exhausted 일시 실패가 재시도 상한에 이르러 실패로 남은 대기
 */
public record EvaluationQueueCounts(long waiting, long rejected, long exhausted) {

	/**
	 * @throws IllegalArgumentException 건수가 음수일 때
	 */
	public EvaluationQueueCounts {
		if (waiting < 0 || rejected < 0 || exhausted < 0) {
			throw new IllegalArgumentException("건수는 음수가 아니다");
		}
	}

}
