package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.source.ActivationRefusal;
import net.yozmnews.source.CriterionShortfall;
import net.yozmnews.source.FeedEnableResult;
import net.yozmnews.source.FetchFailureView;
import net.yozmnews.source.PreviewReport;
import org.jspecify.annotations.Nullable;

/**
 * 피드 켜기의 결과 — docs/specs/admin.md §3.
 * <p>
 * {@code ENABLED} 면 {@code preview}(이미 켜져 있었으면 null), {@code REFUSED} 면 {@code refusal}, {@code BELOW_CRITERIA} 면
 * {@code preview} 와 {@code shortfalls}, {@code UNFETCHED} 면 {@code failure} 가 있다. {@code READING_CHANGED} 는 값이 없다.
 */
record FeedEnableResponse(Outcome result, @Nullable PreviewReport preview, List<CriterionShortfall> shortfalls,
		@Nullable ActivationRefusal refusal, @Nullable FetchFailureView failure) {

	static FeedEnableResponse of(FeedEnableResult result) {
		return switch (result) {
			case FeedEnableResult.Enabled enabled ->
				new FeedEnableResponse(Outcome.ENABLED, enabled.preview(), List.of(), null, null);
			case FeedEnableResult.Refused refused ->
				new FeedEnableResponse(Outcome.REFUSED, null, List.of(), refused.reason(), null);
			case FeedEnableResult.BelowCriteria below ->
				new FeedEnableResponse(Outcome.BELOW_CRITERIA, below.preview(), below.shortfalls(), null, null);
			case FeedEnableResult.Unfetched unfetched ->
				new FeedEnableResponse(Outcome.UNFETCHED, null, List.of(), null, unfetched.failure());
			case FeedEnableResult.ReadingChanged changed ->
				new FeedEnableResponse(Outcome.READING_CHANGED, null, List.of(), null, null);
		};
	}

	enum Outcome {

		/** 켰다 */
		ENABLED,
		/** 설정 판정(프로필 · 피드 존재)으로 켜지 않았다 */
		REFUSED,
		/** 미리보기가 활성화 기준에 못 미쳐 켜지 않았다 */
		BELOW_CRITERIA,
		/** 피드를 받지 못해 켜지 않았다 */
		UNFETCHED,
		/** 미리본 뒤 저장 전에 그 피드의 읽기 설정이 바뀌어 켜지 않았다 */
		READING_CHANGED

	}

}
