package net.yozmnews.source.domain.registry;

import java.util.Objects;

import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedKey;

/**
 * 운영자 원문 수정의 판정 — 무엇이 켜진 채 남는가. docs/specs/source.md §4 · source-R50.
 * <p>
 * 관문은 켜는 순간에만 판정하므로, 관문이 본 읽기 설정이나 이용조건 검토일이 바뀐 피드 · 소스는 끄고 다시 켜게 한다.
 */
public final class ProfileRevision {

	private ProfileRevision() {
	}

	/**
	 * 수정 뒤에도 켜진 채 남을 수 있는가 — 옛 · 새 프로필 모두에 그 피드가 있고 읽기 설정이 같을 때만. 옛 프로필을 얻지 못했으면(옛 원문이
	 * 검증에 걸림) 견줄 것이 없어 남지 못한다.
	 * @param before 수정 전 원문으로 얻은 프로필
	 * @param after 수정한 원문의 프로필
	 */
	public static boolean keepsEnabled(ProfileResolution before, CollectionProfile after, FeedKey key) {
		Objects.requireNonNull(after, "after");
		Objects.requireNonNull(key, "key");
		return switch (before) {
			case ProfileResolution.Resolved resolved -> resolved.profile().reading(key).isPresent()
					&& resolved.profile().reading(key).equals(after.reading(key));
			case ProfileResolution.PresetMissing missing -> false;
			case ProfileResolution.Invalid invalid -> false;
		};
	}

	/** 수정 뒤 소스가 켜진 채 남을 수 있는가 — 새 프로필에 이용조건 검토일이 있을 때만(source-R4) */
	public static boolean keepsSourceEnabled(CollectionProfile after) {
		return after.termsReviewedAt().isPresent();
	}

}
