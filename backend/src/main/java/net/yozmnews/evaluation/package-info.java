/**
 * 태그 체계와 공유 평가 — 태그 체계 버전 · 첫 기동 적재 · 태그 기준 · 저장 기준과 그 변경 이력, 활성 체계 조회, 평가 대기 · 평가 작업 ·
 * 결과 저장과 조회. docs/specs/evaluation.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "article", "source" })
package net.yozmnews.evaluation;

import org.springframework.modulith.ApplicationModule;
