# ADR-0343: 운영자 비밀도 BYOK 키처럼 로그 · 오류 문구 · 응답에 남기지 않고, 그 규약의 정본은 `overview` §6 이다

- 상태: 승인
- 날짜: 2026-11-26
- 근거: [이슈 #268](https://github.com/Theia0382/yozm-news/issues/268) — 기점 `da596fc` 에서 시험 주석 여섯 곳이 운영자 키를 로그에 남기지 않는 규칙의 근거로 `security.md` 를 들었으나 그 문서에는 BYOK 키의 규칙만 있었다. 운영자 비밀 여섯이 값을 숨기는 자리를 코드에서 대조했다
- 관련: [ADR-0043](0043-admin-functions-guarded-by-env-bearer-token-until-account-roles.md)(운영자 토큰 값은 로그 · 예외 문구 · 응답에 남기지 않는다 — 보충), [ADR-0152](0152-secret-value-is-a-platform-public-type-that-fails-on-serialization.md)(BYOK 키 평문의 래퍼 `SecretValue`)

## 맥락

- `overview` §6 "비밀 값" 규약은 BYOK 키만 다뤘다. 평문을 `SecretValue` 로 감싸고 로그 · 오류 · 응답 · 직렬화에 남기지 않는다.
- 운영자가 설정으로 주고 앱 모듈이 읽는 비밀은 여섯이고, 코드는 여섯 모두 이미 값을 숨긴다.
  - Jev 키 · 외부 임베딩 키는 `evaluation` 의 값 타입이 `toString` 에서 숨기고 문구에 담지 않는다
  - 봇 차단 비밀 키는 `platform` 의 `SecretValue` 로 다룬다
  - 운영자 토큰은 다이제스트로만 갖는다(ADR-0043)
  - 첫 운영자 초기 비밀번호는 `account` 의 설정 타입이 `toString` 에서 숨긴다(account-R83)
  - KEK 는 `toString` 이 버전 목록만 낸다(credential-R8)
- 규칙은 모듈마다 흩어져 있었다. 운영자 토큰은 ADR-0043 과 account Spec, 첫 운영자 초기 비밀번호는 account-R83, KEK 는 credential-R8, 봇 차단 비밀 키는 account-R105 가 적었고, Jev 키 · 임베딩 키는 evaluation Spec 의 내부 구조 서술과 코드 · 시험만 있었다. 시험 여섯 곳이 근거로 든 `security.md` 에는 그 규칙이 없었다.

## 결정

- **운영자 비밀도 BYOK 키처럼 로그 · 오류 문구 · 응답에 남기지 않는다.** 대상은 운영자가 설정으로 주고 앱 모듈이 읽는 비밀 여섯이다. 목록은 `overview` §6 에 있다.
- **DB 비밀번호는 이 규약 밖이다.** 앱 모듈이 아니라 프레임워크의 데이터 소스 연결 설정이 다룬다.
- **운영자 비밀은 그 비밀을 가진 모듈이 설정에서 읽어 값을 숨기는 타입으로만 다룬다.** 숨기는 방법(값 타입의 `toString` · `SecretValue` · 다이제스트)은 모듈이 고르고 그 모듈 Spec 이 적는다.
- **이 규약의 정본은 [`overview` §6](../overview.md#6-횡단-규약) "비밀 값" 행이다.** 시험은 그 행과 모듈 규칙을 근거로 든다.

## 결과

- 새 운영자 비밀을 더하는 작업은 `overview` §6 의 목록에 더하고, 값을 숨기는 타입과 그 시험을 함께 둔다.
- `security.md` 는 BYOK 키 보관의 위협(DB 덤프와 KEK 를 함께 얻으면 키가 열린다)만 갖고, 평문을 다루는 규약은 `overview` §6 을 가리킨다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 시험 주석을 모듈 Spec 규칙으로만 돌리고 횡단 규칙은 두지 않는다 | 모듈 Spec 에 규칙이 고르게 있지 않다. Jev 키 · 임베딩 키는 규칙 자리를 새로 만들어야 하고, 같은 규칙이 여러 곳에 흩어진다 |
| DB 비밀번호까지 넣는다 | 앱 모듈에 값을 숨기는 타입이 없어 "그 비밀을 가진 모듈이 값을 숨기는 타입으로 다룬다"가 그 하나에 성립하지 않는다 |
