/**
 * 계정과 그 인증 — 계정 · 자체 로그인 자격, 보안 구성(운영자 · 세션 · 기본 체인)과 운영자 접근 통제, 세션 · 세션 사용자 범위, 로그인 · 실패
 * 지연, 화면 내 알림(피드 상태 변화는 {@code source} 의 이벤트로 받는다). docs/specs/user.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "source" })
package net.yozmnews.user;

import org.springframework.modulith.ApplicationModule;
