package net.yozmnews.user.domain.suggestion;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.tag.TagSetId;

/**
 * 저장한 구독 제안 거절 한 행 — 기록 당시 버전의 키 그대로. docs/specs/user.md §6
 *
 * @param version 기록 당시 태그 체계 버전
 * @param key 그 버전의 주제 중분류 전체 키
 * @param level 거절 때 관심 x(−1 ~ 1)
 * @param declinedAt 거절한 시각
 */
public record RecordedDecline(TagSetId version, String key, double level, Instant declinedAt) {

	public RecordedDecline {
		Objects.requireNonNull(version, "version");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(declinedAt, "declinedAt");
		if (!(level >= -1.0 && level <= 1.0)) {
			throw new IllegalArgumentException("관심은 −1 이상 1 이하여야 한다: " + level);
		}
	}

	/**
	 * 같은 활성 주제에 닿는 두 거절 가운데 남길 것 — 거절 시각이 늦은 것, 같으면 관심이 큰 것. 판단의 정본 — user-R65
	 */
	public static RecordedDecline later(RecordedDecline a, RecordedDecline b) {
		int byTime = a.declinedAt().compareTo(b.declinedAt());
		if (byTime != 0) {
			return byTime > 0 ? a : b;
		}
		return a.level() >= b.level() ? a : b;
	}

}
