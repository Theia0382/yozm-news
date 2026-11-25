package net.yozmnews.user;

import java.util.Objects;

/**
 * 정리된 사용자 스토리라인을 가리키는 값 — 다이제스트 항목이 그 사용자 스토리라인의 제목과 링크를 붙일 때 쓴다. docs/specs/user.md §3
 *
 * @param id 사용자 스토리라인 ID
 * @param title 사용자 스토리라인 전체의 제목
 */
public record UserStorylineSummaryRef(UserStorylineId id, String title) {

	public UserStorylineSummaryRef {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(title, "title");
	}

}
