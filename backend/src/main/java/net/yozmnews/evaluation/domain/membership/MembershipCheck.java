package net.yozmnews.evaluation.domain.membership;

import java.util.Optional;
import java.util.Set;

import net.yozmnews.evaluation.domain.evaluator.Membership;
import net.yozmnews.evaluation.domain.evaluator.ResultCheck;

/**
 * 판정 결과가 물은 후보와 맞는지 — docs/specs/evaluation.md §4 · evaluation-R38.
 * <p>
 * 사건 ID 집합이 물은 후보와 정확히 같아야 하고({@link ResultCheck#keyDifference}), 원본 응답은 {@code jsonb} 로 저장할 수 있어야
 * 한다({@link ResultCheck#rawResponseProblem}).
 * 어긋남의 문구는 사건 ID 만 담는다.
 */
public final class MembershipCheck {

	private MembershipCheck() {
	}

	/**
	 * @param asked 물은 후보 사건 ID
	 * @return 어긋남의 운영자용 문구 — 맞으면 빈 값
	 */
	public static Optional<String> mismatch(Membership membership, Set<Long> asked) {
		return ResultCheck.keyDifference("사건", asked, membership.probabilities().keySet())
			.map(difference -> "사건 키가 물은 후보와 다르다 — " + difference)
			.or(() -> ResultCheck.rawResponseProblem(membership.rawResponse()));
	}

}
