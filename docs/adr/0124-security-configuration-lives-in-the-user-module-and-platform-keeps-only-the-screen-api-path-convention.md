# ADR-0124: 인증 · 보안 구성은 운영자 토큰 체인까지 `user` 모듈이 갖고, `platform` 은 화면용 API 경로 규약만 갖는다

- 상태: 승인
- 날짜: 2026-11-13
- 근거: [이슈 #145](https://github.com/Theia0382/yozm-news/issues/145) — 기점 `a02a205` 에서 `platform` 이 다른 모듈을 import 하지 않음(`platform` main 의 `net.yozmnews.*` import 는 자기 패키지뿐)과, 보안 구성 밖에서 보안 타입을 쓰는 곳이 명세 설정의 운영자 경로 상수 하나뿐임을 확인
- 관련: [ADR-0042](0042-app-roles-as-web-worker-profiles-marked-by-platform-annotations.md)(결과 절 첫 항목의 "`platform` 의 보안 설정은 사용자 조회 등을 Spring 인터페이스 타입으로 주입받는다 · 그 길이 막히면 역할 애너테이션의 자리를 다시 본다" — 부분 대체), [ADR-0043](0043-admin-functions-guarded-by-env-bearer-token-until-account-roles.md)(운영자 토큰 — 동작은 그대로, 자리만 옮긴다), [ADR-0013](0013-architecture-patterns.md)(모듈 경계)

## 맥락

- 보안 구성(운영자 토큰 체인 · 모두 허용 체인)은 `platform` 에 있었다. 계정이 없던 때 운영자 경로를 막으려고 둔 자리다([ADR-0043](0043-admin-functions-guarded-by-env-bearer-token-until-account-roles.md)).
- 계정 · 세션 · 자체 로그인을 들이면 세션 체인이 계정을 읽고(요청마다 상태 · 역할), 로그인이 자격을 대조해야 한다. 이 지식은 `user` 의 것이다.
- `platform` 은 다른 모듈에 의존할 수 없다 — 모든 모듈의 진입점이 `platform` 의 역할 애너테이션에 의존해 순환이 된다([ADR-0042](0042-app-roles-as-web-worker-profiles-marked-by-platform-annotations.md)).
- 운영자 토큰은 운영자 경로가 계정 세션으로 바뀔 때까지의 임시 장치다. 그 전환이 끝나면 보안 구성 전체가 계정 · 세션 지식이 된다.

## 결정

- **보안 구성 전체를 `user` 모듈이 갖는다.** 체인 셋(운영자 → 세션 → 기본)의 순서 · 경로, 운영자 토큰 인증(자격 판정 · 필터 · provider · 설정), 세션 체인, 보안 거부의 문제 유형이 `user` 에 있다. 두 역할 공통이고 역할 표시를 달지 않는다.
- **운영자 토큰 체인도 지금 옮긴다.** 동작은 바꾸지 않는다 — 패키지만 옮긴다.
- **`platform` 은 화면용 API 경로 규약을 갖는다.** 화면용 API 경로(`/api/**`)와 운영자 경로(`/api/admin/**`)의 상수(`ApiPaths`)는 라우팅 규약이라 `platform` 의 공개 타입이고, 보안 구성 · 명세 설정 · 시험이 읽는다. `user` 가 `platform` 을 쓰는 방향이라 의존 규칙에 들어맞는다.

## 결과

- 계정 지식(계정 조회 · 자격 대조 · 세션 사용자)과 그것을 쓰는 체인이 한 모듈 안에서 컴파일로 묶인다. 운영자 경로를 세션으로 바꾸는 작업은 `user` 안의 변경이 된다.
- 운영자 접근 통제의 규칙 식별자가 `platform` 에서 `user` 로 옮겨 간다 — `platform` 의 옛 식별자는 폐지 표시로 새 식별자를 가리킨다.
- 운영자 토큰 코드는 곧 바뀔 코드인데도 한 번 옮긴다. 옮기지 않으면 그동안 두 모듈이 체인을 나눠 갖는다.
- 웹 계층 시험이 싣는 보안 구성도 `user` 의 것이 된다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `platform` 이 모든 체인을 갖고 `user` 는 Spring Security 타입의 빈(인증 provider · 공개 경로 매처 등)으로 끼운다 | 계정 지식이 컴파일러가 잡지 않는 프레임워크 타입 결선으로 넘어가고, 그 이음매가 운영자 세션 전환까지 계속 는다. 운영자 토큰이 임시 장치라 "계정과 무관한 것을 옮긴다"는 근거가 약하다(사용자 결정) |
| 세션 체인만 `user` 가 내고 운영자 · 기본 체인은 `platform` 에 둔다 | 체인 순서가 모듈 사이 계약이 되고, 운영자 경로를 세션으로 바꿀 때 같은 세션 · CSRF 정책이 두 모듈에 필요하다 |
| `platform` 이 공개 인터페이스(세션 사용자 확인 등)를 선언하고 `user` 가 구현한다 | 외부 의존성이 아닌 곳에 구현이 하나뿐인 포트 꼴 인터페이스가 생긴다([ADR-0013](0013-architecture-patterns.md)) |
