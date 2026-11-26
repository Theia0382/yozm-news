package net.yozmnews.storyline.domain.backlog;

/**
 * 회차 하나가 처리할 남은 항목의 수 — 처리 현황이 낸다 · docs/specs/storyline.md §3 {@code StorylineProcessing}.
 * <p>
 * 한 SQL 문장으로 센다 — 두 문장으로 세면 그 사이 쓰기로 미룬 것이 남은 것보다 많아질 수 있다.
 *
 * @param waiting 남은 항목 — 점유 중 · 미룬 것 포함
 * @param deferred 남은 항목 가운데 다시 볼 시각이 아직 오지 않은 것
 */
public record Backlog(long waiting, long deferred) {

	/**
	 * @throws IllegalArgumentException 수가 음수이거나 미룬 것이 남은 것보다 많을 때
	 */
	public Backlog {
		if (waiting < 0 || deferred < 0 || deferred > waiting) {
			throw new IllegalArgumentException("남은 수와 미룬 수가 맞지 않는다: " + waiting + " · " + deferred);
		}
	}

}
