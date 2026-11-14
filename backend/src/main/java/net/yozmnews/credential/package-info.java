/**
 * 사용자 LLM 키(BYOK)의 암호화 보관 — 봉투 암호와 KEK, 키 저장, 재암호화, 상태와 무효 알림, 저장한 키의 공급. docs/specs/credential.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "llm", "llm :: stored-key" })
package net.yozmnews.credential;

import org.springframework.modulith.ApplicationModule;
