package net.yozmnews.user.application;

import java.util.List;
import java.util.Objects;

import net.yozmnews.user.ReportScheduleDirectory;
import net.yozmnews.user.ReportScheduleEntry;
import net.yozmnews.user.domain.interest.DigestSchedule;
import net.yozmnews.user.domain.interest.ReportSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ReportScheduleDirectory} 의 구현 — 쓰는 계정마다 설정 행의 시간대 · 다이제스트 생성 시각 · 리포트 설정을 낸다(설정 행이 없으면
 * 기본값 — 읽기는 {@link ScheduleRows}). docs/specs/user.md §4 · user-R61
 * <p>
 * 백그라운드 작업 전용이고 쓰지 않는다. 역할 애너테이션이 없다.
 */
@Service
class ReportScheduleDirectoryService implements ReportScheduleDirectory {

	private final ScheduleRows rows;

	ReportScheduleDirectoryService(ScheduleRows rows) {
		this.rows = Objects.requireNonNull(rows, "rows");
	}

	@Override
	@Transactional(readOnly = true)
	public List<ReportScheduleEntry> page(long afterUserId, int limit) {
		if (limit < 1 || limit > MAX_PAGE) {
			throw new IllegalArgumentException("쪽 크기는 1 ~ " + MAX_PAGE + " 이어야 한다: " + limit);
		}
		return this.rows.page(afterUserId, limit).stream().map(row -> {
			DigestSchedule schedule = row.settings().schedule();
			ReportSettings reports = row.settings().reports();
			return new ReportScheduleEntry(row.user(), schedule.timezone(), schedule.time(), reports.weekly(),
					reports.weeklyDay(), reports.monthly(), reports.monthlyDay());
		}).toList();
	}

}
