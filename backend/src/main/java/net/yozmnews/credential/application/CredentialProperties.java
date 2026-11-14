package net.yozmnews.credential.application;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 키 보관 설정 — docs/specs/credential.md §7 · docs/operations/configuration.md "LLM 키 암호화".
 *
 * @param kekFile KEK 파일의 경로 — 없으면 기동하지 않는다
 */
@ConfigurationProperties(CredentialProperties.PREFIX)
record CredentialProperties(@Nullable String kekFile) {

	/** 설정 키의 접두 */
	static final String PREFIX = "yozmnews.credential";

}
