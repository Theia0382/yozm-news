/**
 * 변화 리포트 — 생성 흐름 · 출력 서술 · 별칭 대조 · 저장 · 조회, 생성기 포트와 로컬 대체물. docs/specs/report.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "llm", "event", "storyline", "user", "tag", "digest" })
package net.yozmnews.report;

import org.springframework.modulith.ApplicationModule;
