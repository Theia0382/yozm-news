# ADR-0248: 다이제스트가 읽는 관심 설정과 구독 제안의 조회는 화면 경로에서 세션 사용자로 부를 수 있다

- 상태: 승인
- 날짜: 2026-11-19
- 근거: [이슈 #188](https://github.com/Theia0382/yozm-news/issues/188) — 제안 조회가 화면 요청 안에서 지금 주제 구독을 읽어야 함을 확인하고 리드와 정함. 구현 커밋 `3ecb800` · `bdf6bb1` · `ac24312`
- 관련: [ADR-0228](0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md)(부분 대체 — 결정 절의 대상 목록과 결과 절의 "다이제스트가 읽는 관심 설정(`DigestInterestsDirectory`)은 … '백그라운드 작업 전용' 그대로다"), [ADR-0247](0247-topic-suggestion-declines-are-kept-by-user-with-the-recorded-version-and-digest-answers-the-interest-at-decline-through-an-interface-declared-by-user.md)(거절의 조회 · 공급 인터페이스)

## 맥락

- 구독 제안은 저장하지 않고 화면 요청 안에서 계산한다 — "구독하지 않은 중분류"를 알려면 지금 주제 구독을 읽어야 한다.
- 지금 주제 구독(펼친 중분류)을 내는 `user` 의 공개 조회는 다이제스트가 읽는 관심 설정(`DigestInterestsDirectory`) 하나다. ADR-0228 은 사용자를 인자로 받는 소비자 조회를 화면 경로에서 부를 수 있게 하면서, 이 조회는 화면에서 쓰지 않는다는 까닭으로 백그라운드 작업 전용으로 남겼다.
- ADR-0228 은 같은 읽기를 화면 전용 조회로 따로 여는 길을 기각했다.

## 결정

- **다이제스트가 읽는 관심 설정(`DigestInterestsDirectory`)은 사용자를 인자로 받는 조회로 화면 경로에서 세션 사용자로 부를 수 있다.** 쓰지 않는 것은 그대로다.
- **구독 제안이 새로 연 조회도 같다** — 구독 제안(`digest` 의 `TopicSuggestions`), 거절 조회(`user` 의 `TopicSuggestionDeclines`), 지금 관심 공급(`user` 가 선언하고 `digest` 가 구현하는 `TopicInterestNow` — 거절 처리기가 부른다).
- 후보 선별(`CandidateSelection`)은 그대로 백그라운드 작업 전용이다.

## 결과

- 같은 주제 구독 읽기의 계약이 하나로 남는다.
- 제안 조회는 관심 설정 전체(제외 · 관심사 · 분량 등)를 읽고 주제 구독만 쓴다 — 좁은 인터페이스는 아니다.
- 화면 경로에서 넘기는 사용자가 세션 사용자인지는 처리기의 책임이고, 처리기는 격리 시험에 등록한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `user` 에 "지금 주제 구독"만 내는 좁은 조회를 새로 연다 | 같은 읽기의 계약이 둘이 된다 — ADR-0228 이 기각한 길과 같은 결이다 |
| 제안을 백그라운드 작업(다이제스트 생성)이 계산해 저장한다 | 수락 · 거절 · 구독 변경 뒤 저장한 목록이 다음 생성까지 낡고, 관심을 조회 때 계산한다는 결정과 결이 어긋나는 파생 저장이 생긴다 |
