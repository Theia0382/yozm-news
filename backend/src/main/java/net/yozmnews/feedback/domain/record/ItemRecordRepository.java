package net.yozmnews.feedback.domain.record;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.platform.UserId;

/**
 * 항목 기록의 저장소 — 다이제스트 항목마다 그 사용자가 누른 값. 항목은 (다이제스트, 항목 키 = 첫 사건)으로 가리킨다. docs/specs/feedback.md
 * §4 · feedback-R1 · feedback-R7
 */
public interface ItemRecordRepository {

	/**
	 * 그 항목의 기록을 {@code value} 로 바꾼다 — 한 문장이다. 없음이면 행을 지우고, 좋아요 · 싫어요면 행을 넣거나 값이 다를 때만 고친다. 같은
	 * 항목의 동시 요청은 그 행의 잠금에서 차례를 선다.
	 * @return 값이 바뀌었으면 참
	 */
	boolean change(UserId user, long digestId, EventId itemKey, FeedbackValue value, Instant now);

	/**
	 * 그 사용자의 그 다이제스트의 항목 기록.
	 * @return 항목 키 → 값(좋아요 · 싫어요) — 항목 키 순
	 */
	Map<EventId, FeedbackValue> valuesOf(UserId user, long digestId);

	/** 주어진 다이제스트마다 그 항목 키의 기록을 모든 사용자에 대해 지운다 */
	void removeAll(Map<Long, ? extends Collection<EventId>> itemKeysByDigest);

}
