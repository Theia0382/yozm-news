package net.yozmnews.event.domain.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import net.yozmnews.article.ArticleId;
import org.junit.jupiter.api.Test;

/**
 * 대기 행의 점유 — 토큰을 쓰고 만료는 지금 + 10분, 넣은 시각은 그대로.
 * <p>
 * 지키는 규칙: event-R6
 */
class PendingArticleTests {

	private static final UUID TOKEN = UUID.fromString("00000000-0000-4000-8000-000000000001");

	@Test
	void aClaimLeasesTheRowForTenMinutes() {
		PendingArticle pending = new PendingArticle(new ArticleId(7), Instant.parse("2026-11-05T00:00:00Z"), null);

		PendingArticle claimed = pending.claim(TOKEN, Instant.parse("2026-11-05T01:00:00Z"));

		assertThat(claimed).isEqualTo(new PendingArticle(new ArticleId(7), Instant.parse("2026-11-05T00:00:00Z"),
				new IntakeLease(TOKEN, Instant.parse("2026-11-05T01:10:00Z"))));
	}

}
