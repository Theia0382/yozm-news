# ADR-0281: 사용자 스토리라인은 관심 신호를 받은 흐름이고, 씨앗에서 이어짐을 앞에서 뒤로 따라 닿는 꼭대기를 한 묶음으로 추적한다

- 상태: 승인. 부분 대체 — 겹치는 씨앗을 한 묶음으로 합치기([ADR-0294](0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md)) · 갈라질 때 잇기와 물려받기([ADR-0294](0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md))
- 날짜: 2026-11-22
- 근거: [이슈 #244](https://github.com/Theia0382/yozm-news/issues/244) — 스토리라인 구독과 스토리라인 관심이 이어진 뒤 흐름을 따라가지 않아, 앞 흐름을 구독하거나 거듭 싫어요한 사용자에게 뒤 흐름이 새 흐름으로 다시 왔다. 구독 · 관심을 물려주는 대신 그 값을 가진 개체를 세우기로 정했고, 측정 기록을 재생해 개체 수와 앞 · 뒤 흐름 반응의 부호를 확인했다. 구현 커밋 `c995bb8` · `a029668` · `3db16e1`
- 관련: [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(부분 대체 — 결정 "개인 쪽은 스토리라인 구독과 관심사 둘이다": 사용자 스토리라인 개체가 생기고 `user` 에 둔다), [ADR-0264](0264-the-storyline-tops-query-gives-each-event-its-containment-tree-roots-in-membership-score-order-without-the-scores.md)(꼭대기 조회는 이어짐을 따르지 않는다 — 그대로), [ADR-0282](0282-flow-tracking-points-to-a-user-storyline-which-user-handlers-store-under-the-settings-lock-while-readers-get-the-result-of-the-same-rule.md)(저장과 흐름 추적), [ADR-0283](0283-storyline-interest-is-keyed-by-user-storyline-and-its-value-is-spread-to-each-top-for-selection.md)(관심의 키)

## 맥락

- 공용 스토리라인 사이에는 포함(나무)과 이어짐(앞 흐름이 뒤 흐름을 낳았다) 두 관계가 있다. 꼭대기 조회는 포함만 따른다(ADR-0264) — 이어진 뒤 흐름은 다른 꼭대기다.
- 사용자에게 이어진 흐름은 한 흐름이다. 구독과 관심이 꼭대기에 붙어 있으면 뒤 흐름은 구독도 관심도 없는 새 흐름이 된다.
- 구독 · 관심을 뒤 흐름에 물려주면 해제와 물려주기가 어긋난다 — 뒤 흐름에서 해제해도 앞 흐름의 구독이 남아 다시 물려준다.
- 이어짐은 관계 판정이 맺고 기준 조정 · 합치기 · 풀기로 바뀐다.

## 결정

- **사용자 스토리라인은 그 사용자가 관심 신호를 준 흐름이다.** 신호는 좋아요 · 싫어요(되돌리기 포함 — 이력 행이 있으면)와 흐름 추적(구독 · 해제)이다. 노출은 만들지 않는다. 공용 스토리라인과 따로 `user` 가 갖는다.
- **씨앗은 관심 창 안에 반응을 받은 사건의 지금 꼭대기, 창 안에 사용자가 추적 · 해제한 스토리라인의 지금 꼭대기, 추적 중인 스토리라인(추적 씨앗)의 지금 꼭대기다.** 추적 씨앗은 창과 무관하다.
> 부분 대체(첫 문장 · 셋째 문장) — [ADR-0294](0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md). 지금 상태: [`user` user-R66](../specs/user.md#사용자-스토리라인)
- **추적 대상은 씨앗과, 씨앗에서 지금의 이어짐을 앞에서 뒤로 따라 닿는 꼭대기다.** 뒤에서 앞으로는 따르지 않는다 — 신호를 준 순간부터 잇는다. 추적 대상이 하나라도 겹치는 씨앗들은 한 사용자 스토리라인이다(갈라진 가지 전체가 한 묶음이고, 겹치면 합친다).
> 부분 대체(셋째 문장) — [ADR-0294](0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md). 지금 상태: [`user` user-R66](../specs/user.md#사용자-스토리라인)
- **공용 관계의 변화를 따른다.** 맞출 때마다 지금 관계로 묶음을 다시 낸다. 이어짐이 사라져 묶음이 갈라지면 저장한 개체는 겹치는 꼭대기가 가장 많은 조각이 잇고, 추적 중이었으면 다른 조각도 추적을 물려받는다 — 물려받은 것은 사용자의 신호가 아니다. 합쳐지면 ID 가 가장 작은 개체가 잇고 추적 시각은 가장 이른 것이다.
- **창 안에 신호가 없고 추적도 없으면 사라진다.** 관심 값이 0 인지가 아니라 신호가 있는지로 정한다.
- 꼭대기 단위의 이어짐 닫힘은 `storyline` 의 공개 조회 하나가 낸다. 묶기 · 잇기의 판정은 `user` 의 `domain` 에 둔다.

## 결과

- 앞 흐름을 추적하면 뒤 흐름도 추적 중이고, 뒤 흐름에서 해제하면 그 흐름의 추적이 꺼진다. 해제해도 신호가 창 안에 있는 동안 개체가 남아, 다시 추적하면 같은 개체다.
- 뒤 흐름만 신호를 준 사용자에게 앞 흐름은 그 사용자 스토리라인이 아니다.
- 이어짐 판정의 품질을 그대로 물려받는다 — 잘못 이어진 흐름은 한 묶음이 되고, 관계가 고쳐지면 갈라진다. 갈라진 조각의 추적은 사용자가 마음에 들지 않으면 해제한다.
- 물려받기는 저장하는 맞추기가 갈라짐을 본 때 일어난다 — 그 사이 갈라진 조각의 신호가 창 밖으로 나가면 그 조각은 물려받지 못한다.
- 추적 씨앗의 스토리라인이 지워져도 그 흐름이 남아 있으면 추적은 남는다.
- 사용자에게 드러나는 것(정리 · 탭)은 개인 스토리라인 화면이 정한다 — 이 개체는 저장과 추적만 맡는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 구독 · 관심을 뒤 흐름의 꼭대기에 물려준다 | 해제와 물려주기가 어긋나고, 물려준 값과 사용자가 준 값을 구분할 자리가 없다 |
| 이어짐을 방향 없이 따라 앞 흐름까지 한 묶음으로 본다 | 신호를 주기 전의 흐름까지 추적 · 관심에 든다 — 사용자는 신호를 준 순간부터의 흐름을 따라간다 |
| 추적 대상을 저장한 대로 두고 관계가 사라져도 남긴다 | 잘못 이어졌다가 고쳐진 흐름이 계속 한 묶음으로 남는다 |
| 갈라지면 추적을 한 조각에만 남긴다 | 어느 조각이 사용자가 따라가던 흐름인지 알 수 없다 — 남기고 사용자가 해제하게 한다 |
| 관심 값이 0 이 되면 지운다 | 좋아요와 싫어요가 상쇄된 흐름이 신호가 있는데 사라지고, 값은 저장하지 않아(ADR-0215) 지울 때를 따로 계산해야 한다 |
| 노출도 사용자 스토리라인을 만든다 | 받은 흐름마다 개체가 생긴다 — 사용자가 관심을 밝힌 흐름이 아니다 |

## 후속 기록

- 2026-11-23: 결과 "사용자에게 드러나는 것(정리 · 탭)은 개인 스토리라인 화면이 정한다" — [ADR-0289](0289-shown-flows-are-user-storylines-with-a-summary-which-the-summary-job-creates-and-removes.md) · [ADR-0290](0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md) · [`reader` reader-R13](../specs/reader.md#정리된-사용자-스토리라인)
