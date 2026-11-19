package net.yozmnews.tag;

/**
 * 평가 대기의 건수 — docs/specs/tag.md §3
 *
 * @param waiting 실패로 남지 않은 대기 — 점유 중 포함
 * @param rejected 기사 거부로 실패로 남은 대기
 * @param retrying 실패로 남지 않은 대기 가운데 일시 실패를 한 번 이상 겪은 것 — {@code waiting} 에 든다
 */
public record EvaluationQueueCounts(long waiting, long rejected, long retrying) {

	/**
	 * @throws IllegalArgumentException 건수가 음수이거나 일시 실패를 겪은 대기가 대기보다 많을 때
	 */
	public EvaluationQueueCounts {
		if (waiting < 0 || rejected < 0 || retrying < 0) {
			throw new IllegalArgumentException("건수는 음수가 아니다");
		}
		if (retrying > waiting) {
			throw new IllegalArgumentException("일시 실패를 겪은 대기는 대기에 든다: " + retrying + " > " + waiting);
		}
	}

}
