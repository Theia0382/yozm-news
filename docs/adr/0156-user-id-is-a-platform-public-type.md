# ADR-0156: 사용자 식별자 `UserId` 는 `platform` 의 공개 타입이다

- 상태: 승인
- 날짜: 2026-11-14
- 근거: [이슈 #150](https://github.com/Theia0382/yozm-news/issues/150) — `credential` 모듈을 만들며 `user` · `credential` · `llm` 사이의 의존 방향을 확인
- 관련: [ADR-0125](0125-screen-api-handlers-need-a-session-unless-marked-public-and-take-the-user-only-as-a-session-filled-argument.md)(처리기는 사용자를 `UserId` 인자로만 받는다 — 그 타입의 모듈을 이 ADR 이 옮긴다), [ADR-0152](0152-secret-value-is-a-platform-public-type-that-fails-on-serialization.md)(같은 까닭으로 `platform` 에 둔 횡단 타입)

## 맥락

- `UserId` 는 `user` 의 공개 타입이었다. 화면용 API 처리기는 세션 사용자를 이 타입의 인자로만 받고, 사용자 데이터를 가진 모듈은 이 타입으로 주인을 가리킨다.
- 그래서 사용자 데이터를 가진 모듈은 모두 `user` 에 의존하게 된다. 그런데 `user` 도 그 모듈들에 의존해야 할 일이 있다 — 키 무효 이벤트를 받아 알림을 남기고, 세션 체인이 키 유무를 묻고, 사용자 설정이 허용 모델 목록을 읽는다.
- 모듈 사이의 순환 의존은 검증이 실패로 판정한다.

## 결정

- **`UserId` 는 `platform` 의 공개 타입이다.** 어느 모듈의 지식도 아닌 횡단 식별자다.
- 세션 사용자로 인자를 채우는 일과 사용자 격리의 규칙은 `user` 에 남는다.

타입은 [platform Spec](../specs/platform.md#userid)에 있다.

## 결과

- 사용자 데이터를 가진 모듈이 이 타입 때문에 `user` 에 의존하지 않는다. `user` 가 그 모듈의 이벤트를 받거나 공개 API 를 부를 수 있다.
- 다이제스트 실패 알림처럼 뒤에 생기는 "모듈이 알리고 `user` 가 알림을 남기는" 흐름도 같은 꼴을 쓴다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `user` 에 둔다 | `credential` · `llm` 이 `user` 를 향해 `user` 가 둘 어느 쪽에도 의존할 수 없다. 알림은 각 모듈이 `user` 의 쓰기 API 를 불러 남겨야 하고, 키 관문은 물음의 인터페이스를 따로 둬야 하며, 사용자 설정이 허용 모델 목록을 읽는 의존과 순환한다 |
