package net.yozmnews.digest.domain.item;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.selection.ArticleFacts;
import net.yozmnews.digest.domain.selection.CandidateEvent;
import net.yozmnews.digest.domain.selection.EventFacts;
import net.yozmnews.digest.domain.selection.SelectedEvent;
import net.yozmnews.event.EventId;
import net.yozmnews.source.SourceId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TopicStatus;
import org.jspecify.annotations.Nullable;

/**
 * 항목 시험의 재료 — 손으로 적은 고른 사건. 사건마다 정치 행정 기사 하나(기사 ID = 사건 ID × 10, 발행 2026-11-16T12:00Z)를 가진다.
 */
final class Items {

	private Items() {
	}

	/** 구독 사건 — 사건 중요도 {@code importance}(없으면 null) */
	static SelectedEvent subscribed(long id, @Nullable Double importance) {
		return new SelectedEvent(candidate(id, importance), SelectionReason.SUBSCRIBED, null);
	}

	/** 구독 사건 — 중요도 3 */
	static SelectedEvent subscribed(long id) {
		return subscribed(id, 3.0);
	}

	/** 큰 슬롯 사건 — 큰 판정 행 {@code row}, 중요도 3 */
	static SelectedEvent big(long id, int row) {
		return new SelectedEvent(candidate(id, 3.0), SelectionReason.MAJOR_STORYLINE, row);
	}

	static StorylineId storyline(long id) {
		return new StorylineId(id);
	}

	/** 같은 고른 사건에 꼭대기를 실은 것 */
	static SelectedEvent withTops(SelectedEvent event, List<StorylineId> tops) {
		CandidateEvent candidate = event.event();
		return new SelectedEvent(new CandidateEvent(candidate.facts(), tops, candidate.articles()), event.reason(),
				event.bigRow());
	}

	/** 항목들의 모든 사건에 같은 요약문 길이 — 바꿀 수 있는 지도 */
	static Map<EventId, Integer> summaryLengths(int length, List<DigestItemPlan> items) {
		Map<EventId, Integer> lengths = new HashMap<>();
		items.forEach(item -> item.events().forEach(event -> lengths.put(event.event().facts().id(), length)));
		return lengths;
	}

	/** 항목의 사건 ID — 순서대로 */
	static List<Long> ids(DigestItemPlan item) {
		return item.events().stream().map(event -> event.event().facts().id().value()).toList();
	}

	private static CandidateEvent candidate(long id, @Nullable Double importance) {
		EventFacts facts = new EventFacts(new EventId(id), Set.of("politics.executive"), Set.of("domestic"), importance,
				false);
		ArticleFacts article = new ArticleFacts(new ArticleId(id * 10), Set.of("politics.executive"),
				Set.of("domestic"), Set.of("straight"), TopicStatus.TAGGED, importance, Set.of(new SourceId(1)),
				Instant.parse("2026-11-16T12:00:00Z"));
		return new CandidateEvent(facts, List.of(), List.of(article));
	}

}
