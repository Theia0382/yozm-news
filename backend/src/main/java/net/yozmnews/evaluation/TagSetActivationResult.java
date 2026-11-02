package net.yozmnews.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * 태그 체계 활성화의 결과 — docs/specs/evaluation.md §3 · evaluation-R16 · evaluation-R25
 */
public sealed interface TagSetActivationResult {

	/**
	 * 새 버전을 활성으로 바꿨다.
	 *
	 * @param requeued 재평가 대기에 넣거나 되돌린 행 수 — 이미 대기 중 · 점유 중인 행은 세지 않는다
	 */
	record Activated(TagSetVersionView version, TagSetChanges changes, long requeued)
			implements TagSetActivationResult {

		public Activated {
			Objects.requireNonNull(version, "version");
			Objects.requireNonNull(changes, "changes");
		}

	}

	/** 내용이 기준 버전과 같아 아무것도 하지 않았다 */
	record Unchanged() implements TagSetActivationResult {
	}

	/** 원문 문제 · 기준 버전 대조 문제가 있어 아무것도 하지 않았다 — 찾은 순서대로 모두 */
	record Invalid(List<TagSetProblemView> problems) implements TagSetActivationResult {

		public Invalid {
			problems = List.copyOf(problems);
		}

	}

	/** 재평가 기간이 0 ~ {@link TagSetAdministration#MAX_REEVALUATION_DAYS} 밖이라 아무것도 하지 않았다 */
	record DaysOutOfRange() implements TagSetActivationResult {
	}

}
