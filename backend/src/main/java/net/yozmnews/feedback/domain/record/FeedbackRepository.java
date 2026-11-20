package net.yozmnews.feedback.domain.record;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.platform.UserId;

/**
 * 사건마다의 현재 상태와 이력의 저장소 — docs/specs/feedback.md §4 · feedback-R1 · feedback-R3 ~ feedback-R5.
 */
public interface FeedbackRepository {

	/**
	 * 그 사건의 현재 상태를 {@code value} 로 바꾼다 — 한 문장이다. 없음이면 행을 지우고, 좋아요 · 싫어요면 행을 넣거나 값이 다를 때만
	 * 고친다(값이 같으면 누른 다이제스트 · 시각도 그대로다). 같은 (사용자, 사건)의 동시 요청은 그 행의 잠금에서 차례를 선다.
	 * @return 값이 바뀌었으면 참 — 행이 생겼거나 값이 달라졌거나 지워졌다
	 */
	boolean change(UserId user, EventId event, FeedbackValue value, long digestId, Instant now);

	/** 그 사건의 이력 한 줄을 더한다 */
	void append(UserId user, EventId event, FeedbackValue value, long digestId, Instant now);

	/**
	 * 주어진 사건마다 그 사용자의 마지막 이력 행(행 ID 가 가장 큰 것)을 읽는다 — 그 값은 그 사건의 현재 상태와 같다(없음 포함).
	 * @return 사건 ID 순 — 이력이 없는 사건은 빠진다. 빈 입력이면 빈 목록
	 */
	List<LastChange> lastChanges(UserId user, Collection<EventId> events);

	/** 그 사용자의 이력 행 가운데 시각이 {@code from} 이상 · {@code to} 미만인 것을 행 ID 순으로 읽는다 */
	List<HistoryRow> history(UserId user, Instant from, Instant to);

	/** 그 사건들의 현재 상태 · 이력 행을 모든 사용자에 대해 지운다 */
	void removeAll(Collection<EventId> events);

}
