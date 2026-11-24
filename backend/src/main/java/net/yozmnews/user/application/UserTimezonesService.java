package net.yozmnews.user.application;

import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.account.AccountDirectory;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestScheduleEntry;
import net.yozmnews.user.UserDigestSchedules;
import net.yozmnews.user.UserTimezones;
import net.yozmnews.user.domain.interest.InterestDefaults;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserTimezones} · {@link UserDigestSchedules} 의 구현 — 계정 유무와 설정 행을 읽기 전용 트랜잭션 하나에서 읽는다. 쓰지 않는다.
 * docs/specs/user.md §4 · user-R60
 * <p>
 * 두 조회가 한 클래스인 것은 시간대가 일정({@link #scheduleOf})의 시간대이기 때문이다. 설정 행이 없을 때의 일정은 기본 일정
 * {@code InterestDefaults.SCHEDULE} 하나다 — 일정 목록({@code DigestScheduleDirectoryService})과 같은 정본이다.
 */
@Service
class UserTimezonesService implements UserTimezones, UserDigestSchedules {

	private final AccountDirectory accounts;

	private final InterestSettingsRepository settings;

	UserTimezonesService(AccountDirectory accounts, InterestSettingsRepository settings) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ZoneId> timezoneOf(UserId user) {
		return scheduleOf(user).map(DigestScheduleEntry::timezone);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<DigestScheduleEntry> scheduleOf(UserId user) {
		Objects.requireNonNull(user, "user");
		if (!this.accounts.exists(user)) {
			return Optional.empty();
		}
		// 설정 행이 없으면 기본 일정 — 일정 목록과 같은 정본
		return Optional.of(this.settings.findByUser(user)
			.map(row -> new DigestScheduleEntry(user, row.schedule().timezone(), row.schedule().time()))
			.orElse(new DigestScheduleEntry(user, InterestDefaults.SCHEDULE.timezone(), InterestDefaults.SCHEDULE.time())));
	}

}
