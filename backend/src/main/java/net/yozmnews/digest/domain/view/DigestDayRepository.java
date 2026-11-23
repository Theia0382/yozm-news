package net.yozmnews.digest.domain.view;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import net.yozmnews.digest.DigestItemCursor;
import net.yozmnews.digest.DigestItemRef;
import net.yozmnews.digest.DigestSectionView;
import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 조회 저장소 — 날짜 보기 · 날짜 목록 · 수동 생성 상태의 재료를 읽는다. 쓰지 않는다. 구현은 {@code adapter/out/persistence} 의
 * {@code JdbcDigestDayRepository}. docs/specs/digest.md §4 · digest-R38 ~ R40
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

	/** 그 사용자의 가장 최근 실패 — ID 가 가장 큰 {@code FAILED} 행. 없으면 빈 값 */
	Optional<FailedAttempt> latestFailed(UserId user);

	/** 그 사용자의 기간을 덮는 행({@code READY} · {@code EMPTY} — digest-R30) 가운데 가장 큰 ID. 없으면 빈 값 */
	OptionalLong latestCoveringId(UserId user);

	/** 그 사용자의 날짜별로 보이는 다이제스트의 항목 섹션이 저장한 꼭대기(스토리라인 ID) — 서로 다른 것, ID 순 */
	List<Long> storedStorylines(UserId user);

	/**
	 * 그 사용자의 날짜별로 보이는 다이제스트의 항목 섹션 가운데 저장한 꼭대기가 주어진 것에 드는 섹션 — 날짜 내림차순, 같은 날짜면 섹션 위치
	 * 내림차순으로 {@code limit} 개까지.
	 * @param storedStorylines 저장한 꼭대기(스토리라인 ID) — 비면 빈 목록
	 * @param before 이 커서보다 뒤의 섹션만 — 없으면 처음부터
	 */
	List<DigestItemRef> itemsOf(UserId user, Collection<Long> storedStorylines, @Nullable DigestItemCursor before,
			int limit);

}
