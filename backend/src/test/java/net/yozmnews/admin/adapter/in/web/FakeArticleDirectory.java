package net.yozmnews.admin.adapter.in.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.yozmnews.article.ArticleContent;
import net.yozmnews.article.ArticleDirectory;
import net.yozmnews.article.ArticleId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 운영자 API 시험의 기사 조회 대역 — 시험이 넣은 기사만 있고, 받은 호출을 순서대로 남긴다.
 */
class FakeArticleDirectory implements ArticleDirectory {

	final Map<ArticleId, ArticleContent> contents = new LinkedHashMap<>();

	final List<String> calls = new ArrayList<>();

	void reset() {
		this.contents.clear();
		this.calls.clear();
	}

	@Override
	public Map<ArticleId, ArticleContent> findAll(Collection<ArticleId> ids) {
		this.calls.add("findAll " + ids.stream().map(ArticleId::value).toList());
		Map<ArticleId, ArticleContent> found = new LinkedHashMap<>();
		ids.forEach(id -> {
			ArticleContent content = this.contents.get(id);
			if (content != null) {
				found.put(id, content);
			}
		});
		return found;
	}

	@Override
	public List<ArticleId> firstSeenSince(Instant since) {
		throw new UnsupportedOperationException("운영자 컨트롤러는 부르지 않는다");
	}

	/** 운영자 API 슬라이스에 대역을 싣는다 */
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {

		@Bean
		FakeArticleDirectory fakeArticleDirectory() {
			return new FakeArticleDirectory();
		}

	}

}
