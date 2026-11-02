package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.evaluation.TagSetChanges;
import net.yozmnews.evaluation.TagSetProblemView;
import net.yozmnews.evaluation.TagSetReviewResult;
import org.jspecify.annotations.Nullable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 태그 체계 원문 확인의 결과 — docs/specs/admin.md §3 · admin-R1.
 * <p>
 * {@code READY} 면 {@code changes} 와 {@code recentArticles}, {@code INVALID_TAG_SET} 이면 {@code problems} 가 있다.
 * {@code UNCHANGED} 는 값이 없다. 재평가 기간이 범위 밖이면 결과가 아니라 400 이다.
 */
record TagSetReviewResponse(Outcome result, @Nullable TagSetChanges changes, @Nullable Long recentArticles,
		List<TagSetProblemView> problems) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws ResponseStatusException 400 — 재평가 기간이 범위 밖일 때
	 */
	static TagSetReviewResponse of(TagSetReviewResult result) {
		return switch (result) {
			case TagSetReviewResult.Ready ready ->
				new TagSetReviewResponse(Outcome.READY, ready.changes(), ready.recentArticles(), List.of());
			case TagSetReviewResult.Unchanged unchanged ->
				new TagSetReviewResponse(Outcome.UNCHANGED, null, null, List.of());
			case TagSetReviewResult.Invalid invalid ->
				new TagSetReviewResponse(Outcome.INVALID_TAG_SET, null, null, invalid.problems());
			case TagSetReviewResult.DaysOutOfRange outOfRange -> throw ReevaluationDays.outOfRange();
		};
	}

	enum Outcome {

		/** 활성화할 수 있다 */
		READY,
		/** 내용이 활성 버전과 같다 */
		UNCHANGED,
		/** 원문 문제 · 활성 버전 대조 문제가 있다 */
		INVALID_TAG_SET

	}

}
