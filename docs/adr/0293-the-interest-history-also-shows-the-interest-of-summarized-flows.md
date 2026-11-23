# ADR-0293: 관심사 히스토리는 정리된 흐름의 관심도 보인다

- 상태: 승인. 부분 대체 — 흐름 행의 값을 읽는 꼭대기([ADR-0294](0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md))
- 날짜: 2026-11-23
- 근거: [이슈 #245](https://github.com/Theia0382/yozm-news/issues/245) — 관심사 히스토리가 주제의 관심만 보여, 흐름에 쌓인 관심은 어디에도 보이지 않았다. 리드가 흐름을 더하고 생김 · 사라짐의 기준을 정했다. 구현 커밋 `0590ea1` · `e16498b` · `27a214d`
- 관련: [ADR-0227](0227-the-interest-history-shows-subtopic-interest-and-the-users-own-topic-subscription-changes-only.md)(부분 대체 — 단위에 정리된 흐름이 더해진다), [ADR-0226](0226-new-and-lost-interests-are-neighbouring-periods-crossing-one-threshold-on-the-interest-value.md)(보충 — 흐름의 생김 · 사라짐은 두 기준), [ADR-0278](0278-followed-flows-are-tops-at-or-above-the-follow-threshold-or-subscribed-less-the-excluded-and-the-keep-threshold-is-0-2.md)(만들 기준 · 남길 기준), [ADR-0289](0289-shown-flows-are-user-storylines-with-a-summary-which-the-summary-job-creates-and-removes.md)(보이는 흐름)

## 맥락

- 관심사 히스토리의 단위는 주제 중분류이고, 생김 · 사라짐은 이웃한 두 기간이 기준 하나를 넘나드는 것이다.
- 스토리라인 관심은 꼭대기마다 계산하고, 사용자 스토리라인의 값은 그 꼭대기마다 같다.
- 사용자에게 흐름은 정리가 있을 때만 이름이 있다.

## 결정

> 부분 대체(둘째 문장) — [ADR-0294](0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md). 지금 상태: [`reader` reader-R8](../specs/reader.md#관심사-히스토리)
- **관심사 히스토리는 지금 정리된 흐름마다 기간별 관심을 한 행으로 보인다.** 값은 그 흐름의 추적 대상 가운데 ID 가 가장 작은 꼭대기의 스토리라인 관심이다.
- **흐름의 생김은 관심이 만들 기준 이상이 된 기간, 사라짐은 보이던 흐름의 관심이 남길 기준 아래로 내려간 기간이다.** 기준은 관심 흐름의 것 그대로다.
- **추적은 생김 · 사라짐에 넣지 않는다.** 관심 값만으로 본다.
- **주제 신호가 없어도 정리된 흐름이 있으면 흐름의 행을 보인다.**

## 결과

- 추적해서 정리된 흐름은 관심 값이 만들 기준에 닿지 않으면 행만 있고 생김이 없다.
- 정리가 지워진 흐름은 지난 기간의 행에서도 사라진다.
- 주제 표와 흐름 표는 따로다 — 흐름의 관심은 주제의 관심에 더해지지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 신호가 있는 모든 꼭대기를 행으로 둔다 | 이름 없는 행이 생기고, 사용자 스토리라인 하나가 여러 행으로 나뉜다 |
| 주제와 같이 기준 하나로 생김 · 사라짐을 본다 | 탭에 보이는 기준(만들 기준 · 남길 기준)과 히스토리의 표시가 어긋난다 |
| 추적을 생김으로 친다 | 추적 이력은 추적 중인 흐름 화면에 이미 있다 |
