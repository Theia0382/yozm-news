/**
 * 사용자 LLM 키(BYOK)의 암호화 보관 — 봉투 암호와 KEK, 키 저장, 재암호화. docs/specs/credential.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "llm" })
package net.yozmnews.credential;

import org.springframework.modulith.ApplicationModule;
