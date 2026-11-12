package net.yozmnews.event.domain.assignment;

import net.yozmnews.event.AssignmentCriteria;
import net.yozmnews.event.domain.event.LockedEvent;
import org.jspecify.annotations.Nullable;

/**
 * 사건 대 사건 판단의 정본 — 핵심 말 겹침의 기준 쪽, 남는 사건, 병합이 쓰는 앞선 배정 확률 · docs/specs/event.md §4 · event-R13.
 * <p>
 * 병합의 점수 산식은 {@link CandidateScoring}, 판정은 {@link AssignmentRule} 이다 — 사건 하나를 기사 자리에 둔 배정과 같다. 여기에는
 * 사건 대 사건에만 필요한 규칙만 둔다.
 */
public final class MergeRule {

	private MergeRule() {
	}

	/**
	 * 핵심 말 겹침의 기준이 바뀐 사건 쪽인가 — 기사 수가 적은 사건, 같으면 ID 가 큰 사건(나중에 연 쪽)의 핵심 말을 기준으로 센다. 어느 쪽이
	 * 바뀌어 회차가 보든 같은 쌍은 같은 점수다.
	 * @return 바뀐 사건의 핵심 말이 기준이면 true, 상대 사건의 핵심 말이면 false
	 */
	public static boolean termsFromSubject(int subjectCount, long subjectId, int otherCount, long otherId) {
		if (subjectCount != otherCount) {
			return subjectCount < otherCount;
		}
		return subjectId > otherId;
	}

	/**
	 * 남는 사건 — 기사 수가 많은 쪽, 같으면 ID 가 작은 쪽(먼저 연 쪽).
	 * @return 남는 사건의 ID
	 */
	public static long survivor(LockedEvent a, LockedEvent b) {
		if (a.articleCount() != b.articleCount()) {
			return (a.articleCount() > b.articleCount()) ? a.id() : b.id();
		}
		return Math.min(a.id(), b.id());
	}

	/**
	 * 병합이 기록 없는 띠 쌍에 쓰는 앞선 배정 확률 — 확률 기준 미만이면 그 값(붙일 수 없는 후보가 되어 묻지 않는다), 기준 이상이거나 없으면
	 * null(묻는다). 앞선 확률로는 합치지 않는다.
	 * @param prior 그 쌍의 앞선 배정 확률(최댓값), 없으면 null
	 */
	public static @Nullable Double priorProbability(@Nullable Double prior, AssignmentCriteria criteria) {
		return (prior == null || AssignmentRule.passes(prior, criteria)) ? null : prior;
	}

}
