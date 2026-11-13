package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.yozmnews.event.EventId;
import net.yozmnews.event.EventPeriod;
import net.yozmnews.storyline.BigStoryline;
import net.yozmnews.storyline.EventStoryline;
import net.yozmnews.storyline.StorylineCursor;
import net.yozmnews.storyline.StorylineDirectory;
import net.yozmnews.storyline.StorylineEvent;
import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylinePage;
import net.yozmnews.storyline.StorylineRelationView;
import net.yozmnews.storyline.StorylineView;
import net.yozmnews.storyline.UnlinkedEvents;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 스토리라인 조회 대역 — 다음에 돌려줄 큰 스토리라인 · 소속 없는 사건 · 목록 쪽 · 스토리라인 · 타임라인 · 사건의 스토리라인을
 * 시험이 정하고, 받은 호출을 순서대로 남긴다.
 */
class FakeStorylineDirectory implements StorylineDirectory {

	List<BigStoryline> bigStorylines = List.of();

	UnlinkedEvents unlinked = new UnlinkedEvents(0, 0);

	StorylinePage page = new StorylinePage(List.of(), null);

	/** 찾는 ID → 낼 스토리라인(합쳐진 ID 면 남는 스토리라인) */
	final Map<Long, StorylineView> storylines = new LinkedHashMap<>();

	final Map<Long, List<StorylineEvent>> timelines = new LinkedHashMap<>();

	final Map<Long, List<EventStoryline>> eventStorylines = new LinkedHashMap<>();

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.bigStorylines = List.of();
		this.unlinked = new UnlinkedEvents(0, 0);
		this.page = new StorylinePage(List.of(), null);
		this.storylines.clear();
		this.timelines.clear();
		this.eventStorylines.clear();
		this.calls.clear();
	}

	@Override
	public Optional<StorylineView> find(StorylineId id) {
		this.calls.add("find " + id.value());
		return Optional.ofNullable(this.storylines.get(id.value()));
	}

	@Override
	public List<StorylineEvent> events(StorylineId id) {
		this.calls.add("events " + id.value());
		return this.timelines.getOrDefault(id.value(), List.of());
	}

	@Override
	public List<EventStoryline> storylinesOf(EventId event) {
		this.calls.add("storylinesOf " + event.value());
		return this.eventStorylines.getOrDefault(event.value(), List.of());
	}

	@Override
	public List<StorylineRelationView> relationsOf(StorylineId id) {
		throw new UnsupportedOperationException("운영자 API 는 스토리라인의 관계를 읽지 않는다");
	}

	@Override
	public StorylinePage list(EventPeriod period, @Nullable StorylineCursor after, int size) {
		this.calls.add("list " + period.from() + " ~ " + period.to() + " "
				+ ((after == null) ? "-" : after.lastArticleAt() + "/" + after.id().value()) + " " + size);
		return this.page;
	}

	@Override
	public List<BigStoryline> bigStorylines(EventPeriod period) {
		this.calls.add("bigStorylines " + period.from() + " ~ " + period.to());
		return this.bigStorylines;
	}

	@Override
	public UnlinkedEvents unlinked(EventPeriod period) {
		this.calls.add("unlinked " + period.from() + " ~ " + period.to());
		return this.unlinked;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeStorylineDirectory fakeStorylineDirectory() {
			return new FakeStorylineDirectory();
		}

	}

}
