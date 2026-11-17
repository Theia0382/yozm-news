/**
 * 좋아요 / 싫어요 — 기사에 남기는 값의 현재 상태와 추가 전용 이력, 받을 수 있는 기사의 판정, 지운 기사 따르기, 누르기 · 되돌리기 · 현재 상태의
 * 화면용 API, 소비자 조회. docs/specs/feedback.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "article" })
package net.yozmnews.feedback;

import org.springframework.modulith.ApplicationModule;
