# ADR-0209: 최대 후보 수와 큰 슬롯 수는 사용자 설정이고, 큰 슬롯은 최대 후보 수의 절반을 넘지 않는다

- 상태: 승인. 부분 대체 — 최대 후보 수 단계 값과 직접 입력 범위([ADR-0272](0272-the-input-token-budget-is-a-safety-ceiling-and-the-maximum-candidate-steps-set-the-digest-volume.md)) · 큰 슬롯 상한을 세는 단위([ADR-0260](0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md)) · 구독 밖 노출을 큰 슬롯만 맡는 것([ADR-0280](0280-events-of-followed-flows-outside-the-subscribed-topics-enter-one-per-flow-within-a-share-of-the-subscribed-seats-as-interest.md))
- 날짜: 2026-11-17
- 근거: [이슈 #176](https://github.com/Theia0382/yozm-news/issues/176) — 리드가 최대 후보 수(단계 · 직접 입력 · 최대값)와 큰 슬롯 수를 사용자 설정으로 정함. 구현 커밋 `641e7d4`(설정) · `b1b3161`(화면) · `b3cfac1`(상한)
- 관련: [ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)(결정 "구독 밖 노출은 큰 사건 슬롯이 맡는다" — 부분 대체: 큰 슬롯을 끈 사용자에게는 구독 밖 노출이 없다), [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)(큰 슬롯), [ADR-0208](0208-the-big-slot-takes-the-representatives-of-the-heaviest-events-of-each-big-storyline-row.md)(큰 슬롯에 넣는 기사), [ADR-0198](0198-feedback-recording-is-request-limited-per-session-user.md)(결과 "사용자 설정으로 열면 한도를 다시 본다" — 보충: 직접 입력의 상한 150 이 피드백 한도 안이다)

## 맥락

- 다이제스트 후보는 사용자 키로 LLM 공급자에 보내는 입력이다. 많을수록 입력 토큰(사용자 비용)이 는다.
> 부분 대체(첫 문장) — [ADR-0280](0280-events-of-followed-flows-outside-the-subscribed-topics-enter-one-per-flow-within-a-share-of-the-subscribed-seats-as-interest.md). 지금 상태: [digest Spec "후보 선별"](../specs/digest.md#후보-선별) digest-R52
- 큰 슬롯은 구독 밖 노출을 맡는다. 큰 스토리라인 하나가 사건 여럿을 넣으므로 슬롯 수와 최대 후보 수는 서로의 자리를 먹는다.

## 결정

> 부분 대체 — [ADR-0272](0272-the-input-token-budget-is-a-safety-ceiling-and-the-maximum-candidate-steps-set-the-digest-volume.md). 지금 상태: [user Spec "관심 설정"](../specs/user.md#관심-설정) user-R56
- **최대 후보 수는 사용자가 단계(적게 30 · 보통 60 · 많이 90)로 고르거나 직접 입력(10 ~ 150)한다.** 기본은 보통이다. 값은 `user` 의 설정 행에 두고 정본은 코드 상수다 — 칸에 범위 사본을 두지 않는다.
- **큰 슬롯 수는 사용자 설정(0 ~ 10, 기본 3)이다.** 0 이면 큰 슬롯을 넣지 않는다.
> 부분 대체 — [ADR-0260](0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md). 지금 상태: [digest Spec "후보 선별"](../specs/digest.md#후보-선별) digest-R19
- **큰 슬롯 기사는 최대 후보 수의 절반(내림)을 넘지 않는다.** 넘으면 행 순서로 하나씩 돌며 담아 행마다 고르게 줄인다. 남은 자리는 구독 후보가 쓴다.
- 범위는 저장할 때만 본다 — 범위를 나중에 좁혀도 이미 저장된 값을 읽는다.

## 결과

- 사용자가 자기 비용과 구독 밖 노출을 고른다. 관심 설정 화면에 "다이제스트 분량" 구획이 있다.
> 부분 대체("구독 밖 노출이 없고") — [ADR-0280](0280-events-of-followed-flows-outside-the-subscribed-topics-enter-one-per-flow-within-a-share-of-the-subscribed-seats-as-interest.md). 지금 상태: [digest Spec "후보 선별"](../specs/digest.md#후보-선별) digest-R52
- 큰 슬롯을 끈 사용자에게는 구독 밖 노출이 없고, 구독 제안의 재료(구독 밖 기사에 받은 좋아요)도 생기지 않는다.
- 최대 후보 수가 입력 토큰 예산보다 크면 예산이 먼저 자른다.
- 다이제스트 하나가 인용하는 서로 다른 기사는 많아야 150 이라 피드백 요청 제한(세션 사용자 300 / 10분 — [user Spec](../specs/user.md#요청-방어) user-R38)의 창 하나에 든다.
- 단계 값 · 범위 · 절반은 측정 전 목표다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 최대 후보 수를 상수로 둔다 | 사용자가 자기 비용을 모델 선택으로만 줄일 수 있다 |
| 큰 슬롯 수를 1 ~ 10 으로 둔다(끌 수 없음) | 구독 주제만 받고 싶은 사용자의 뜻을 막는다 — 막으려던 것은 관찰된 관심이 노출을 좁히는 되먹임이지 사용자의 명시 선택이 아니다 |
| 큰 슬롯 기사 수에 상한을 두지 않는다 | 적게(30) · 큰 슬롯 3 이면 큰 슬롯이 모든 자리를 써서 구독 후보가 없을 수 있다 |
| 큰 슬롯 기사 수를 사용자 설정 하나로 따로 둔다 | 손잡이가 셋이 되고 서로의 관계를 사용자가 계산해야 한다 |

## 후속 기록

- 2026-11-18: 결과 "관심 설정 화면에 '다이제스트 분량' 구획이 있다" — 같은 구획에 요약 길이가 더해졌다([ADR-0223](0223-the-digest-summary-length-is-a-user-setting-in-the-digest-volume-section.md)), 지금 상태는 [user Spec "관심 설정"](../specs/user.md#관심-설정) user-R56
- 2026-11-19: 결과 "큰 슬롯을 끈 사용자에게는 … 구독 제안의 재료(구독 밖 기사에 받은 좋아요)도 생기지 않는다" — 구독한 중분류와 함께 붙은 구독 후보도 구독하지 않은 중분류의 재료가 된다([ADR-0245](0245-a-topic-suggestion-is-made-when-the-current-interest-of-an-unsubscribed-subtopic-reaches-its-own-threshold.md)), 지금 상태는 [digest Spec "구독 제안"](../specs/digest.md#구독-제안) digest-R41
- 2026-11-19: 결과 "다이제스트 하나가 인용하는 서로 다른 기사는 많아야 150 이라 피드백 요청 제한 … 의 창 하나에 든다" — 인용은 사건마다 표본이라 서로 다른 인용 기사는 많아야 750 이고 피드백 요청 제한도 750 / 10분이다([ADR-0263](0263-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md)), 지금 상태는 [user Spec "요청 방어"](../specs/user.md#요청-방어) user-R38 · [feedback Spec](../specs/feedback.md#5-규칙과-불변식) feedback-R7
- 2026-11-21: 결과 "최대 후보 수가 입력 토큰 예산보다 크면 예산이 먼저 자른다"의 예산으로 자를 때 큰 슬롯 몫 — [ADR-0271](0271-when-the-input-token-budget-cuts-candidates-the-big-slot-keeps-at-most-half-of-what-is-left-and-uses-what-subscribed-events-leave.md) · [digest Spec "후보 선별"](../specs/digest.md#후보-선별) digest-R22
- 2026-11-22: 결과 "다이제스트 하나가 인용하는 서로 다른 기사는 많아야 150 이라 피드백 요청 제한 … 의 창 하나에 든다"의 근거 — [ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md) · [ADR-0272](0272-the-input-token-budget-is-a-safety-ceiling-and-the-maximum-candidate-steps-set-the-digest-volume.md) · [user Spec "요청 방어"](../specs/user.md#요청-방어) user-R38
