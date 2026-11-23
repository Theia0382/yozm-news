package net.yozmnews.user;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.storyline.StorylineId;

/**
 * 정리된 사용자 스토리라인 하나 — 그 사용자의 LLM 키로 만든 글과 그 흐름의 추적 대상. docs/specs/user.md §3
 *
 * @param id 사용자 스토리라인 ID
 * @param head 헤드 — 그 사용자 스토리라인의 손잡이(화면의 추적 버튼 대상). {@code members} 에 든다
 * @param title 흐름 전체의 제목
 * @param summary 흐름 전체의 흐름 요약
 * @param recent 흐름 전체의 최근 전개
 * @param tracked 추적 중인가
 * @param members 저장한 추적 대상 — 스토리라인 포함 나무의 꼭대기. 비지 않는다
 * @param tops 추적 대상 꼭대기마다의 글 — 스토리라인 ID 순, {@code members} 와 같은 꼭대기다
 * @param usage 정리에 쓴 모델과 토큰
 * @param createdAt 처음 정리한 시각
 * @param updatedAt 마지막으로 정리한 시각
 */
public record UserStorylineSummaryView(UserStorylineId id, StorylineId head, String title, String summary, String recent,
		boolean tracked, Set<StorylineId> members, List<TopSummary> tops, SummaryUsage usage, Instant createdAt,
		Instant updatedAt) {

	public UserStorylineSummaryView {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(head, "head");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(summary, "summary");
		Objects.requireNonNull(recent, "recent");
		members = Set.copyOf(Objects.requireNonNull(members, "members"));
		tops = List.copyOf(Objects.requireNonNull(tops, "tops"));
		Objects.requireNonNull(usage, "usage");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(updatedAt, "updatedAt");
	}

}
