package net.yozmnews.event.domain.intake;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import org.jspecify.annotations.Nullable;

/**
 * 사건 묶기 대기 행 하나와 그 전이 — docs/specs/event.md §4 · event-R6.
 *
 * @param enqueuedAt 대기에 넣은 시각 — 회차가 이 순서로 점유한다
 * @param lease 회차의 점유 — 없으면 null
 */
public record PendingArticle(ArticleId articleId, Instant enqueuedAt, @Nullable IntakeLease lease) {

	public PendingArticle {
		Objects.requireNonNull(articleId, "articleId");
		Objects.requireNonNull(enqueuedAt, "enqueuedAt");
	}

	/** 회차가 점유한다 — 만료는 {@code now} + {@link IntakeSchedule#LEASE} */
	public PendingArticle claim(UUID token, Instant now) {
		return new PendingArticle(this.articleId, this.enqueuedAt, new IntakeLease(token, now.plus(IntakeSchedule.LEASE)));
	}

}
