/**
 * 공유 사건 묶기 — 사건 묶기 대기와 기사 표현(임베딩 · 핵심 말)의 계산 · 저장, 사건 배정, 사건 조회와 사건 묶음의 기간 집계. docs/specs/event.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "article", "evaluation", "tag", "source" })
package net.yozmnews.event;

import org.springframework.modulith.ApplicationModule;
