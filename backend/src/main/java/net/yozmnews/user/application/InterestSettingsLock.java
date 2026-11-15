package net.yozmnews.user.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.interest.InterestSettings;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관심 설정 조회 · 저장의 앞 단계 정본 — 설정 행이 없으면 만들고, 잠그고, 선호 버전이 지금 활성 버전과 다르면 먼저 따라가게 한다 —
 * docs/specs/user.md §5 · user-R46
 * <p>
 * 그래서 활성 전환이 커밋된 뒤 리스너가 그 사용자에 닿기 전에도 조회 · 저장은 활성 키만 다룬다. 같은 사용자의 조회 · 저장 · 따라가기는 이 잠금으로
 * 줄을 선다.
 */
@Component
class InterestSettingsLock {

	private final InterestSettingsRepository settings;

	private final InterestDefaultsWriter defaults;

	private final PreferenceFollowups followups;

	InterestSettingsLock(InterestSettingsRepository settings, InterestDefaultsWriter defaults,
			PreferenceFollowups followups) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.defaults = Objects.requireNonNull(defaults, "defaults");
		this.followups = Objects.requireNonNull(followups, "followups");
	}

	/**
	 * 부르는 쪽의 트랜잭션 안에서만.
	 * @return 잠갔고 지금 활성 버전으로 따라간 설정 행
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	InterestSettings lockFollowed(UserId user, Instant now) {
		Optional<InterestSettings> locked = this.settings.lockByUser(user);
		if (locked.isEmpty()) {
			this.defaults.createFor(user, now);
			locked = this.settings.lockByUser(user);
		}
		return this.followups.followLocked(locked.orElseThrow(), now);
	}

}
