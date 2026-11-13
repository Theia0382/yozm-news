package net.yozmnews.user.domain.notice;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedStatus;
import org.jspecify.annotations.Nullable;

/**
 * 피드 상태 알림의 피드 한 줄 — 알림에 모은 그 피드의 전이. docs/specs/user.md §4 · user-R23
 *
 * @param feedId 피드
 * @param sourceName 출처 이름 — 남길 때 얻지 못했으면 null
 * @param section 피드 섹션 — 남길 때 얻지 못했으면 null
 * @param firstFrom 알림에 모은 첫 전이의 이전 상태
 * @param lastTo 알림에 모은 마지막 전이의 새 상태
 * @param firstAt 첫 전이의 판정 시각
 * @param lastAt 마지막 전이의 판정 시각
 * @param changes 모은 전이 수 — 1 이상
 */
public record FeedStatusEntry(FeedId feedId, @Nullable String sourceName, @Nullable String section,
		FeedStatus firstFrom, FeedStatus lastTo, Instant firstAt, Instant lastAt, int changes) {

	public FeedStatusEntry {
		Objects.requireNonNull(feedId, "feedId");
		Objects.requireNonNull(firstFrom, "firstFrom");
		Objects.requireNonNull(lastTo, "lastTo");
		Objects.requireNonNull(firstAt, "firstAt");
		Objects.requireNonNull(lastAt, "lastAt");
		if (changes < 1) {
			throw new IllegalArgumentException("모은 전이가 없다: " + changes);
		}
		if (lastAt.isBefore(firstAt)) {
			throw new IllegalArgumentException("마지막 전이가 첫 전이보다 이르다: " + firstAt + " > " + lastAt);
		}
		if (changes == 1 && firstFrom == lastTo) {
			throw new IllegalArgumentException("전이 하나인데 상태가 그대로다: " + firstFrom);
		}
	}

}
