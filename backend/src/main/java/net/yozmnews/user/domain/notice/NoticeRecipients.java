package net.yozmnews.user.domain.notice;

import java.util.List;

import net.yozmnews.platform.UserId;

/**
 * 피드 상태 알림을 받는 사람의 조회 — 사용자 범위가 없는 조회라 리스너만 부른다(화면 · API 경로에 두지 않는다). 구현은
 * {@code adapter/out/persistence} 의 {@code JdbcNoticeRecipients}. docs/specs/user.md §4 · user-R22
 */
public interface NoticeRecipients {

	/** 지금 쓰는(ACTIVE) 운영자(ADMIN) 계정 — 계정 ID 순 */
	List<UserId> activeAdmins();

}
