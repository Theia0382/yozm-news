/**
 * 공유 사건 묶기 — 사건 묶기 대기와 기사 표현(임베딩 · 핵심 말)의 계산 · 저장, 사건 배정. docs/specs/event.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "article", "evaluation" })
package net.yozmnews.event;

import org.springframework.modulith.ApplicationModule;
