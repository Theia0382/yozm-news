package net.yozmnews.feedback;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.platform.UserId;

/**
 * 좋아요 / 싫어요의 소비자 조회 — 다이제스트 후보 선별 · 관심도가 읽는다. docs/specs/feedback.md §3 · feedback-R8
 * <p>
 * 사용자를 인자로 받는 조회다 — 화면 경로에서는 세션 사용자로만 부른다. 기간을 사용자 시간대의 날로 자르는 일과 주제로 모으는 일은 부르는 쪽이
 * 한다.
 */
public interface ArticleFeedback {

	/** 한 번에 묻는 서로 다른 기사 수의 상한 — 정본. 측정 전 목표 */
	int MAX_ARTICLES = 1000;

	/**
	 * 주어진 기사 가운데 그 사용자의 현재 상태가 싫어요인 기사를 읽는다. 같은 기사가 두 번 와도 한 번이고, 빈 입력이면 빈 집합이다.
	 * @throws IllegalArgumentException 서로 다른 기사가 {@link #MAX_ARTICLES} 개를 넘을 때
	 */
	Set<ArticleId> dislikedAmong(UserId user, Collection<ArticleId> articles);

	/**
	 * 그 사용자의 이력 행 가운데 시각이 {@code from} 이상 · {@code to} 미만인 것을 기록한 순서(행 ID 순)로 읽는다. 같은 (사용자, 기사)의 마지막
	 * 행의 값이 현재 상태와 같다 — 시각 순으로 다시 정렬하면 이 성질이 깨질 수 있다.
	 * @throws IllegalArgumentException {@code from} 이 {@code to} 보다 늦을 때
	 */
	List<FeedbackChange> changes(UserId user, Instant from, Instant to);

}
