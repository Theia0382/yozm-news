package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.evaluation.TagSetActivationResult;
import net.yozmnews.evaluation.TagSetChanges;
import net.yozmnews.evaluation.TagSetProblemView;
import org.jspecify.annotations.Nullable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 태그 체계 원문 활성화의 결과 — docs/specs/admin.md §3 · admin-R1.
 * <p>
 * {@code ACTIVATED} 면 새 버전의 {@code id}(JSON 수) · {@code number} 와 {@code changes} · {@code requeued},
 * {@code INVALID_TAG_SET} 이면 {@code problems} 가 있다. {@code UNCHANGED} 는 값이 없다. 재평가 기간이 범위 밖이면 결과가 아니라
 * 400 이다.
 */
record TagSetActivationResponse(Outcome result, @Nullable Long id, @Nullable Integer number,
		@Nullable TagSetChanges changes, @Nullable Long requeued, List<TagSetProblemView> problems) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws ResponseStatusException 400 — 재평가 기간이 범위 밖일 때
	 */
	static TagSetActivationResponse of(TagSetActivationResult result) {
		return switch (result) {
			case TagSetActivationResult.Activated activated -> new TagSetActivationResponse(Outcome.ACTIVATED,
					activated.version().id().value(), activated.version().number(), activated.changes(),
					activated.requeued(), List.of());
			case TagSetActivationResult.Unchanged unchanged ->
				new TagSetActivationResponse(Outcome.UNCHANGED, null, null, null, null, List.of());
			case TagSetActivationResult.Invalid invalid ->
				new TagSetActivationResponse(Outcome.INVALID_TAG_SET, null, null, null, null, invalid.problems());
			case TagSetActivationResult.DaysOutOfRange outOfRange -> throw ReevaluationDays.outOfRange();
		};
	}

	enum Outcome {

		/** 새 버전을 활성으로 바꿨다 */
		ACTIVATED,
		/** 내용이 활성 버전과 같아 아무것도 하지 않았다 */
		UNCHANGED,
		/** 원문 문제 · 활성 버전 대조 문제가 있어 아무것도 하지 않았다 */
		INVALID_TAG_SET

	}

}
