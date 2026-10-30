package net.yozmnews.source.domain.profile;

import org.jspecify.annotations.Nullable;

/**
 * 운영자가 소스 · 피드에 둔 수집 주기 설정 — 프로필 밖의 운영 설정이고, 정하지 않은 쪽은 null
 */
public record PollIntervalSettings(@Nullable PollInterval source, @Nullable PollInterval feed) {

	/** 운영자가 아무것도 정하지 않았다 */
	public static final PollIntervalSettings NONE = new PollIntervalSettings(null, null);

}
