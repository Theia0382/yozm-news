package net.yozmnews.user.domain.notice;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedStatusTransition;
import net.yozmnews.source.FeedStatusesChanged;
import org.jspecify.annotations.Nullable;

/**
 * 피드 상태 알림의 내용 — 피드마다 한 줄, 피드 ID 순. 모으기 규칙의 정본이다. docs/specs/user.md §4 · user-R23
 * <p>
 * 이미 있는 피드는 마지막 새 상태 · 마지막 판정 시각을 바꾸고 횟수를 하나 올린다(이름 · 섹션은 이번에 얻었으면 바꾸고, 얻지 못했으면 이전
 * 값을 둔다). 없는 피드는 새 줄이다. 부르는 쪽은 반영 기준으로 이미 걸렀다 — 판정 시각이 모든 줄의 마지막 판정 시각보다 늦지 않으면
 * 거부한다.
 *
 * @param feeds 피드마다 한 줄 — 비지 않고, 피드 ID 순이며 같은 피드가 두 번 나오지 않는다
 */
public record FeedStatusContent(List<FeedStatusEntry> feeds) implements NoticeContent {

	public FeedStatusContent {
		feeds = List.copyOf(feeds);
		if (feeds.isEmpty()) {
			throw new IllegalArgumentException("피드가 없다");
		}
		for (int i = 1; i < feeds.size(); i++) {
			if (feeds.get(i - 1).feedId().value() >= feeds.get(i).feedId().value()) {
				throw new IllegalArgumentException("피드 ID 순이 아니거나 같은 피드가 두 번 나온다: " + feeds.get(i).feedId());
			}
		}
	}

	/**
	 * 새 알림의 내용.
	 * @param labels 피드의 출처 이름 · 섹션 — 없는 피드는 이름 없이 남긴다
	 */
	public static FeedStatusContent first(FeedStatusesChanged changed, Map<FeedId, FeedLabel> labels) {
		return apply(new TreeMap<>(), changed, labels);
	}

	/**
	 * 이 내용에 회차 하나의 전이를 더한 내용.
	 * @param labels 피드의 출처 이름 · 섹션 — 없는 피드의 이미 있는 줄은 이전 이름을 둔다
	 * @throws IllegalArgumentException 판정 시각이 어느 줄의 마지막 판정 시각보다 늦지 않을 때
	 */
	public FeedStatusContent merge(FeedStatusesChanged changed, Map<FeedId, FeedLabel> labels) {
		TreeMap<Long, FeedStatusEntry> byFeed = new TreeMap<>();
		for (FeedStatusEntry entry : this.feeds) {
			if (!changed.judgedAt().isAfter(entry.lastAt())) {
				throw new IllegalArgumentException(
						"반영한 회차보다 늦지 않은 판정 시각이다: " + changed.judgedAt() + " ≤ " + entry.lastAt());
			}
			byFeed.put(entry.feedId().value(), entry);
		}
		return apply(byFeed, changed, labels);
	}

	/** 회차의 전이를 줄들에 더한다 — {@code byFeed} 를 고친다 */
	private static FeedStatusContent apply(TreeMap<Long, FeedStatusEntry> byFeed, FeedStatusesChanged changed,
			Map<FeedId, FeedLabel> labels) {
		Instant at = changed.judgedAt();
		Set<Long> seen = new HashSet<>();
		for (FeedStatusTransition transition : changed.transitions()) {
			long key = transition.feedId().value();
			if (!seen.add(key)) {
				throw new IllegalArgumentException("한 회차에 같은 피드가 두 번 나온다: " + transition.feedId());
			}
			@Nullable FeedLabel label = labels.get(transition.feedId());
			@Nullable FeedStatusEntry previous = byFeed.get(key);
			FeedStatusEntry next;
			if (previous == null) {
				next = new FeedStatusEntry(transition.feedId(), (label != null) ? label.sourceName() : null,
						(label != null) ? label.section() : null, transition.from(), transition.to(), at, at, 1);
			}
			else {
				next = new FeedStatusEntry(previous.feedId(), (label != null) ? label.sourceName() : previous.sourceName(),
						(label != null) ? label.section() : previous.section(), previous.firstFrom(), transition.to(),
						previous.firstAt(), at, previous.changes() + 1);
			}
			byFeed.put(key, next);
		}
		return new FeedStatusContent(List.copyOf(byFeed.values()));
	}

}
