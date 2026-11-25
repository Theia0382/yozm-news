package net.yozmnews.user.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.yozmnews.account.AccountDirectory;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import net.yozmnews.user.domain.interest.ScheduleSettings;
import org.springframework.stereotype.Component;

/**
 * 쓰는 계정마다의 일정 · 리포트 설정 읽기 — 다이제스트 · 리포트의 일정 목록이 함께 쓴다. docs/specs/user.md §4 · user-R61 · user-R76
 * <p>
 * 쓰는 계정의 쪽을 {@code account} 의 공개 조회로 읽고, 계정마다 설정 행의 값을 낸다 — 설정 행이 없으면 기본값
 * ({@link ScheduleSettings#DEFAULTS}). 부르는 쪽의 트랜잭션에서 읽는다. 쓰지 않는다.
 */
@Component
class ScheduleRows {

	private final AccountDirectory accounts;

	private final InterestSettingsRepository settings;

	ScheduleRows(AccountDirectory accounts, InterestSettingsRepository settings) {
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	/** 사용자 ID 가 {@code afterUserId} 보다 큰 쓰는 계정의 값 — ID 순으로 {@code limit} 개까지 */
	List<Row> page(long afterUserId, int limit) {
		List<UserId> users = this.accounts.activePage(afterUserId, limit);
		Map<UserId, ScheduleSettings> stored = this.settings.scheduleSettingsOf(users);
		return users.stream().map(user -> new Row(user, stored.getOrDefault(user, ScheduleSettings.DEFAULTS))).toList();
	}

	/**
	 * 쓰는 계정 하나의 값.
	 * @param user 사용자
	 * @param settings 설정 행의 일정 · 리포트 설정 — 행이 없으면 기본값
	 */
	record Row(UserId user, ScheduleSettings settings) {
	}

}
