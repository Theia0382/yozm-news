# ADR-0241: 선정 이유는 구독 주제가 아닌 인용에만 표시하고, 화면 이름은 구독 주제 · 관심 반영 · 큰 화제다

- 상태: 승인. 부분 대체 — 인용마다 선정 이유를 표시하는 것([ADR-0262](0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md))
- 날짜: 2026-11-18
- 근거: [이슈 #179](https://github.com/Theia0382/yozm-news/issues/179) — 수용 기준 "선정 이유 표시 — 화면 문구는 여기서 정한다(큰 슬롯 이름 포함)", 정함(이름 — 큰 슬롯 이름을 "큰 화제"로 고침, 개발 이름 "스토리라인"을 다이제스트와 그 설정 화면에 넣지 않으려고 · 구독 주제가 아닌 인용에만 표시). 구현 커밋 `7dbf1de` · `7fa1e6b`(관심 설정)
- 관련: [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(보충 — "화면 문구는 화면을 만들 때 따로 정한다"의 다이제스트 선정 이유 · 큰 슬롯), [ADR-0216](0216-the-interest-selection-reason-marks-subscribed-candidates-that-enter-only-by-interest-with-a-weight-above-neutral.md)(관련 — `INTEREST` 의 뜻)

## 맥락

- 선정 이유는 `SUBSCRIBED`(구독 주제) · `INTEREST`(구독 후보 가운데 관심도로 들어온 것) · `MAJOR_STORYLINE`(큰 슬롯)이다. 인용 대부분은 구독 주제다.
- "스토리라인"은 개발 이름이고 스토리라인 화면의 이름이다 — 스토리라인 화면과 큰 판정의 표시는 "큰 스토리라인"을 쓴다. 다이제스트만 읽는 사용자는 그 화면을 모를 수 있다.
- 다이제스트는 지난 날짜로도 보이고 기간이 72시간까지 길어질 수 있다.

## 결정

> 부분 대체 — [ADR-0262](0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md). 지금 상태: [digest Spec "다이제스트 조회"](../specs/digest.md#다이제스트-조회) digest-R48
- **인용의 선정 이유는 구독 주제가 아닌 것(관심 반영 · 큰 화제)에만 표시하고, 표시가 있으면 범례로 "표시가 없는 기사는 구독한 주제에서 왔다"를 알린다.**
- **화면 이름은 `SUBSCRIBED` "구독 주제" · `INTEREST` "관심 반영" · `MAJOR_STORYLINE` "큰 화제"다** — 관심 설정의 큰 슬롯도 "큰 화제"로 부른다. 스토리라인 화면 · 운영자 화면의 "큰 스토리라인"은 그대로다.

## 결과

- 구독 순위만으로는 들지 않았을 기사(관심도로 든 구독 기사 · 큰 화제)가 눈에 띈다. 같은 표시가 줄마다 반복되지 않는다.
- 다이제스트를 읽는 사용자는 "스토리라인"을 몰라도 큰 슬롯의 뜻을 안다. "큰"이 큰 판정과 말을 잇는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 모든 인용에 이유 표시 | 대부분이 구독 주제라 같은 표시가 줄마다 반복된다 |
| 큰 슬롯을 "큰 스토리라인"으로 | 개발 이름이 다이제스트 화면에 나온다 — 다이제스트만 읽는 사용자가 스토리라인을 먼저 알아야 한다 |
| "오늘의 화제" | 지난 날짜 화면 · 72시간 기간에서 "오늘"이 맞지 않는다 |
| `INTEREST` 를 "좋아요 반영"으로 | 관심도는 좋아요만이 아니라 구독 변경도 반영한다 |

## 후속 기록

- 2026-11-22: 맥락의 `INTEREST`(구독 후보 가운데 관심도로 들어온 것)의 뜻과 범례 문구 — [ADR-0280](0280-events-of-followed-flows-outside-the-subscribed-topics-enter-one-per-flow-within-a-share-of-the-subscribed-seats-as-interest.md) · [digest Spec "후보 선별"](../specs/digest.md#후보-선별) digest-R22 · R52
