package net.yozmnews.user.application;

import java.util.List;
import java.util.Objects;

import net.yozmnews.user.DigestScheduleDirectory;
import net.yozmnews.user.DigestScheduleEntry;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DigestScheduleDirectory} 의 구현 — 쓰는 계정의 설정 행에서 시간대 · 생성 시각을 쪽으로 읽는다. docs/specs/user.md §4
 * <p>
 * 백그라운드 작업 전용이고 쓰지 않는다. 역할 애너테이션이 없다.
 */
@Service
class DigestScheduleDirectoryService implements DigestScheduleDirectory {

	private final InterestSettingsRepository settings;

	DigestScheduleDirectoryService(InterestSettingsRepository settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	@Override
	@Transactional(readOnly = true)
	public List<DigestScheduleEntry> page(long afterUserId, int limit) {
		if (limit < 1 || limit > MAX_PAGE) {
			throw new IllegalArgumentException("쪽 크기는 1 ~ " + MAX_PAGE + " 이어야 한다: " + limit);
		}
		return this.settings.activeSchedules(afterUserId, limit)
			.stream()
			.map(row -> new DigestScheduleEntry(row.user(), row.schedule().timezone(), row.schedule().time()))
			.toList();
	}

}
