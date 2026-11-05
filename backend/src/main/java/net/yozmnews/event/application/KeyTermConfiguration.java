package net.yozmnews.event.application;

import net.yozmnews.event.domain.feature.KeyTermExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 핵심 말 추출기의 결선 — 컨텍스트가 닫을 때 분석기를 닫는다 · docs/specs/event.md §4
 */
@Configuration(proxyBeanMethods = false)
class KeyTermConfiguration {

	@Bean(destroyMethod = "close")
	KeyTermExtractor keyTermExtractor() {
		return new KeyTermExtractor();
	}

}
