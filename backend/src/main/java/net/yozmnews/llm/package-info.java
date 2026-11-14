/**
 * 사용자 LLM 키(BYOK)로 하는 모든 LLM 호출의 관문 — 공급자와 허용 모델 목록, 키 검증, 공급자 클라이언트와 실패 분류, 로컬 대체물.
 * docs/specs/llm.md.
 */
@ApplicationModule(allowedDependencies = { "platform" })
package net.yozmnews.llm;

import org.springframework.modulith.ApplicationModule;
