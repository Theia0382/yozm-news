package net.yozmnews.feedback;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;

/**
 * 좋아요 / 싫어요의 소비자 조회 — 사건마다의 기록을 다이제스트 후보 선별 · 관심도가 읽는다. docs/specs/feedback.md §3 · feedback-R8
 * <p>
 * 기록은 누른 때의 사건 ID 로 남고, 이 조회가 읽을 때 {@code event} 로 합쳐진 사건을 푼다. 사용자를 인자로 받는 조회다 — 화면 경로에서는
 * 세션 사용자로만 부른다. 기간을 사용자 시간대의 날로 자르는 일과 주제로 모으는 일은 부르는 쪽이 한다.
 */
public interface EventFeedback {

	/** 한 번에 묻는 서로 다른 사건 수의 상한 — 정본. 측정 전 목표 */
	int MAX_EVENTS = 1000;

	/**
	 * 주어진 (지금) 사건 가운데 그 사용자의 값이 싫어요인 사건을 읽는다. 사건마다 그 사건과 그 사건으로 합쳐진 사건의 이력을 모아 행 ID 가 가장
	 * 큰 행(마지막으로 누른 값)의 값으로 정한다 — 그 행이 없음(되돌리기)이면 싫어요가 아니다. 같은 사건이 두 번 와도 한 번이고, 빈 입력이면 빈
	 * 집합이다.
	 * @throws IllegalArgumentException 서로 다른 사건이 {@link #MAX_EVENTS} 개를 넘을 때
	 */
	Set<EventId> dislikedAmong(UserId user, Collection<EventId> events);

	/**
	 * 그 사용자의 이력 행 가운데 시각이 {@code from} 이상 · {@code to} 미만인 것을 기록한 순서(행 ID 순)로 읽는다. 행마다 기록한 사건과
	 * 지금 사건(합쳐졌으면 남는 사건, 지웠으면 없음)을 싣는다. 같은 (사용자, 기록한 사건)의 마지막 행의 값이 그 사건의 현재 상태와 같다 — 시각
	 * 순으로 다시 정렬하면 이 성질이 깨질 수 있다.
	 * @throws IllegalArgumentException {@code from} 이 {@code to} 보다 늦을 때
	 */
	List<FeedbackChange> changes(UserId user, Instant from, Instant to);

}
