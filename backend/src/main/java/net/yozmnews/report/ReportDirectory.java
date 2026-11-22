package net.yozmnews.report;

import java.util.Optional;

import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;

/**
 * 리포트 조회 — 사용자의 리포트 목록과 한 편 · docs/specs/report.md §3 · report-R11 · report-R12
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 쓰지 않는다.
 */
public interface ReportDirectory {

	/** 목록 한 쪽의 상한 — 정본. 측정 전 목표 */
	int MAX_PAGE_SIZE = 50;

	/**
	 * 그 사용자의 리포트 — ID 내림차순으로 {@code size} 건, {@code before} 가 있으면 그보다 작은 ID 만
	 * @throws IllegalArgumentException {@code size} 가 1 ~ {@link #MAX_PAGE_SIZE} 밖일 때
	 */
	ReportPage list(UserId user, @Nullable ReportId before, int size);

	/**
	 * 그 사용자의 리포트 한 편 — 없거나 다른 사용자의 리포트면 빈 값
	 */
	Optional<ReportView> find(UserId user, ReportId id);

}
