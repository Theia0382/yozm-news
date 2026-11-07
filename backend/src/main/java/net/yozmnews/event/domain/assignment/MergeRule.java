package net.yozmnews.event.domain.assignment;

import net.yozmnews.event.domain.event.LockedEvent;

/**
 * 사건 대 사건 판단의 정본 — 핵심 말 겹침의 기준 쪽과 남는 사건 · docs/specs/event.md §4 · event-R13.
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

}
