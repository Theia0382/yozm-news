package net.yozmnews.admin.adapter.in.web;

import java.util.List;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.ProfileProblemView;
import net.yozmnews.source.ProfileRevisionResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 운영자 원문 수정의 결과 — docs/specs/admin.md §3.
 * <p>
 * {@code REVISED} 면 끈 피드 · 소스를 껐는지 · 더한 피드가, {@code INVALID_PROFILE} 이면 {@code problems} 가 있다. 프리셋 소스는 결과가
 * 아니라 404 다.
 */
record RevisionResponse(Outcome result, List<Long> disabledFeedIds, boolean sourceDisabled, List<Long> addedFeedIds,
		List<ProfileProblemView> problems) {

	/**
	 * 결과를 응답으로 옮긴다.
	 * @throws ResponseStatusException 404 — 프리셋을 참조하는 소스라 원문이 없을 때
	 */
	static RevisionResponse of(ProfileRevisionResult result) {
		return switch (result) {
			case ProfileRevisionResult.Revised revised -> new RevisionResponse(Outcome.REVISED, ids(revised.disabledFeeds()),
					revised.sourceDisabled(), ids(revised.addedFeeds()), List.of());
			case ProfileRevisionResult.InvalidProfile invalid ->
				new RevisionResponse(Outcome.INVALID_PROFILE, List.of(), false, List.of(), invalid.problems());
			case ProfileRevisionResult.NotOperatorProfile notOperator ->
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, AdminSourceController.PRESET_SOURCE_HAS_NO_TEXT);
		};
	}

	private static List<Long> ids(List<FeedId> feeds) {
		return feeds.stream().map(FeedId::value).toList();
	}

	enum Outcome {

		/** 고쳤다 */
		REVISED,
		/** 새 원문이 검증을 통과하지 못해 아무것도 바꾸지 않았다 */
		INVALID_PROFILE

	}

}
