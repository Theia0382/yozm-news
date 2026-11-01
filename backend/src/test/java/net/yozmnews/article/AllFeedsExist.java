package net.yozmnews.article;

import net.yozmnews.source.SourceDirectory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * article 모듈 하나만 띄운 시험의 {@link SourceDirectory} 대역 — 모든 피드가 있다고 답한다. 피드 확인과 source 의 실제 결선은
 * {@code ArticleIntakeTests} · {@code CollectionWithArticleIntakeTests} 가 본다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AllFeedsExist {

	@Bean
	SourceDirectory allFeedsExist() {
		return feed -> true;
	}

}
