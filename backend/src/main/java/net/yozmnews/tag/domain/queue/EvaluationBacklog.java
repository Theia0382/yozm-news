package net.yozmnews.tag.domain.queue;

/**
 * 평가 대기의 건수 — 한 SQL 문장으로 센다 · docs/specs/tag.md §4. 공개 {@code EvaluationQueueCounts} 와 처리 현황이 이 값에서 만든다.
 *
 * @param waiting 실패로 남지 않은 대기 — 점유 중 · 미룬 것 포함
 * @param rejected 기사 거부로 실패로 남은 대기
 * @param retrying 실패로 남지 않은 대기 가운데 일시 실패를 한 번 이상 겪은 것
 * @param deferred 실패로 남지 않은 대기 가운데 평가 가능 시각이 아직 오지 않은 것 — 정착 시간 · 일시 실패 미룸
 */
public record EvaluationBacklog(long waiting, long rejected, long retrying, long deferred) {

	/**
	 * @throws IllegalArgumentException 건수가 음수이거나 일시 실패 · 미룬 대기가 대기보다 많을 때
	 */
	public EvaluationBacklog {
		if (waiting < 0 || rejected < 0 || retrying < 0 || deferred < 0) {
			throw new IllegalArgumentException("건수는 음수가 아니다");
		}
		if (retrying > waiting || deferred > waiting) {
			throw new IllegalArgumentException(
					"일시 실패 · 미룬 대기는 대기에 든다: " + retrying + " · " + deferred + " > " + waiting);
		}
	}

}
