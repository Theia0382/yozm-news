package net.yozmnews.user.domain.userstoryline;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * 저장한 사용자 스토리라인 하나 · docs/specs/user.md §5.
 *
 * @param id 사용자 스토리라인 ID
 * @param tops 마지막으로 맞춘 추적 대상 — 지금 꼭대기로 푼 것
 * @param trackedSeeds 추적 씨앗 스토리라인 → 그 지금 꼭대기. 키는 사용자가 누른(또는 갈라질 때 물려받은) 스토리라인의 지금 ID 이고 꼭대기가
 *        아닐 수 있다. 지워진 스토리라인은 없다
 * @param subscribedAt 추적을 시작한 시각 — 추적 중이 아니면 null. 추적 중이면 추적 씨앗이 하나 이상이어야 하지만, 추적 씨앗의 스토리라인이
 *        모두 지워진 개체는 씨앗 없이 올 수 있다(맞추기가 고친다)
 * @param createdAt 만든 시각
 */
public record StoredUserStoryline(long id, Set<Long> tops, Map<Long, Long> trackedSeeds, @Nullable Instant subscribedAt,
		Instant createdAt) {

	/**
	 * @throws IllegalArgumentException 추적 중이 아닌데 추적 씨앗이 있을 때
	 */
	public StoredUserStoryline {
		tops = Set.copyOf(Objects.requireNonNull(tops, "tops"));
		trackedSeeds = Map.copyOf(Objects.requireNonNull(trackedSeeds, "trackedSeeds"));
		Objects.requireNonNull(createdAt, "createdAt");
		if (subscribedAt == null && !trackedSeeds.isEmpty()) {
			throw new IllegalArgumentException("추적 중이 아닌 사용자 스토리라인에 추적 씨앗이 있다: " + id);
		}
	}

	/** 추적 중인가 */
	public boolean tracked() {
		return this.subscribedAt != null;
	}

}
