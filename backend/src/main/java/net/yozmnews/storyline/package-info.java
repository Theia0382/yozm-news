/**
 * 공유 스토리라인 — 사건 변화를 따르는 잇기 대기와 회차, 소속 · 표현 · 판정 근거, 잇기 규칙, 운영자 채우기.
 * docs/specs/storyline.md.
 */
@ApplicationModule(allowedDependencies = { "event", "platform" })
package net.yozmnews.storyline;

import org.springframework.modulith.ApplicationModule;
