package net.yozmnews.admin.adapter.in.web;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.storyline.StorylineId;
import net.yozmnews.storyline.StorylineLinkingCriteria;
import net.yozmnews.storyline.StorylineMembership;
import net.yozmnews.storyline.StorylineRelationAsked;
import net.yozmnews.storyline.StorylineRelationCriteria;
import net.yozmnews.storyline.StorylineRelationDetail;
import net.yozmnews.storyline.StorylineReview;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 스토리라인 운영자 조회 대역 — 스토리라인 ID 마다 낼 소속 근거 · 관계 · 물은 기록과 지금 기준을 시험이 정하고, 받은 호출을
 * 순서대로 남긴다.
 */
class FakeStorylineReview implements StorylineReview {

	static final StorylineLinkingCriteria LINKING = new StorylineLinkingCriteria(Duration.ofDays(3), 0.62);

	static final StorylineRelationCriteria RELATION = new StorylineRelationCriteria(0.6, 0.2, 3, 0.88,
			Duration.ofDays(14));

	final Map<Long, List<StorylineMembership>> memberships = new LinkedHashMap<>();

	final Map<Long, List<StorylineRelationDetail>> relations = new LinkedHashMap<>();

	final Map<Long, List<StorylineRelationAsked>> asked = new LinkedHashMap<>();

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.memberships.clear();
		this.relations.clear();
		this.asked.clear();
		this.calls.clear();
	}

	@Override
	public List<StorylineMembership> memberships(StorylineId id) {
		this.calls.add("memberships " + id.value());
		return this.memberships.getOrDefault(id.value(), List.of());
	}

	@Override
	public List<StorylineRelationDetail> relations(StorylineId id) {
		this.calls.add("relations " + id.value());
		return this.relations.getOrDefault(id.value(), List.of());
	}

	@Override
	public List<StorylineRelationAsked> asked(StorylineId id) {
		this.calls.add("asked " + id.value());
		return this.asked.getOrDefault(id.value(), List.of());
	}

	@Override
	public StorylineLinkingCriteria linkingCriteria() {
		return LINKING;
	}

	@Override
	public StorylineRelationCriteria relationCriteria() {
		return RELATION;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeStorylineReview fakeStorylineReview() {
			return new FakeStorylineReview();
		}

	}

}
