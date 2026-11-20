package net.yozmnews.feedback.application;

import java.util.Map;
import java.util.Objects;

import net.yozmnews.event.EventId;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.feedback.domain.record.ItemRecordRepository;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면의 항목 기록 조회 — 세션 사용자의 것. docs/specs/feedback.md §4 · feedback-R7
 */
@Service
public class FeedbackStates {

	private final ItemRecordRepository itemRecords;

	FeedbackStates(ItemRecordRepository itemRecords) {
		this.itemRecords = Objects.requireNonNull(itemRecords, "itemRecords");
	}

	/**
	 * 그 사용자가 그 다이제스트의 항목에 누른 값을 항목 키 순으로 읽는다. 누른 값이 없는 항목은 빠진다. 다이제스트 하나의 항목 수만큼이라 상한이
	 * 없다.
	 * @return 항목 키(그 항목의 첫 사건) → 좋아요 · 싫어요
	 */
	@Transactional(readOnly = true)
	public Map<EventId, FeedbackValue> itemsOf(UserId user, long digestId) {
		Objects.requireNonNull(user, "user");
		return this.itemRecords.valuesOf(user, digestId);
	}

}
