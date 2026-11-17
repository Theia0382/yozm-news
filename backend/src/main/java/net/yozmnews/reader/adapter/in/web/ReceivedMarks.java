package net.yozmnews.reader.adapter.in.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleId;
import net.yozmnews.digest.ReceivedArticles;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;

/**
 * 사건마다 세션 사용자가 받은 날짜 맞붙이기 — docs/specs/reader.md §5 reader-R5.
 * <p>
 * 사건의 기사 ID 를 {@link EventDirectory#articleIdsOf} 로 한 번 얻어, 서로 다른 기사를 {@link ReceivedArticles#MAX_ARTICLES} 개씩
 * {@link ReceivedArticles#receivedAmong} 에 묻는다. 사건마다 그 기사들의 받은 날짜 가운데 가장 이른 것을 단다. 받은 소식의 뜻은 digest-R13 이다.
 */
final class ReceivedMarks {

	private final EventDirectory events;

	private final ReceivedArticles received;

	ReceivedMarks(EventDirectory events, ReceivedArticles received) {
		this.events = Objects.requireNonNull(events, "events");
		this.received = Objects.requireNonNull(received, "received");
	}

	/** 사건 → 가장 이른 받은 날짜 — 받은 기사가 없는 사건은 빠진다. 빈 입력이면 부르지 않는다 */
	Map<EventId, LocalDate> of(UserId user, Collection<EventId> events) {
		Objects.requireNonNull(user, "user");
		if (events.isEmpty()) {
			return Map.of();
		}
		Map<EventId, List<ArticleId>> articles = this.events.articleIdsOf(events);
		Set<ArticleId> distinct = new LinkedHashSet<>();
		articles.values().forEach(distinct::addAll);
		Map<ArticleId, LocalDate> dates = new HashMap<>();
		List<ArticleId> all = new ArrayList<>(distinct);
		for (int from = 0; from < all.size(); from += ReceivedArticles.MAX_ARTICLES) {
			dates.putAll(this.received.receivedAmong(user,
					all.subList(from, Math.min(from + ReceivedArticles.MAX_ARTICLES, all.size()))));
		}
		Map<EventId, LocalDate> marks = new LinkedHashMap<>();
		articles.forEach((event, ids) -> ids.stream()
			.map(dates::get)
			.filter(Objects::nonNull)
			.min(LocalDate::compareTo)
			.ifPresent(date -> marks.put(event, date)));
		return marks;
	}

}
