/**
 * 계정과 그 인증 — 계정 · 자체 로그인 자격, 계정을 만드는 길과 가입 정책 · 초대, 비밀번호 변경과 복구 코드, 보안 구성(운영자 · 세션 · 기본
 * 체인)과 운영자 접근 통제, 세션 · 세션 사용자 범위, 로그인 · 실패 지연, 처리기 앞의 요청 방어(표시는 {@code platform} 의 것), LLM 키가 없는
 * 세션 사용자의 키 관문(키 유무는 {@code credential} 에 묻는다), 계정의 사실 이벤트(계정 생김 · 비밀번호 바뀜)와 계정 공개 조회.
 * docs/specs/account.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "credential" })
package net.yozmnews.account;

import org.springframework.modulith.ApplicationModule;
