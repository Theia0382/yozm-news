package net.yozmnews.feedback.domain.record;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleId;
import net.yozmnews.feedback.FeedbackChange;
import net.yozmnews.feedback.FeedbackValue;
import net.yozmnews.platform.UserId;

/**
 * 현재 상태와 이력의 저장소 — docs/specs/feedback.md §4 · feedback-R1 · feedback-R3 ~ feedback-R5.
 */
public interface FeedbackRepository {

	/**
	 * 현재 상태를 {@code value} 로 바꾼다 — 한 문장이다. 없음이면 행을 지우고, 좋아요 · 싫어요면 행을 넣거나 값이 다를 때만 고친다(값이 같으면 누른
	 * 다이제스트 · 시각도 그대로다). 같은 (사용자, 기사)의 동시 요청은 그 행의 잠금에서 차례를 선다.
	 * @return 값이 바뀌었으면 참 — 행이 생겼거나 값이 달라졌거나 지워졌다
	 */
	boolean change(UserId user, ArticleId article, FeedbackValue value, long digestId, Instant now);

	/** 이력 한 줄을 더한다 */
	void append(UserId user, ArticleId article, FeedbackValue value, long digestId, Instant now);

	/**
	 * 주어진 기사 가운데 그 사용자의 현재 상태가 있는 기사와 그 값을 읽는다.
	 * @return 기사 ID 순 — 값은 {@link FeedbackValue#LIKE} · {@link FeedbackValue#DISLIKE}. 빈 입력이면 빈 맵
	 */
	Map<ArticleId, FeedbackValue> currentAmong(UserId user, Collection<ArticleId> articles);

	/** 그 사용자의 이력 행 가운데 시각이 {@code from} 이상 · {@code to} 미만인 것을 행 ID 순으로 읽는다 */
	List<FeedbackChange> changes(UserId user, Instant from, Instant to);

	/** 그 기사들의 현재 상태 · 이력 행을 모두 지운다 */
	void removeAll(Collection<ArticleId> articles);

}
