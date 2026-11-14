package net.yozmnews.llm.application;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 설정 — docs/specs/llm.md §7 · docs/operations/configuration.md "허용 모델 목록".
 *
 * @param modelsFile 허용 모델 목록의 운영자 파일 경로 — 없으면 앱에 든 기본 파일
 */
@ConfigurationProperties(LlmProperties.PREFIX)
record LlmProperties(@Nullable String modelsFile) {

	/** 설정 키의 접두 */
	static final String PREFIX = "yozmnews.llm";

}
