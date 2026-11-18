package net.yozmnews.digest.domain.view;

import java.time.LocalDate;
import java.util.List;

import net.yozmnews.digest.DigestSectionView;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 조회 저장소 — 날짜 보기 · 날짜 목록의 재료를 읽는다. 쓰지 않는다. 구현은 {@code adapter/out/persistence} 의
 * {@code JdbcDigestDayRepository}. docs/specs/digest.md §4 · digest-R38 · R39
 */
public interface DigestDayRepository {

	/** 그 사용자의 그 날짜의 다이제스트 행 전부 — ID 순, 보이는 다이제스트 표시와 함께. 없으면 빈 목록 */
	List<DigestDayRow> rowsOn(UserId user, LocalDate date);

	/** 그 다이제스트의 섹션과 인용 — 섹션은 위치 순, 인용은 위치 순 */
	List<DigestSectionView> sectionsOf(long digestId);

	/** 그 사용자의 다이제스트 행이 있는 날짜 가운데 그 날짜의 바로 앞 · 뒤 */
	DigestNeighbors neighbors(UserId user, LocalDate date);

	/**
	 * 그 사용자의 다이제스트 행이 있는 날짜를 늦은 순으로 {@code limit} 개까지.
	 * @param before 이 날짜보다 이른 날짜만 — 없으면 처음부터
	 */
	List<DigestDateRow> dates(UserId user, @Nullable LocalDate before, int limit);

}
