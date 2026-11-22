package net.yozmnews.report.domain.view;

import java.util.List;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import net.yozmnews.report.ReportId;
import org.jspecify.annotations.Nullable;

/**
 * 리포트 조회 저장소 — 질의마다 사용자 조건이 있다. 쓰지 않는다 · docs/specs/report.md §4 · report-R11
 */
public interface ReportViewRepository {

	/**
	 * 그 사용자의 리포트 — ID 내림차순으로 {@code limit} 건, {@code before} 가 있으면 그보다 작은 ID 만
	 */
	List<ReportRow> page(UserId user, @Nullable ReportId before, int limit);

	/** 그 사용자의 리포트 한 행 — 없거나 다른 사용자의 것이면 빈 값 */
	Optional<ReportRow> find(UserId user, ReportId id);

	/** 그 사용자의 그 리포트의 섹션과 인용 — 위치 순. 다른 사용자의 리포트면 빈 목록 */
	List<StoredSection> sections(UserId user, ReportId id);

}
