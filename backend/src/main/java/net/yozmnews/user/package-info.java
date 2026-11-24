/**
 * 사용자 한 사람의 관심과 그 사람에게 보이는 기록 — 화면 내 알림(피드 상태 변화는 {@code source} 의, 키 무효는 {@code credential} 의,
 * 비밀번호 바뀜은 {@code account} 의 이벤트로 받는다), 관심 설정(계정 생김을 {@code account} 의 이벤트로 받아 기본값을 넣는다, 활성 태그 체계 ·
 * 계보는 {@code tag} 에서 읽고 체계 내용의 값 타입은 {@code evaluation} 의 것이다, 고를 소스는 {@code source}, 허용 모델은 {@code llm},
 * 키 상태는 {@code credential} 에 묻는다), 스토리라인 구독(남는 스토리라인 풀기와 합치거나 지운 스토리라인 알림은 {@code storyline}, 사라진
 * 구독 알림의 제목은 {@code event} 의 대표 기사와 {@code article} 의 제목에서 읽는다), 사용자 스토리라인과 그 정리(씨앗이 되는 반응 이력은
 * {@code feedback}, 꼭대기와 이어짐은 {@code storyline} 에서 읽는다), 구독 제안 거절. 계정의 있음 · 상태는 {@code account} 의 공개 조회로
 * 읽는다. docs/specs/user.md.
 */
@ApplicationModule(allowedDependencies = { "platform", "account", "source", "credential", "llm", "tag", "evaluation",
		"storyline", "event", "article", "feedback" })
package net.yozmnews.user;

import org.springframework.modulith.ApplicationModule;
