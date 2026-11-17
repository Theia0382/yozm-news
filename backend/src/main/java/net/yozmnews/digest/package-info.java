/**
 * 사용자별 다이제스트 — 생성 · 출력 검증 · 저장, 지운 기사 따르기. docs/specs/digest.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "article", "llm", "feedback" })
package net.yozmnews.digest;

import org.springframework.modulith.ApplicationModule;
