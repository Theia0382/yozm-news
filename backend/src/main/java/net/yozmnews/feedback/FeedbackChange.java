package net.yozmnews.feedback;

import java.time.Instant;
import java.util.Objects;

import net.yozmnews.article.ArticleId;

/**
 * 이력 행 하나 — 소비자 조회 {@link ArticleFeedback} 의 값. docs/specs/feedback.md §3 · feedback-R1
 *
 * @param article 값을 남긴 기사
 * @param value 바뀐 뒤의 값 — 없음으로 되돌린 행은 {@link FeedbackValue#NONE}
 * @param digestId 누른 다이제스트의 ID({@code digest} 의 다이제스트 ID)
 * @param occurredAt 요청을 받은 시각
 */
public record FeedbackChange(ArticleId article, FeedbackValue value, long digestId, Instant occurredAt) {

	public FeedbackChange {
		Objects.requireNonNull(article, "article");
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(occurredAt, "occurredAt");
		if (digestId < 1) {
			throw new IllegalArgumentException("다이제스트 ID 는 1 이상이어야 한다: " + digestId);
		}
	}

}
