# ADR-0146: 초기 비밀번호 계정의 세션은 비밀번호 변경 · 상태 조회 · 로그아웃만 열고, 그 허용 표시는 `platform` 의 공개 애너테이션이다

- 상태: 승인
- 날짜: 2026-11-13
- 근거: [이슈 #147](https://github.com/Theia0382/yozm-news/issues/147) — 구현 `4d3f932`
- 관련: [ADR-0125](0125-screen-api-handlers-need-a-session-unless-marked-public-and-take-the-user-only-as-a-session-filled-argument.md)(공개 처리기 표시 · 등록 가드 — 같은 꼴의 표시를 하나 더한다), [ADR-0048](0048-sign-up-closed-by-default-with-invitations-and-operator-bootstrap.md)(초기 비밀번호로 만든 계정은 첫 로그인 때 비밀번호 변경을 강제한다)

## 맥락

- 운영자 계정 생성 · 첫 운영자는 남이 정한 초기 비밀번호로 계정을 만들고, 첫 로그인 때 비밀번호 변경을 강제한다([ADR-0048](0048-sign-up-closed-by-default-with-invitations-and-operator-bootstrap.md)).
- 화면이 변경 화면으로 보내기만 하면, 초기 비밀번호를 아는 다른 사람이 API 로 그 계정을 쓸 수 있다.
- 화면용 API 처리기는 모듈마다 있고, 세션 체인은 처리기의 공개 표시를 읽어 인가한다([ADR-0125](0125-screen-api-handlers-need-a-session-unless-marked-public-and-take-the-user-only-as-a-session-filled-argument.md)).

## 결정

- **초기 비밀번호로 로그인한 세션 사용자는 공개 처리기와, 변경 전 허용 표시가 있는 처리기(지금 비밀번호 변경 하나)만 쓴다.** 그 밖의 화면용 API 는 403 `password-change-required` 다. 그 요청의 권한은 역할 대신 강제 변경 권한 하나라 역할 권한이 없다.
- **허용 표시는 `platform` 의 공개 애너테이션 `@AllowedBeforePasswordChange`(이유 필수)이다.** 세션 체인 인가 · 명세 설정 · 등록 가드가 `platform` 의 판정 하나로 읽는다. 공개 처리기에는 달지 않는다.
- 강제 변경 여부는 요청마다 자격에서 읽는다 — 세션에 저장하지 않는다.

## 결과

- 초기 비밀번호를 아는 다른 사람이 계정 주인보다 먼저 들어와도 비밀번호를 바꾸는 것 말고는 할 수 없다.
- 새 모듈의 처리기가 변경 전에도 열려야 하면 그 처리기에 표시를 단다 — 표시가 없으면 닫힌다.
- 명세는 표시가 없는 세션 연산의 403 설명에 "비밀번호를 바꿔야 한다"를 담는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 화면만 변경 화면으로 보낸다 | API 로 그대로 쓴다 |
| 허용 경로 목록을 `user` 의 보안 구성에 둔다 | 다른 모듈의 처리기를 `user` 가 경로로 알아야 하고, 공개 처리기 표시와 꼴이 달라진다 |
