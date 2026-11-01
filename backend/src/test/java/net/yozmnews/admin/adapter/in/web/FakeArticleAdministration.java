package net.yozmnews.admin.adapter.in.web;

import java.util.ArrayList;
import java.util.List;

import net.yozmnews.article.ArticleAdministration;
import net.yozmnews.article.EntryStatistics;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 기사 쪽 공개 운영 API 대역 — 돌려줄 집계를 시험이 정하고, 받은 호출을 남긴다.
 */
class FakeArticleAdministration implements ArticleAdministration {

	@Nullable EntryStatistics statistics;

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.statistics = null;
		this.calls.clear();
	}

	@Override
	public EntryStatistics entryStatistics() {
		this.calls.add("entryStatistics");
		if (this.statistics == null) {
			throw new IllegalStateException("시험이 결과를 정하지 않았다");
		}
		return this.statistics;
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
