package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.source.CriterionShortfall;
import net.yozmnews.source.FetchFailureView;
import net.yozmnews.source.PreviewReport;
import net.yozmnews.source.ProfilePreviewResult;
import net.yozmnews.source.ProfileProblemView;
import org.jspecify.annotations.Nullable;

/**
 * 등록 전 운영자 원문의 피드 하나를 미리본 결과 — docs/specs/admin.md §3.
 * <p>
 * {@code INVALID_PROFILE} 이면 {@code problems}, {@code PASSED} 면 {@code preview}, {@code BELOW_CRITERIA} 면 {@code preview} 와
 * {@code shortfalls}, {@code UNFETCHED} 면 {@code failure} 가 있다. {@code FEED_NOT_IN_PROFILE} 은 값이 없다.
 */
record PreviewResponse(Outcome result, List<ProfileProblemView> problems, @Nullable PreviewReport preview,
		List<CriterionShortfall> shortfalls, @Nullable FetchFailureView failure) {

	static PreviewResponse of(ProfilePreviewResult result) {
		return switch (result) {
			case ProfilePreviewResult.InvalidProfile invalid ->
				new PreviewResponse(Outcome.INVALID_PROFILE, invalid.problems(), null, List.of(), null);
			case ProfilePreviewResult.FeedNotInProfile missing ->
				new PreviewResponse(Outcome.FEED_NOT_IN_PROFILE, List.of(), null, List.of(), null);
			case ProfilePreviewResult.Passed passed ->
				new PreviewResponse(Outcome.PASSED, List.of(), passed.preview(), List.of(), null);
			case ProfilePreviewResult.BelowCriteria below ->
				new PreviewResponse(Outcome.BELOW_CRITERIA, List.of(), below.preview(), below.shortfalls(), null);
			case ProfilePreviewResult.Unfetched unfetched ->
				new PreviewResponse(Outcome.UNFETCHED, List.of(), null, List.of(), unfetched.failure());
		};
	}

	enum Outcome {

		/** 원문이 검증에 걸려 받지 않았다 */
		INVALID_PROFILE,
		/** 원문에 그 피드 식별자가 없어 받지 않았다 */
		FEED_NOT_IN_PROFILE,
		/** 받아 판정했고 활성화 기준을 넘었다 */
		PASSED,
		/** 받아 판정했고 활성화 기준에 못 미쳤다 */
		BELOW_CRITERIA,
		/** 피드를 받지 못했다 */
		UNFETCHED

	}

}
