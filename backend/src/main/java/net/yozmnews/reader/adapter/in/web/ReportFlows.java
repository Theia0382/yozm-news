package net.yozmnews.reader.adapter.in.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.yozmnews.event.EventId;
import net.yozmnews.platform.UserId;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.user.UserStorylineSummaryRef;

/**
 * 변화 리포트가 가리키는 흐름 · 사건의 표시 이름 맞붙이기 — docs/specs/reader.md §5 reader-R16.
 * <p>
 * 흐름(인용 · 숫자 줄의 지금 스토리라인)은 그 포함 나무의 지금 꼭대기({@link StorylineDirectory#topsOfStorylines})를 가진 정리된 사용자
 * 스토리라인의 제목 · ID 와, 그 스토리라인의 대표 사건 대표 기사를 단다 — 정리가 없으면 화면은 대표 기사 제목과 공용 타임라인으로 잇는다. 사건은
 * 대표 기사를 단다. 한 응답에 각 조회를 한 번 부른다(상한으로 나누는 것 밖) — 대표 기사는 흐름과 사건을 모아 한 번에 읽는다.
 */
final class ReportFlows {

	private final StorylineDirectory storylines;

	private final UserStorylineRefs refs;

	private final StorylineTitles titles;

	ReportFlows(StorylineDirectory storylines, UserStorylineRefs refs, StorylineTitles titles) {
		this.storylines = Objects.requireNonNull(storylines, "storylines");
		this.refs = Objects.requireNonNull(refs, "refs");
		this.titles = Objects.requireNonNull(titles, "titles");
	}

	/**
	 * @param flows 지금 스토리라인 ID 들 — 그새 없어진 스토리라인은 대표 기사 · 정리가 없는 이름으로 낸다
	 * @param events 사건 ID 들
	 */
	Named of(UserId user, Collection<StorylineId> flows, Collection<EventId> events) {
		List<StorylineId> distinct = flows.stream().distinct().toList();
		Map<StorylineId, StorylineView> views = distinct.isEmpty() ? Map.of() : this.storylines.findAll(distinct);
		Map<StorylineId, StorylineId> tops = topsOf(distinct);
		Map<StorylineId, List<UserStorylineSummaryRef>> summaries = this.refs.of(user,
				List.copyOf(new LinkedHashSet<>(tops.values())));
		Set<EventId> represented = new LinkedHashSet<>(events);
		views.values().forEach(view -> represented.add(view.representativeEvent()));
		Map<EventId, NewsArticleResponse> articles = this.titles.of(represented);

		Map<StorylineId, ReportFlowResponse> named = new LinkedHashMap<>();
		for (StorylineId id : distinct) {
			StorylineView view = views.get(id);
			StorylineId top = tops.get(id);
			named.put(id, new ReportFlowResponse(id.value(),
					(view == null) ? null : articles.get(view.representativeEvent()),
					((top == null) ? List.<UserStorylineSummaryRef>of() : summaries.getOrDefault(top, List.of()))
						.stream()
						.map(UserStorylineResponses.RefResponse::of)
						.toList()));
		}
		Map<EventId, NewsArticleResponse> eventArticles = new LinkedHashMap<>();
		events.forEach(event -> {
			NewsArticleResponse article = articles.get(event);
			if (article != null) {
				eventArticles.put(event, article);
			}
		});
		return new Named(named, eventArticles);
	}

	/** 스토리라인마다 지금 꼭대기 — {@link StorylineDirectory#MAX_TOP_STORYLINES} 씩 묻는다. 없어진 스토리라인은 빠진다 */
	private Map<StorylineId, StorylineId> topsOf(List<StorylineId> ids) {
		Map<StorylineId, StorylineId> tops = new LinkedHashMap<>();
		List<StorylineId> all = new ArrayList<>(ids);
		for (int from = 0; from < all.size(); from += StorylineDirectory.MAX_TOP_STORYLINES) {
			int to = Math.min(all.size(), from + StorylineDirectory.MAX_TOP_STORYLINES);
			tops.putAll(this.storylines.topsOfStorylines(all.subList(from, to)));
		}
		return tops;
	}

	/**
	 * 맞붙인 이름
	 * @param flows 스토리라인 ID → 흐름의 표시
	 * @param events 사건 ID → 대표 기사 — 대표가 없거나 기사가 지워진 사건은 빠진다
	 */
	record Named(Map<StorylineId, ReportFlowResponse> flows, Map<EventId, NewsArticleResponse> events) {
	}

}
