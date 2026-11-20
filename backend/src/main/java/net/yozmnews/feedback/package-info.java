/**
 * 좋아요 / 싫어요 — 다이제스트 항목에 누른 값(항목 기록)과 그 항목의 사건마다의 현재 상태 · 추가 전용 이력, 받을 수 있는 항목의 판정, 지운
 * 사건 · 지운 항목 따르기, 누르기 · 되돌리기 · 항목 기록의 화면용 API, 소비자 조회. docs/specs/feedback.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "event" })
package net.yozmnews.feedback;

import org.springframework.modulith.ApplicationModule;
