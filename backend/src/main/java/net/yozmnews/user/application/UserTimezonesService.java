package net.yozmnews.user.application;

import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.user.UserTimezones;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.interest.InterestDefaults;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserTimezones} 의 구현 — 계정 유무와 설정 행을 읽기 전용 트랜잭션 하나에서 읽는다. 쓰지 않는다. docs/specs/user.md §4 · user-R60
 */
@Service
class UserTimezonesService implements UserTimezones {

	private final AccountRepository accounts;

	private final InterestSettingsRepository settings;

	UserTimezonesService(AccountRepository accounts, InterestSettingsRepository settings) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ZoneId> timezoneOf(UserId user) {
		Objects.requireNonNull(user, "user");
		if (!this.accounts.existsById(user.value())) {
			return Optional.empty();
		}
		// 설정 행이 없으면 계정을 만들 때의 값 — InterestDefaultsWriter 와 같은 정본
		return Optional.of(this.settings.findByUser(user)
			.map(row -> row.schedule().timezone())
			.orElse(InterestDefaults.TIMEZONE));
	}

}
