package net.yozmnews.user.domain.userstoryline;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 저장된 사용자 스토리라인 행 — 저장한 그대로의 값이다. 스토리라인 ID 는 저장한 때의 것이라 합쳐졌거나 지워졌을 수 있다 — 지금 값으로 푼 것은
 * {@link StoredUserStoryline} 이다. docs/specs/user.md §6
 *
 * @param id 사용자 스토리라인 ID
 * @param createdAt 만든 시각
 * @param subscribedAt 추적을 시작한 시각(구독 행) — 추적 중이 아니면 null
 * @param members 추적 대상으로 저장한 스토리라인 ID
 * @param seeds 추적 씨앗으로 저장한 스토리라인 ID
 * @param head 저장한 헤드 스토리라인 ID — 헤드 열이 생기기 전에 저장된 개체는 null
 */
public record UserStorylineRow(long id, Instant createdAt, @Nullable Instant subscribedAt, Set<Long> members,
		Set<Long> seeds, @Nullable Long head) {

	public UserStorylineRow {
		Objects.requireNonNull(createdAt, "createdAt");
		members = Set.copyOf(Objects.requireNonNull(members, "members"));
		seeds = Set.copyOf(Objects.requireNonNull(seeds, "seeds"));
	}

}
