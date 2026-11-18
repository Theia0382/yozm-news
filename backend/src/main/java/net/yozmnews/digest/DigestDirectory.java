package net.yozmnews.digest;

import java.time.LocalDate;
import java.util.Optional;

import net.yozmnews.platform.UserId;
import org.jspecify.annotations.Nullable;

/**
 * 다이제스트 조회 — 독자 화면이 날짜마다 보이는 다이제스트 · 가장 최근 시도 · 날짜 목록 · 수동 생성 상태를 읽는다. docs/specs/digest.md §3 ·
 * digest-R38 ~ R40.
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 다른 사용자의 다이제스트는 보지 않는다. 쓰지 않는다.
 */
public interface DigestDirectory {

	/** 날짜 목록 한 쪽의 상한 — 정본. 측정 전 목표 */
	int MAX_DATES = 100;

	/**
	 * 가장 늦은 날짜(계기 · 상태 무관)의 날짜 보기 — digest-R38.
	 * @return 다이제스트 행이 하나도 없으면 빈 값
	 */
	Optional<DigestDay> latest(UserId user);

	/**
	 * 그 날짜의 날짜 보기 — digest-R38.
	 * @return 그 날짜의 다이제스트 행이 없으면 빈 값
	 */
	Optional<DigestDay> on(UserId user, LocalDate date);

	/**
	 * 다이제스트 행이 있는 날짜를 늦은 순으로 — digest-R39.
	 * @param before 이 날짜보다 이른 날짜만 — 없으면 처음부터
	 * @param limit 1 ~ {@link #MAX_DATES}
	 * @throws IllegalArgumentException {@code limit} 이 범위 밖일 때
	 */
	DigestDates dates(UserId user, @Nullable LocalDate before, int limit);

	/**
	 * 수동 생성 상태 — digest-R40. 잠그지 않고 읽은 그 순간의 값이다 — 실제 요청의 판정은 {@link DigestRequests#request} 가 한다.
	 * @return 계정이 없으면 빈 값
	 */
	Optional<DigestRequestStatus> requestStatus(UserId user);

}
