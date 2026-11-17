package net.yozmnews.article;

import java.util.Map;

import net.yozmnews.source.FeedLabels;
import net.yozmnews.source.SourceDirectory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * article 모듈 하나만 띄운 시험의 source 대역 — {@link SourceDirectory} 는 모든 피드가 있다고, {@link FeedLabels} 는 이름이 있는 피드가
 * 없다고 답한다. 피드 확인과 source 의 실제 결선은 {@code ArticleIntakeTests} · {@code CollectionWithArticleIntakeTests} 가, 헤드라인의
 * 출처 이름 엮기는 {@code ArticleDirectoryServiceTests} 가 본다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AllFeedsExist {

	@Bean
	SourceDirectory allFeedsExist() {
		return feed -> true;
	}

	@Bean
	FeedLabels noFeedLabels() {
		return feeds -> Map.of();
	}

}
