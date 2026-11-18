package net.yozmnews.evaluation.domain.relation;

import java.util.Optional;

import net.yozmnews.evaluation.domain.evaluator.Relation;
import net.yozmnews.evaluation.domain.evaluator.RelationWording;
import net.yozmnews.evaluation.domain.evaluator.ResultCheck;

/**
 * 관계 판정 결과가 그 문구 판의 답과 맞는지 — 판정 때와 원본을 다시 읽을 때 함께 쓴다 · docs/specs/evaluation.md §4 ·
 * evaluation-R49 · evaluation-R50.
 * <p>
 * 답의 집합이 그 판의 답({@link RelationWording#answers})과 정확히 같아야 하고({@link ResultCheck#keyDifference}), 원본 응답은
 * {@code jsonb} 로 저장할 수 있어야 한다({@link ResultCheck#rawResponseProblem}). 어긋남의 문구는 답의 이름만 담는다.
 */
public final class RelationCheck {

	private RelationCheck() {
	}

	/**
	 * @param wording 그 결과를 낸 요청의 문구 판
	 * @return 어긋남의 운영자용 문구 — 맞으면 빈 값
	 */
	public static Optional<String> mismatch(Relation relation, RelationWording wording) {
		return ResultCheck.keyDifference("답", wording.answers(), relation.probabilities().keySet())
			.map(difference -> "답이 문구 판 " + wording.version() + " 의 선택지와 다르다 — " + difference)
			.or(() -> ResultCheck.rawResponseProblem(relation.rawResponse()));
	}

}
