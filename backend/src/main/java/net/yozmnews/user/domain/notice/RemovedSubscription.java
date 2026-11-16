package net.yozmnews.user.domain.notice;

import org.jspecify.annotations.Nullable;

/**
 * 사라진 구독 하나 — 사라진 구독 알림의 항목. docs/specs/user.md §4 · user-R54
 * <p>
 * 기사 정보(제목)는 담지 않는다 — 알림함을 읽을 때 사건의 대표 기사로 찾으므로, 기사가 지워지면 제목도 보이지 않는다.
 *
 * @param storylineId 사라진 스토리라인
 * @param eventId 풀기에서 남은 사건 — 알지 못하면(지우기 · 이벤트 없이 발견한 사라짐) null
 */
public record RemovedSubscription(long storylineId, @Nullable Long eventId) {
}
