# ADR-0228: 사용자를 인자로 받는 소비자 조회는 화면 경로에서 세션 사용자로 부를 수 있다

- 상태: 승인. 부분 대체 — 화면 경로 대상 목록([ADR-0248](0248-the-digest-interests-and-topic-suggestion-queries-may-be-called-on-screen-paths-with-the-session-user.md) · [ADR-0308](0308-account-authentication-and-security-live-in-the-account-module-while-interests-and-notices-stay-in-user.md)) · 다이제스트가 읽는 관심 설정의 백그라운드 작업 전용([ADR-0248](0248-the-digest-interests-and-topic-suggestion-queries-may-be-called-on-screen-paths-with-the-session-user.md))
- 날짜: 2026-11-18
- 근거: [이슈 #189](https://github.com/Theia0382/yozm-news/issues/189) — 관심사 히스토리를 조회 때 계산하려면 화면 요청 안에서 관심도 재료 조회를 불러야 함을 확인하고 정함. 구현 커밋 `7d7a1f8` · `86e0cc0`
- 관련: [ADR-0191](0191-the-consumer-query-of-storyline-subscriptions-is-opened-with-the-subscriptions-and-resolves-ids-without-writing.md)(결정 "조회는 쓰지 않는다 … 백그라운드 작업 전용이다"의 "백그라운드 작업 전용" — 부분 대체), [ADR-0215](0215-interests-are-not-stored-entities-and-are-computed-from-signal-history-at-query-time.md)(관심사는 조회할 때 계산한다), [ADR-0192](0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)(독자 화면은 `reader` 가 엮는다)

## 맥락

- 관심도 재료를 내는 소비자 조회 셋(좋아요 / 싫어요 이력 · 주제 구독 이력 · 스토리라인 구독 이력)과 `digest` 의 재료 읽기는 "백그라운드 작업 전용 — 화면 경로에서 부르지 않는다"로 적혀 있었다.
- 보안 규칙은 "사용자 범위가 없는 조회는 백그라운드 작업 전용"이다. 세 조회는 사용자를 인자로 받아 그 사용자로 범위가 정해진다. 같은 꼴의 `ReceivedArticles` 는 `reader` 가 세션 사용자로 부른다.
- 관심사 히스토리는 저장하지 않고 조회할 때 계산한다([ADR-0215](0215-interests-are-not-stored-entities-and-are-computed-from-signal-history-at-query-time.md)). 화면 요청 안에서 재료 조회를 부르지 못하면 계산할 길이 없다.

## 결정

- **사용자를 인자로 받는 소비자 조회는 화면 경로에서 세션 사용자로만 부른다.** 화면 경로의 격리는 처리기가 사용자를 세션 인자로만 받는 것과 격리 시험이 지킨다. 조회는 여전히 쓰지 않는다.
> 부분 대체(대상 목록) — [ADR-0248](0248-the-digest-interests-and-topic-suggestion-queries-may-be-called-on-screen-paths-with-the-session-user.md) · [ADR-0308](0308-account-authentication-and-security-live-in-the-account-module-while-interests-and-notices-stay-in-user.md). 지금 상태: [user Spec](../specs/user.md#digestinterestsdirectory) user-R57 · [account Spec](../specs/account.md#accountdirectory) account-R37
- 대상은 `feedback` 의 `ArticleFeedback`, `user` 의 `TopicSubscriptionHistory` · `StorylineSubscriptionDirectory` · `UserTimezones`, `digest` 의 관심도 재료 읽기 · `InterestHistory` 다. 사용자 범위가 없는 조회만 백그라운드 작업 전용이다.

## 결과

- 화면 요청에서 다른 모듈의 소비자 조회를 엮을 수 있다 — 같은 읽기에 화면용 조회를 따로 열지 않는다.
- 화면 경로에서 부를 때 넘기는 사용자가 세션 사용자인지는 그 처리기의 책임이다. 처리기를 더하는 작업은 격리 시험을 함께 등록한다.
> 부분 대체(다이제스트가 읽는 관심 설정 부분) — [ADR-0248](0248-the-digest-interests-and-topic-suggestion-queries-may-be-called-on-screen-paths-with-the-session-user.md). 지금 상태: [user Spec](../specs/user.md#digestinterestsdirectory) user-R57
- 후보 선별(`CandidateSelection`)과 다이제스트가 읽는 관심 설정(`DigestInterestsDirectory`)은 화면에서 쓰지 않으므로 "백그라운드 작업 전용" 그대로다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 계약을 두고 관심사 히스토리를 백그라운드 작업이 계산해 저장한다 | 관심사를 저장하지 않는다는 결정([ADR-0215](0215-interests-are-not-stored-entities-and-are-computed-from-signal-history-at-query-time.md))과 조회 때 계산([ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md))을 다시 연다 |
| 같은 읽기를 화면 경로 전용 조회로 `feedback` · `user` 에 따로 연다 | 같은 읽기의 계약이 둘이 된다 |

## 후속 기록

- 2026-11-20: 결정의 대상 목록 가운데 `feedback` 의 `ArticleFeedback`(지금은 사건 단위 `EventFeedback`) — [ADR-0266](0266-feedback-is-recorded-under-the-original-event-id-and-feedback-resolves-merged-events-through-event-when-reading-with-the-last-pressed-value-winning.md) · [feedback Spec](../specs/feedback.md#eventfeedback--feedbackchange) `EventFeedback`
- 2026-11-22: 결정의 대상 목록에 사용자 스토리라인 조회(`UserStorylineDirectory`)가 든다 — 관심사 히스토리 · 구독 제안의 화면 경로에서 세션 사용자로 불린다([ADR-0282](0282-flow-tracking-points-to-a-user-storyline-which-user-handlers-store-under-the-settings-lock-while-readers-get-the-result-of-the-same-rule.md)), 지금 상태는 [user Spec](../specs/user.md#userstorylinedirectory)
