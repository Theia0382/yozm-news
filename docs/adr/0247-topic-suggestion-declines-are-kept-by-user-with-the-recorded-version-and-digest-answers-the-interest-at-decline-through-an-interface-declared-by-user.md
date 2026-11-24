# ADR-0247: 구독 제안의 거절은 `user` 가 기록 당시 버전과 함께 갖고, 거절 때 관심은 `user` 가 선언한 공급 인터페이스로 `digest` 가 답한다

- 상태: 승인. 부분 대체 — 계정과 함께 지움([ADR-0308](0308-account-authentication-and-security-live-in-the-account-module-while-interests-and-notices-stay-in-user.md))
- 날짜: 2026-11-19
- 근거: [이슈 #188](https://github.com/Theia0382/yozm-news/issues/188) — 기점 `91ba014` 의 모듈 의존(`digest` → `user`)과 태그 선호 · 주제 구독 이력의 키 처리를 대조하고 정함. 구현 커밋 `3ecb800` · `bdf6bb1`
- 관련: [ADR-0192](0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)(사용자가 정해 두는 것의 화면은 그 데이터를 가진 모듈이 연다), [ADR-0196](0196-feedback-is-accepted-only-on-articles-cited-by-the-users-digest-and-digest-answers-through-an-interface-declared-by-feedback.md) · [ADR-0157](0157-credential-depends-on-llm-and-stored-keys-reach-llm-through-a-supply-interface-that-llm-declares-and-credential-implements.md)(공급 인터페이스의 꼴), [ADR-0246](0246-a-declined-topic-suggestion-returns-only-after-a-period-and-a-higher-interest-and-a-decline-is-not-an-interest-signal.md)(거절 때 관심을 쓰는 규칙)

## 맥락

- 주제에 대해 사용자가 정해 두는 것(주제 구독 · 지역 · 형식 제외)은 모두 `user` 의 관심 설정에 있다. 관심도와 제안 판정은 `digest` 의 것이다.
- 모듈 의존은 `digest` → `user` 다. `user` 가 `digest` 를 읽으면 순환이다.
- 거절 뒤 다시 내는 조건에 거절 때의 관심이 필요하다. 관심은 `digest` 만 계산한다.
- 태그 체계가 바뀌면 키가 이름을 바꾸거나 합쳐지거나 사라진다. 키 문자열은 버전을 넘어 다른 뜻으로 다시 쓰일 수 있다.

## 결정

- **거절 기록은 `user` 가 갖는다.** 처리기는 관심 설정 처리기 옆에 서고, 다른 모듈은 사용자 범위 소비자 조회로 읽는다.
- **거절 때 관심은 `user` 가 선언한 공급 인터페이스를 `digest` 가 구현해 답한다.** `user` 는 관심을 계산하지 않고 `digest` 의 타입을 쓰지 않는다. 화면이 보낸 값은 쓰지 않는다.
- **거절 행은 기록 당시 태그 체계 버전과 함께 적고, 기본 키에 버전을 넣는다.** 읽을 때 계보로 활성 키로 풀고, 같은 활성 키에 닿는 행이 여럿이면 거절 시각이 늦은 것 하나(같으면 관심이 큰 것)를 쓴다. 활성 전환 따라가기로 고쳐 쓰지 않는다.
- 지금 상태는 [user Spec "구독 제안 거절"](../specs/user.md#구독-제안-거절) user-R64 · R65 와 [digest Spec "구독 제안"](../specs/digest.md#구독-제안) digest-R43 이다.

## 결과

- 주제에 대한 사용자 결정이 한 모듈에 모인다. `user` 는 판정을 모르고 "다시 권하지 말라는 기록"만 갖는다.
- 거절 처리기는 거절 때 관심(창 120일의 재료)을 트랜잭션 밖에서 읽고 쓰기는 한 문장이다. 거절마다 재료를 읽는 비용은 재지 않았다.
> 부분 대체 — [ADR-0308](0308-account-authentication-and-security-live-in-the-account-module-while-interests-and-notices-stay-in-user.md). 지금 상태: [`user` Spec §6](../specs/user.md#6-데이터)
- 거절 행은 계정과 함께 지워진다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 거절을 `digest` 가 갖는다 | `digest` 가 사용자가 정해 두는 데이터를 처음 갖고 첫 화면 처리기를 연다. 주제에 대한 사용자 결정이 두 모듈로 갈린다 |
| 거절 때 관심을 저장하지 않고 판정 때 거절 시각을 기준으로 다시 계산한다 | 오래된 거절일수록 재료를 거슬러 읽고, 지운 기사의 피드백 · 재평가 · 계보로 다시 계산한 값이 그때와 달라진다 |
| 거절 처리기를 `reader` 에 두고 `digest` 가 계산한 값을 `user` 의 쓰기 API 로 넘긴다 | 사용자가 정해 두는 것의 처리기를 데이터를 가진 모듈이 열지 않게 된다 |
| 화면이 관심 값을 실어 보낸다 | 신뢰할 수 없는 입력이다 — 사용자가 값을 골라 영구 거절을 만들 수 있다 |
| 기본 키를 (사용자, 키)로 두고 다시 거절하면 덮어쓴다 | 키 문자열이 다른 뜻으로 다시 쓰이면 다른 활성 키로 풀리던 옛 거절이 덮여 사라진다 |
