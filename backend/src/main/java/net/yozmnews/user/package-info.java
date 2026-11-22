/**
 * 계정과 그 인증 — 계정 · 자체 로그인 자격, 보안 구성(운영자 · 세션 · 기본 체인)과 운영자 접근 통제, 세션 · 세션 사용자 범위, 로그인 · 실패
 * 지연, 처리기 앞의 요청 제한(표시는 {@code platform} 의 것), LLM 키가 없는 세션 사용자의 키 관문(키 유무는 {@code credential} 에 묻는다),
 * 화면 내 알림(피드 상태 변화는 {@code source} 의, 키 무효는 {@code credential} 의 이벤트로 받는다), 관심 설정(활성 태그 체계 · 계보는
 * {@code tag} 에서 읽고 체계 내용의 값 타입은 {@code evaluation} 의 것이다, 고를 소스는 {@code source}, 허용 모델은 {@code llm}, 키 상태는
 * {@code credential} 에 묻는다), 스토리라인 구독(남는 스토리라인 풀기와 합치거나 지운 스토리라인 알림은 {@code storyline}, 사라진 구독
 * 알림의 제목은 {@code event} 의 대표 기사와 {@code article} 의 제목에서 읽는다), 사용자 스토리라인(씨앗이 되는 반응 이력은
 * {@code feedback}, 꼭대기와 이어짐은 {@code storyline} 에서 읽는다). docs/specs/user.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "source", "credential", "llm", "tag", "evaluation", "storyline",
		"event", "article", "feedback" })
package net.yozmnews.user;

import org.springframework.modulith.ApplicationModule;
