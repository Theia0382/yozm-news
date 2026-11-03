package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.article.ArticleAdministration;
import net.yozmnews.article.EntryStatistics;
import net.yozmnews.source.FeedId;
import net.yozmnews.source.SourceId;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 기사 쪽 공개 운영 API 대역 — 돌려줄 집계를 시험이 정하고, 받은 호출을 남긴다.
 */
class FakeArticleAdministration implements ArticleAdministration {

	@Nullable EntryStatistics statistics;

	long exclusiveArticles;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.statistics = null;
		this.exclusiveArticles = 0;
		this.calls.clear();
	}

	@Override
	public EntryStatistics entryStatistics() {
		this.calls.add("entryStatistics");
		return FakeResults.given(this.statistics);
	}

	@Override
	public long exclusiveArticleCount(SourceId sourceId) {
		this.calls.add("exclusiveArticleCount " + sourceId.value());
		return this.exclusiveArticles;
	}

	@Override
	public long exclusiveArticleCount(SourceId sourceId, FeedId feedId) {
		this.calls.add("exclusiveArticleCount " + sourceId.value() + " " + feedId.value());
		return this.exclusiveArticles;
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeArticleAdministration fakeArticleAdministration() {
			return new FakeArticleAdministration();
		}

	}

}
