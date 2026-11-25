package net.yozmnews.reader.adapter.in.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.UserStorylineSummaries;
import net.yozmnews.user.UserStorylineSummaryRef;

/**
 * 꼭대기마다 그 꼭대기를 추적 대상으로 가진 정리된 사용자 스토리라인 — 한 번에 묻는 상한({@link UserStorylineSummaries#MAX_TOPS})씩
 * 나눠 묻는다. docs/specs/reader.md §5 reader-R9 · reader-R16.
 * <p>
 * 다이제스트 항목의 스토리라인과 변화 리포트의 스토리라인이 함께 쓴다.
 */
final class UserStorylineRefs {

	private final UserStorylineSummaries summaries;

	UserStorylineRefs(UserStorylineSummaries summaries) {
		this.summaries = Objects.requireNonNull(summaries, "summaries");
	}

	/** 꼭대기 → 정리된 사용자 스토리라인들 — 그런 것이 없는 꼭대기는 빠진다. 빈 입력이면 부르지 않는다 */
	Map<StorylineId, List<UserStorylineSummaryRef>> of(UserId user, List<StorylineId> tops) {
		Map<StorylineId, List<UserStorylineSummaryRef>> found = new HashMap<>();
		for (int from = 0; from < tops.size(); from += UserStorylineSummaries.MAX_TOPS) {
			found.putAll(this.summaries.ofTops(user,
					tops.subList(from, Math.min(from + UserStorylineSummaries.MAX_TOPS, tops.size()))));
		}
		return found;
	}

}
