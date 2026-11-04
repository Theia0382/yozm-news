package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.source.FeedId;
import net.yozmnews.source.FeedLabel;
import net.yozmnews.source.FeedLabels;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 피드 이름 · 섹션 조회 대역 — 시험이 넣은 피드만 있고, 받은 호출을 순서대로 남긴다.
 */
class FakeFeedLabels implements FeedLabels {

	final Map<FeedId, FeedLabel> labels = new LinkedHashMap<>();

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.labels.clear();
		this.calls.clear();
	}

	@Override
	public Map<FeedId, FeedLabel> labels(Collection<FeedId> feeds) {
		this.calls.add("labels " + feeds.stream().map(FeedId::value).toList());
		Map<FeedId, FeedLabel> found = new LinkedHashMap<>();
		feeds.forEach(feed -> {
			FeedLabel label = this.labels.get(feed);
			if (label != null) {
				found.put(feed, label);
			}
		});
		return found;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeFeedLabels fakeFeedLabels() {
			return new FakeFeedLabels();
		}

	}

}
