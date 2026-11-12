/**
 * 공유 스토리라인 — 사건 변화를 따르는 잇기 대기와 회차, 소속 · 표현 · 판정 근거, 잇기 규칙, 운영자 채우기, 스토리라인 사이 관계(포함 ·
 * 이어짐)와 그 판정 회차, 공개 조회, 큰 판정과 그 기준. 기사는 ID 타입만 쓴다 — 사건의 대표 기사 ID 를 관계 판정에 넘긴다.
 * docs/specs/storyline.md.
 */
@ApplicationModule(allowedDependencies = { "event", "evaluation", "article", "platform" })
package net.yozmnews.storyline;

import org.springframework.modulith.ApplicationModule;
