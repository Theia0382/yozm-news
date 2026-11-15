# ADR-0184: 운영자 경로의 거부는 세션 체인과 같은 문제 유형을 쓰고, 틀린 운영자 토큰만 `admin-unauthorized` 로 가른다

- 상태: 승인
- 날짜: 2026-11-15
- 근거: [이슈 #149](https://github.com/Theia0382/yozm-news/issues/149) — 백엔드 `e2656e8`, 화면 `089c7cc`
- 관련: [ADR-0043](0043-admin-functions-guarded-by-env-bearer-token-until-account-roles.md)(부분 대체 — 결정 절 둘째 항목의 둘째 · 셋째 문장: 닫힌 인스턴스의 403 `admin-closed` 와 토큰 없음 · 틀림의 401 `admin-unauthorized`), [ADR-0044](0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md)(거부 응답의 형식), [ADR-0183](0183-admin-paths-open-with-admin-account-sessions-and-keep-the-operator-token-as-an-optional-script-bearer-with-csrf-only-for-sessions.md)(운영자 경로의 인증)

## 맥락

- 운영자 경로가 계정 세션과 선택 운영자 토큰으로 열린다(ADR-0183). 거부 갈래는 넷 — 세션 없음, 비밀번호를 바꿔야 함, `ADMIN` 아님, 변경 요청의 CSRF — 과 틀린 운영자 토큰이다.
- 세션 체인은 이미 문제 유형으로 화면에 로그인 이동(`unauthenticated`) · 비밀번호 변경 이동(`password-change-required`)을 가르게 한다.
- "운영자 기능이 닫힘"(`admin-closed`)은 운영자 토큰 유무에서 나온 상태라 운영자 경로가 세션으로 열리면 뜻을 잃는다.

## 결정

- **세션 체인의 유형을 함께 쓴다** — 세션 없음 · 만료 401 `unauthenticated`, 비밀번호를 바꿔야 함 403 `password-change-required`, CSRF 토큰 없음 · 틀림 403 `csrf-rejected`.
- **`ADMIN` 이 아닌 세션 사용자는 403 `admin-forbidden`** 이다 — 운영자 경로의 존재를 숨기지 않는다.
- **틀린 운영자 토큰만 401 `admin-unauthorized`** 이고 `WWW-Authenticate: Bearer` 를 단다 — `Authorization` 헤더를 낸 요청의 스킴 · 형식 · 값이 틀리거나 토큰을 설정하지 않았을 때다.
- **`admin-closed` 를 없앤다.**

## 결과

- 화면은 운영자 경로에서도 같은 계약으로 로그인 · 비밀번호 변경 화면으로 보낸다. 도구는 401 의 유형으로 토큰 문제와 로그인 문제를 가른다.
- 익명의 변경 요청은 CSRF 가 인증보다 먼저 막아 403 `csrf-rejected` 다(세션 체인과 같다).
- 배포 스모크는 토큰 유무와 무관하게 로그인 요구 하나와 틀린 토큰 거부 하나를 본다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 운영자 경로 전용 유형(세션 없음도 `admin-unauthorized`, 비밀번호 변경 · 역할을 `admin-forbidden` 하나로) | 화면이 운영자 경로에서만 다른 유형으로 가르고, 비밀번호를 바꿔야 하는 운영자를 변경 화면으로 보내지 못한다 |
| `ADMIN` 이 아니면 404 로 숨긴다 | 경로는 공개 소스 · 명세에 있어 숨길 것이 없고, 화면은 권한 없음을 안내하지 못한다 |
