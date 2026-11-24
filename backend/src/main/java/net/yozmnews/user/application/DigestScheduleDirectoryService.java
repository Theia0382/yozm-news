package net.yozmnews.user.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.account.AccountDirectory;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.DigestScheduleDirectory;
import net.yozmnews.user.DigestScheduleEntry;
import net.yozmnews.user.domain.interest.DigestSchedule;
import net.yozmnews.user.domain.interest.InterestDefaults;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DigestScheduleDirectory} 의 구현 — 쓰는 계정의 쪽을 {@code account} 의 공개 조회로 읽고, 계정마다 설정 행의 시간대 · 생성 시각을
 * 낸다(설정 행이 없으면 기본 일정). docs/specs/user.md §4 · user-R61
 * <p>
 * 백그라운드 작업 전용이고 쓰지 않는다. 역할 애너테이션이 없다.
 */
@Service
class DigestScheduleDirectoryService implements DigestScheduleDirectory {

	private final AccountDirectory accounts;

	private final InterestSettingsRepository settings;

	DigestScheduleDirectoryService(AccountDirectory accounts, InterestSettingsRepository settings) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	@Override
	@Transactional(readOnly = true)
	public List<DigestScheduleEntry> page(long afterUserId, int limit) {
		if (limit < 1 || limit > MAX_PAGE) {
			throw new IllegalArgumentException("쪽 크기는 1 ~ " + MAX_PAGE + " 이어야 한다: " + limit);
		}
		List<UserId> users = this.accounts.activePage(afterUserId, limit);
		Map<UserId, DigestSchedule> stored = this.settings.schedulesOf(users);
		return users.stream().map(user -> {
			DigestSchedule schedule = stored.getOrDefault(user, InterestDefaults.SCHEDULE);
			return new DigestScheduleEntry(user, schedule.timezone(), schedule.time());
		}).toList();
	}

}
