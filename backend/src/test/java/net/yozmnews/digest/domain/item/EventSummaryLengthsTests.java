package net.yozmnews.digest.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.domain.selection.ArticleFacts;
import net.yozmnews.event.EventId;
import net.yozmnews.source.SourceId;
import net.yozmnews.tag.TopicStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * 사건의 요약문 길이 — 표본 기사 가운데 내용을 읽은 기사의 RSS 요약문 코드 포인트 합. 제목은 세지 않고, 요약문이 없는 기사 · 내용을 읽지 못한
 * 기사는 0 이다. 스프링 없이 돈다.
 * <p>
 * 지키는 규칙: digest-R47
 */
class EventSummaryLengthsTests {

	private static final Instant PUBLISHED = Instant.parse("2026-11-16T12:00:00Z");

	@Test
	void theSummaryLengthIsTheCodePointSumOfTheReadSampleSummaries() {
		// 사건 1: 요약문 "가나다"(3) · "😀가"(코드 포인트 2 — UTF-16 길이는 3) · 요약문 없음, 제목은 모두 "제목"(세지 않는다 — 세면 11)
		// 사건 2: 표본 기사의 내용을 읽지 못함 — 0
		Map<EventId, List<ArticleFacts>> samples = Map.of(new EventId(1),
				List.of(facts(11), facts(12), facts(13)), new EventId(2), List.of(facts(21)));
		Map<ArticleId, ArticleContent> contents = Map.of(new ArticleId(11), content(11, "가나다"), new ArticleId(12),
				content(12, "😀가"), new ArticleId(13), content(13, null));

		assertThat(EventSummaryLengths.of(samples, contents))
			.containsExactlyInAnyOrderEntriesOf(Map.of(new EventId(1), 5, new EventId(2), 0));
	}

	private static ArticleFacts facts(long id) {
		return new ArticleFacts(new ArticleId(id), Set.of("politics.executive"), Set.of("domestic"),
				Set.of("straight"), TopicStatus.TAGGED, 3.0, Set.of(new SourceId(1)), PUBLISHED);
	}

	private static ArticleContent content(long id, @Nullable String summary) {
		return new ArticleContent(new ArticleId(id), "제목", summary, URI.create("https://example.com/" + id),
				PUBLISHED, List.of());
	}

}
