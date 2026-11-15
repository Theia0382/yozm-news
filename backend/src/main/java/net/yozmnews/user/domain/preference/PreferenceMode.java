package net.yozmnews.user.domain.preference;

import net.yozmnews.tag.TagKind;

/**
 * 태그 선호의 모드 — 태그 종류마다 하나다 — docs/specs/user.md §5 · user-R45
 */
public enum PreferenceMode {

	/** 구독 — 주제 */
	SUBSCRIBE,

	/** 제외 — 지역 · 형식 */
	EXCLUDE;

	/** 그 태그 종류의 모드 — 종류와 모드의 조합의 정본 */
	public static PreferenceMode of(TagKind kind) {
		return switch (kind) {
			case TOPIC -> SUBSCRIBE;
			case REGION, FORMAT -> EXCLUDE;
		};
	}

}
