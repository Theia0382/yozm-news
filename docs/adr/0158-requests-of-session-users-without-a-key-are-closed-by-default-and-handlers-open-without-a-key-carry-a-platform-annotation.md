# ADR-0158: 키 없는 세션 사용자의 요청은 닫힘이 기본이고, 키 없이도 여는 처리기에 `platform` 의 표시를 단다

- 상태: 승인
- 날짜: 2026-11-14
- 근거: [이슈 #150](https://github.com/Theia0382/yozm-news/issues/150)
- 관련: [ADR-0146](0146-sessions-of-accounts-with-an-initial-password-open-only-password-change-status-and-sign-out-marked-by-a-platform-annotation.md)(같은 꼴의 표시), [ADR-0125](0125-screen-api-handlers-need-a-session-unless-marked-public-and-take-the-user-only-as-a-session-filled-argument.md)(공개 처리기 표시 · 등록 가드), [ADR-0003](0003-byok-personalized-digest.md)(키 없는 사용자에게는 키 등록 페이지만), [ADR-0159](0159-having-a-key-means-having-any-stored-key-regardless-of-its-status.md)(키가 있다는 것의 뜻), [ADR-0160](0160-users-without-a-key-may-use-key-account-and-notice-handlers.md)(여는 범위)

## 맥락

- 키 없는 사용자에게는 키 등록 페이지만 보여준다. 화면이 키 화면으로 보내기만 하면, 화면이 감춘 것을 API 가 내준다.
- 키가 있어야 뜻이 있는 처리기(다이제스트 · 스토리라인 · 리포트 등)는 뒤에 여러 모듈에서 생긴다.
- 세션 체인은 처리기의 표시를 읽어 인가한다 — 공개 표시와 비밀번호 변경 전 허용 표시가 그렇다.

## 결정

- **키가 없는 세션 사용자는 공개 처리기와, 키 없이도 여는 표시가 있는 처리기만 쓴다.** 그 밖의 화면용 API 는 403 `llm-key-required` 다.
- **표시는 `platform` 의 공개 애너테이션 `@AllowedWithoutLlmKey`(이유 필수)이다.** 세션 체인 인가 · 명세 설정 · 등록 가드가 `platform` 의 판정 하나로 읽는다. 공개 처리기에는 달지 않는다.
- 비밀번호 강제 변경이 먼저다 — 강제 변경 중인 세션 사용자는 변경 전 허용 표시만으로 판정한다.
- 키 유무는 요청마다 `credential` 에 묻고 세션에 저장하지 않는다. 표시가 있는 처리기 · 공개 처리기의 요청에는 묻지 않는다.

규칙은 [user Spec](../specs/user.md#화면용-api-의-공개와-세션-사용자) user-R5, [platform Spec](../specs/platform.md#화면용-api-경로와-공개-처리기) platform-R21 에 있다.

## 결과

- 새 모듈의 처리기는 표시가 없으면 키 없는 사용자에게 닫힌다 — 빠뜨린 쪽이 닫힌다.
- 표시가 없는 처리기의 요청마다 키 유무 조회가 하나 는다.
- 운영자 경로는 운영자 토큰의 체인이라 이 규칙과 무관하다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 열림이 기본이고 키가 있어야 하는 처리기에 표시를 단다 | 표시를 빠뜨린 처리기가 키 없는 사용자에게 열린다 |
| 서버는 막지 않고 화면만 보낸다 | 화면이 감춘 것을 API 가 내준다 |
