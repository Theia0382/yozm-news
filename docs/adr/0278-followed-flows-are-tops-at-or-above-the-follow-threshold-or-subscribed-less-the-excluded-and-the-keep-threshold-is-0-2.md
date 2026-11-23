# ADR-0278: 관심 흐름은 스토리라인 관심이 0.35 이상이거나 구독한 흐름에서 거른 흐름을 뺀 것이고, 남길 기준은 0.2 다

- 상태: 승인. 부분 대체 — 관심 흐름의 구독한 흐름([ADR-0283](0283-storyline-interest-is-keyed-by-user-storyline-and-its-value-is-spread-to-each-top-for-selection.md)) · 판정을 읽는 화면용 조회([ADR-0287](0287-user-storyline-summaries-are-made-and-stored-by-user-with-the-users-key-and-digest-supplies-the-flow-standing.md))
- 날짜: 2026-11-22
- 근거: [이슈 #243](https://github.com/Theia0382/yozm-news/issues/243) — 개인 스토리라인 화면(탭 · 관심사 히스토리)에 보일 흐름은 사용자 키로 정리 정보를 만드는 흐름이라 그 기준이 곧 사용자 비용이다. 측정 기록으로 기준 하나 · 둘의 정리 호출 수를 세어 리드가 두 기준을 골랐다. 구현 커밋 `1e95385`
- 관련: [ADR-0277](0277-storyline-interest-is-counted-per-containment-tree-top-with-the-topic-formula-and-reactions-to-a-flow-seen-within-seven-days-go-to-the-storyline-only.md)(스토리라인 관심), [ADR-0226](0226-new-and-lost-interests-are-neighbouring-periods-crossing-one-threshold-on-the-interest-value.md)(보충 — 흐름의 생김 · 사라짐은 두 기준으로 본다), [ADR-0227](0227-the-interest-history-shows-subtopic-interest-and-the-users-own-topic-subscription-changes-only.md)(보충 — 관심사 히스토리에 보일 흐름의 기준), [ADR-0279](0279-storyline-interest-multiplies-the-candidate-rank-and-flows-at-or-below-the-exclusion-threshold-leave-subscribed-candidates-and-the-big-slot.md)(거름 기준)

## 맥락

- 스토리라인 관심은 꼭대기마다 −1 ~ 1 이다(ADR-0277). 주제의 관심 있음 기준 θ 는 0.2 이고 생김 · 사라짐은 기준 하나를 넘나드는 것이다(ADR-0226).
- 개인 스토리라인 화면은 보이는 흐름마다 사용자 LLM 으로 정리 정보를 만든다 — 보일 흐름이 많을수록 사용자 비용이 는다. 측정 기록에서 기준 0.2 하나면 14일에 사용자마다 10 ~ 33 흐름을 정리하고, 만들 기준 0.35 를 따로 두면 0 ~ 11 로 준다. 좋아요 한 번은 몫 1 이면 0.25 다.
- 구독한 흐름은 관심 값과 무관하게 사용자가 따라가겠다고 밝힌 흐름이다.

## 결정

> 부분 대체("지금 구독 중인 스토리라인의 꼭대기") — [ADR-0283](0283-storyline-interest-is-keyed-by-user-storyline-and-its-value-is-spread-to-each-top-for-selection.md). 지금 상태: [digest Spec "관심도"](../specs/digest.md#관심도) digest-R51
- **관심 흐름은 스토리라인 관심이 0.35 이상인 꼭대기와 지금 구독 중인 스토리라인의 꼭대기에서, 거른 꼭대기(거름 기준 이하)를 뺀 것이다.** 거름이 구독보다 앞선다.
- **남길 기준은 스토리라인 관심 0.2 다.** 한 번 보인 흐름은 관심이 남길 기준 이상인 동안 남는다.
> 부분 대체(둘째 절) — [ADR-0287](0287-user-storyline-summaries-are-made-and-stored-by-user-with-the-users-key-and-digest-supplies-the-flow-standing.md). 지금 상태: [`digest` digest-R51](../specs/digest.md#관심도)
- 판정과 상수는 `digest` 의 `domain` 에 두고, 화면용 조회는 그것을 쓰는 개인 스토리라인 화면이 연다.

## 결과

- 좋아요 한 번(0.25)으로는 정리를 만들지 않는다 — 두 번 이상이거나 구독해야 한다. 한 번 보인 흐름은 관심이 조금 내려가도 남아 기준 근처에서 보임과 사라짐이 오가지 않는다.
- 관심 흐름은 주제 구독 밖 관심 흐름의 길(ADR-0280)도 쓴다 — 정리를 만드는 흐름과 다이제스트가 따라가는 흐름이 같다.
- 구독했지만 거른 꼭대기가 된 흐름은 관심 흐름이 아니다. 구독했지만 남길 기준 아래로 내려간 흐름을 화면에 둘지는 개인 스토리라인 화면이 정한다.
- 남길 기준은 x 만 본다 — 남은 흐름의 생김 · 사라짐은 주제와 달리 두 기준(만들 · 남길)으로 갈린다.
- 0.35 · 0.2 는 측정 전 목표다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 기준 하나(0.2)로 보이고 사라진다 | 좋아요 한 번으로 보여 정리 호출이 많고, 기준 근처에서 보임 · 사라짐이 오간다 |
| 구독했거나 좋아요가 두 번 이상인 흐름 | 산식 밖 규칙이 하나 더 생기고 노출 · 감쇠를 보지 않아 사라질 기준을 따로 세워야 한다 |
| 구독한 흐름은 거른 꼭대기여도 관심 흐름이다 | 구독한 채 거듭 싫어요한 흐름의 사건을 새 길로 다시 데려온다 — 최근 신호(싫어요)가 오래된 신호(구독)를 이긴다 |

## 후속 기록

- 2026-11-23: 결과 "구독했지만 남길 기준 아래로 내려간 흐름을 화면에 둘지는 개인 스토리라인 화면이 정한다" — [ADR-0289](0289-shown-flows-are-user-storylines-with-a-summary-which-the-summary-job-creates-and-removes.md) · [`user` user-R69](../specs/user.md#사용자-스토리라인의-정리)
