/**
 * 태그 — 태그 체계 버전 · 첫 기동 적재 · 태그 기준 · 저장 기준과 그 변경 이력 · 태그 키 계보, 활성 체계 조회, 평가 대기 · 평가 회차(판정은
 * evaluation 의 태그 판정) · 결과 저장과 조회 시점 판정, 평가 현황 · 주제별 보도량 · 검수 · 기준 미리보기, 빠진 태그 채움.
 * docs/specs/tag.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "article", "evaluation" })
package net.yozmnews.tag;

import org.springframework.modulith.ApplicationModule;
