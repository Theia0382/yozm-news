package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.platform.UserId;
import net.yozmnews.tag.TagSetDirectory;
import net.yozmnews.tag.TagSetView;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import net.yozmnews.user.domain.interest.PreferenceVersionFence;
import net.yozmnews.user.domain.preference.PreferenceCause;
import net.yozmnews.user.domain.preference.PreferenceSet;
import net.yozmnews.user.domain.preference.TagPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관심 설정 기본값 쓰기의 정본 — 계정을 만드는 정본 · 기존 계정 채우기 · 관심 설정 유스케이스가 부른다 — docs/specs/user.md §4 · user-R43
 * <p>
 * 선호 버전 울타리의 공유 잠금 → 그 순간의 활성 태그 체계 → 설정 행(기본값 · 선호 버전 = 활성 버전)과 활성 버전에서 기본 제외인 형식 전부의
 * 제외(이력 원인 {@code ONBOARDING}). 설정 행이 이미 있으면 아무것도 쓰지 않는다. 코드는 특정 형식 키를 알지 않는다 — 기본 제외는 체계 데이터다.
 */
@Service
class InterestDefaultsWriter {

	private final InterestSettingsRepository settings;

	private final TagPreferenceRepository preferences;

	private final PreferenceVersionFence fence;

	private final TagSetDirectory tagSets;

	InterestDefaultsWriter(InterestSettingsRepository settings, TagPreferenceRepository preferences,
			PreferenceVersionFence fence, TagSetDirectory tagSets) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.preferences = Objects.requireNonNull(preferences, "preferences");
		this.fence = Objects.requireNonNull(fence, "fence");
		this.tagSets = Objects.requireNonNull(tagSets, "tagSets");
	}

	/**
	 * 설정 행이 없으면 기본값을 쓴다 — 부르는 쪽의 트랜잭션 안에서만.
	 * @return 썼는가 — 설정 행이 이미 있었으면 거짓
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	boolean createFor(UserId user, Instant now) {
		this.fence.enterShared();
		TagSetView active = this.tagSets.activeTagSet();
		if (!this.settings.insertIfAbsent(InterestSettings.defaults(user, active.id(), now))) {
			return false;
		}
		this.preferences.apply(user, PreferenceSet.empty().changesTo(PreferenceSet.onboarding(active)),
				PreferenceCause.ONBOARDING, active.id(), now);
		return true;
	}

}
