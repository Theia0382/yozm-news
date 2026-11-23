package net.yozmnews.digest.domain.item;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.SelectionReason;
import net.yozmnews.digest.domain.selection.ArticleFacts;
import net.yozmnews.digest.domain.selection.CandidateEvent;
import net.yozmnews.digest.domain.selection.EventFacts;
import net.yozmnews.digest.domain.selection.SelectedEvent;
import net.yozmnews.digest.domain.selection.UserFilter;
import net.yozmnews.event.EventId;
import net.yozmnews.source.SourceId;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.tag.TopicStatus;
import net.yozmnews.user.TopicSubscription;
import org.jspecify.annotations.Nullable;

/**
 * 항목 시험의 재료 — 손으로 적은 고른 사건과 구독 행. 사건마다 기사 하나(기사 ID = 사건 ID × 10, 발행 2026-11-16T12:00Z)를 가지고, 주제를
 * 적지 않으면 정치 행정이다.
 */
final class Items {

	/** 정치 행정 */
	static final String POLITICS_EXECUTIVE = "politics.executive";

	/** 정치 대분류 하나를 구독한 필터 — 주제를 적지 않은 사건을 덮는다 */
	static final UserFilter POLITICS = filter(row("politics", POLITICS_EXECUTIVE));

	private Items() {
	}

	/** 구독 사건 — 사건 중요도 {@code importance}(없으면 null) */
	static SelectedEvent subscribed(long id, @Nullable Double importance) {
		return subscribed(id, importance, POLITICS_EXECUTIVE);
	}

	/** 구독 사건 — 중요도 3 */
	static SelectedEvent subscribed(long id) {
		return subscribed(id, 3.0);
	}

	/** 구독 사건 — 주제 중분류 {@code subtopic}, 사건 중요도 {@code importance}(없으면 null) */
	static SelectedEvent subscribed(long id, @Nullable Double importance, String subtopic) {
		return new SelectedEvent(candidate(id, importance, subtopic), SelectionReason.SUBSCRIBED, null);
	}

	/** 주제 구독 밖 관심 흐름의 길로 든 사건 — 주제 중분류 {@code subtopic}, 사건 중요도 {@code importance} */
	static SelectedEvent followed(long id, double importance, String subtopic) {
		return new SelectedEvent(candidate(id, importance, subtopic), SelectionReason.INTEREST, null);
	}

	/** 큰 슬롯 사건 — 큰 판정 행 {@code row}, 중요도 3 */
	static SelectedEvent big(long id, int row) {
		return big(id, row, POLITICS_EXECUTIVE);
	}

	/** 큰 슬롯 사건 — 큰 판정 행 {@code row}, 주제 중분류 {@code subtopic}, 중요도 3 */
	static SelectedEvent big(long id, int row, String subtopic) {
		return new SelectedEvent(candidate(id, 3.0, subtopic), SelectionReason.MAJOR_STORYLINE, row);
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

	/** 항목의 사건 ID — 순서대로 */
	static List<Long> ids(DigestItemPlan item) {
		return item.events().stream().map(event -> event.event().facts().id().value()).toList();
	}

	/** 구독 행 하나 — 키 {@code key} 와 그 행이 덮는 중분류 */
	static TopicSubscription row(String key, String... subtopics) {
		return new TopicSubscription(key, Set.of(subtopics));
	}

	/** 구독 행만 있고 제외는 없는 사용자 필터 — 행은 적은 순서(키 순으로 적는다) */
	static UserFilter filter(TopicSubscription... rows) {
		return new UserFilter(List.of(rows), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
	}

	private static CandidateEvent candidate(long id, @Nullable Double importance, String subtopic) {
		EventFacts facts = new EventFacts(new EventId(id), Set.of(subtopic), Set.of("domestic"), importance, false);
		ArticleFacts article = new ArticleFacts(new ArticleId(id * 10), Set.of(subtopic), Set.of("domestic"),
				Set.of("straight"), TopicStatus.TAGGED, importance, Set.of(new SourceId(1)),
				Instant.parse("2026-11-16T12:00:00Z"));
		return new CandidateEvent(facts, List.of(), List.of(article));
	}

}
