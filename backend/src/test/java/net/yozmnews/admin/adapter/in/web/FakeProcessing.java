package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.event.EventProcessing;
import net.yozmnews.platform.RoundQueue;
import net.yozmnews.storyline.StorylineProcessing;
import net.yozmnews.tag.TagProcessing;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 처리 현황 조회 대역 셋 — 모듈마다 돌려줄 회차를 시험이 정하고, 불린 순서를 남긴다.
 */
final class FakeProcessing {

	List<RoundQueue> events = List.of();

	List<RoundQueue> storylines = List.of();

	List<RoundQueue> tags = List.of();

	final List<String> called = new ArrayList<>();

	void reset() {
		this.events = List.of();
		this.storylines = List.of();
		this.tags = List.of();
		this.called.clear();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeProcessing fakeProcessing() {
			return new FakeProcessing();
		}

		@Bean
		EventProcessing fakeEventProcessing(FakeProcessing fake) {
			return () -> {
				fake.called.add("event");
				return fake.events;
			};
		}

		@Bean
		StorylineProcessing fakeStorylineProcessing(FakeProcessing fake) {
			return () -> {
				fake.called.add("storyline");
				return fake.storylines;
			};
		}

		@Bean
		TagProcessing fakeTagProcessing(FakeProcessing fake) {
			return () -> {
				fake.called.add("tag");
				return fake.tags;
			};
		}

	}

}
