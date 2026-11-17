package net.yozmnews.user;

import java.util.Optional;

import net.yozmnews.platform.UserId;

/**
 * 다이제스트가 읽는 관심 설정 — 활성 태그 체계의 키로 낸 주제 구독(펼친 중분류와 함께) · 지역 · 형식 제외 · 제외 소스 · 관심사 · 시간대 ·
 * 분량 · 요약 길이 · 추론 강도. 다이제스트 후보 선별 작업이 읽는다. docs/specs/user.md §3
 * <p>
 * 백그라운드 작업 전용이다 — 화면 경로에서 부르지 않는다(화면은 세션 사용자로 범위가 정해진 처리기를 쓴다). 쓰지 않는다 — 선호 버전이 활성
 * 버전과 다르면 따라가기 결과를 계산해 내고, 설정 행이 없으면 계정을 만들 때의 기본값을 계산해 낸다. 저장 행은 리스너 · 사용자의 조회 ·
 * 저장이 고쳐 쓴다.
 */
public interface DigestInterestsDirectory {

	/** 그 사용자의 관심 설정 — 계정이 없으면 빈 값 */
	Optional<DigestInterests> interestsOf(UserId user);

}
