package net.yozmnews.evaluation.domain.membership;

import java.util.Optional;
import java.util.Set;

import net.yozmnews.evaluation.domain.evaluator.Membership;
import net.yozmnews.evaluation.domain.evaluator.ResultCheck;

/**
 * 판정 결과가 물은 후보와 맞는지 — docs/specs/evaluation.md §4 · evaluation-R38.
 * <p>
 * 후보 ID 집합이 물은 후보와 정확히 같아야 하고({@link ResultCheck#keyDifference}), 원본 응답은 {@code jsonb} 로 저장할 수 있어야
 * 한다({@link ResultCheck#rawResponseProblem}).
 * 어긋남의 문구는 후보 ID 만 담는다.
 */
public final class MembershipCheck {

	private MembershipCheck() {
	}

	/**
	 * @param asked 물은 후보 ID
	 * @param noun 후보의 이름 — 사건 소속은 "사건", 스토리라인 소속은 "후보"
	 * @return 어긋남의 운영자용 문구 — 맞으면 빈 값
	 */
	public static Optional<String> mismatch(Membership membership, Set<Long> asked, String noun) {
		return ResultCheck.keyDifference(noun, asked, membership.probabilities().keySet())
			.map(difference -> noun + " 키가 물은 후보와 다르다 — " + difference)
			.or(() -> ResultCheck.rawResponseProblem(membership.rawResponse()));
	}

}
