package net.yozmnews.user.application;

import java.util.List;
import java.util.Objects;

import net.yozmnews.user.DigestScheduleDirectory;
import net.yozmnews.user.DigestScheduleEntry;
import net.yozmnews.user.domain.interest.DigestSchedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DigestScheduleDirectory} 의 구현 — 쓰는 계정마다 설정 행의 시간대 · 생성 시각을 낸다(설정 행이 없으면 기본 일정 — 읽기는
 * {@link ScheduleRows}). docs/specs/user.md §4 · user-R61
 * <p>
 * 백그라운드 작업 전용이고 쓰지 않는다. 역할 애너테이션이 없다.
 */
@Service
class DigestScheduleDirectoryService implements DigestScheduleDirectory {

	private final ScheduleRows rows;

	DigestScheduleDirectoryService(ScheduleRows rows) {
		this.rows = Objects.requireNonNull(rows, "rows");
	}

	@Override
	@Transactional(readOnly = true)
	public List<DigestScheduleEntry> page(long afterUserId, int limit) {
		if (limit < 1 || limit > MAX_PAGE) {
			throw new IllegalArgumentException("쪽 크기는 1 ~ " + MAX_PAGE + " 이어야 한다: " + limit);
		}
		return this.rows.page(afterUserId, limit).stream().map(row -> {
			DigestSchedule schedule = row.settings().schedule();
			return new DigestScheduleEntry(row.user(), schedule.timezone(), schedule.time());
		}).toList();
	}

}
