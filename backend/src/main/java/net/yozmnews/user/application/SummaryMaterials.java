package net.yozmnews.user.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import net.yozmnews.event.EventDirectory;
import net.yozmnews.event.EventId;
import net.yozmnews.event.EventView;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineEvent;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.user.domain.summary.SummaryEvent;
import org.springframework.stereotype.Component;

/**
 * 정리의 재료 읽기 — 추적 대상 꼭대기의 사건과 그 대표 기사의 제목 · 요약문을 다른 모듈의 공개 조회로 읽는다. docs/specs/user.md §4
 * <p>
 * 꼭대기의 사건은 {@code storyline} 의 타임라인(하위 스토리라인의 사건까지), 대표 기사는 {@code event}, 제목 · 요약문은 {@code article} 이
 * 정한다. 대표 기사가 없거나 그새 지운 사건은 뺀다. 트랜잭션 없이 조회를 차례로 부른다 — 한 스냅숏이 아니다.
 */
@Component
class SummaryMaterials {

	private final StorylineDirectory storylines;

	private final EventDirectory events;

	private final ArticleDirectory articles;

	SummaryMaterials(StorylineDirectory storylines, EventDirectory events, ArticleDirectory articles) {
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.events = Objects.requireNonNull(events, "events");
		this.articles = Objects.requireNonNull(articles, "articles");
	}

	/**
	 * @param tops 읽을 꼭대기(스토리라인 ID)
	 * @return 꼭대기 → 그 꼭대기의 사건(첫 기사 시각 · 사건 ID 순). 받은 꼭대기는 모두 키로 있다 — 사건이 없으면 빈 목록
	 */
	Map<Long, List<SummaryEvent>> read(Collection<Long> tops) {
		Map<Long, List<EventView>> timelines = new HashMap<>();
		Set<EventId> eventIds = new HashSet<>();
		for (Long top : new HashSet<>(tops)) {
			List<EventView> timeline = this.storylines.events(new StorylineId(top))
				.stream()
				.map(StorylineEvent::event)
				.toList();
			timelines.put(top, timeline);
			timeline.forEach(event -> eventIds.add(event.id()));
		}
		Map<EventId, ArticleId> representatives = this.events.representativesOfAll(eventIds);
		Map<ArticleId, ArticleContent> contents = this.articles.findAll(new HashSet<>(representatives.values()));
		Map<Long, List<SummaryEvent>> read = new HashMap<>();
		timelines.forEach((top, timeline) -> {
			List<SummaryEvent> lines = new ArrayList<>();
			for (EventView event : timeline) {
				ArticleId representative = representatives.get(event.id());
				ArticleContent content = (representative == null) ? null : contents.get(representative);
				if (content != null) {
					lines.add(new SummaryEvent(event.id().value(), representative.value(), event.firstArticleAt(),
							content.title(), content.summary()));
				}
			}
			read.put(top, List.copyOf(lines));
		});
		return Map.copyOf(read);
	}

}
