package net.yozmnews.account;

import java.util.List;

import net.yozmnews.platform.UserId;

/**
 * 계정 공개 조회 — 다른 모듈이 계정의 사실을 읽는 길이다. 구현은 {@code application} 의 {@code AccountDirectoryService}.
 * docs/specs/account.md §3 · account-R37
 */
public interface AccountDirectory {

	/** 한 쪽의 최대 계정 수 */
	int MAX_PAGE = 500;

	/** 계정이 있는가 — 상태와 무관하다 */
	boolean exists(UserId user);

	/** 쓰는({@code ACTIVE}) 계정인가 — 없으면 거짓 */
	boolean active(UserId user);

	/** 쓰는 운영자({@code ADMIN} · {@code ACTIVE}) 계정 전부 — 계정 ID 순. 사용자 범위가 없어 백그라운드 작업 · 리스너만 부른다 */
	List<UserId> activeAdmins();

	/**
	 * 계정 ID 가 {@code afterUserId} 보다 큰 계정 — ID 순으로 {@code limit} 개까지, 상태와 무관하다. 사용자 범위가 없어 백그라운드 작업 ·
	 * 리스너만 부른다.
	 * @throws IllegalArgumentException {@code limit} 이 1 ~ {@link #MAX_PAGE} 밖일 때
	 */
	List<UserId> page(long afterUserId, int limit);

	/**
	 * {@link #page} 와 같되 쓰는 계정만.
	 * @throws IllegalArgumentException {@code limit} 이 1 ~ {@link #MAX_PAGE} 밖일 때
	 */
	List<UserId> activePage(long afterUserId, int limit);

}
