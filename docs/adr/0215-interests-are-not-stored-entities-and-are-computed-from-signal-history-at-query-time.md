# ADR-0215: 관심사는 개체로 세우지 않고 신호 이력에서 조회 때 계산한다

- 상태: 승인
- 날짜: 2026-11-18
- 근거: [이슈 #187](https://github.com/Theia0382/yozm-news/issues/187) — 관심도 산식을 정하며 관심사를 저장 단위로 둘지 리드와 정함. 구현 커밋 `e6d32cf` · `1487ffe`
- 관련: [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(결정 "관심사를 개체로 세울지는 관심도를 설계할 때 정한다" — 보충), [ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)(관심도는 조회할 때 계산한다)

## 맥락

- 개인 쪽은 스토리라인 구독과 관심사 둘이다. 관심사(관심도 · 관심사 히스토리)는 `digest` 에 두고, 개체로 세울지는 관심도를 설계할 때 정한다([ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)).
- 관심도는 조회할 때 계산한다([ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)). 관심사 히스토리는 기간별 주제 관심도와 새로 생긴 관심 · 사라진 관심을 보여 준다. 주제는 태그 키 계보로 이어진다.

## 결정

- **관심사에 저장 행 · ID · 상태 전이를 두지 않는다.** 관심사는 신호 이력(좋아요 / 싫어요 · 구독 변경)과 노출 기록에서 (사용자, 주제 중분류, 기준 시각)마다 계산하는 값이다.
- 관심사 히스토리도 기간마다 같은 산식으로 계산해 견준다.

## 결과

- 산식 · 상수를 바꾸면 지난 기간도 새 산식으로 보인다. 그때 보인 값은 남지 않는다.
- 태그 체계가 바뀌어도 고쳐 쓸 행이 없다 — 계보로 이어 계산한다.
- 관심사 히스토리 화면은 기간 여럿을 매번 계산한다(비용 미측정). 새로 생긴 관심 · 사라진 관심은 계산 결과를 견주는 기준이 하나 더 필요하다 — 히스토리를 만드는 작업이 정한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 기간마다 관심도 스냅숏을 저장한다 | "조회할 때 계산"을 바꾼다. 산식을 바꾸면 옛 행과 새 행이 섞이고, 태그 체계가 바뀌면 행을 고쳐 써야 한다 |
| 관심사를 사용자가 다루는 개체로 세운다(주제 · 스토리라인 · 자유 텍스트를 묶은 이름 붙은 관심사) | 구독 · 자유 텍스트 관심사와 겹치는 새 기능이다 |

## 후속 기록

- 2026-11-18: 결정 "관심사 히스토리도 기간마다 같은 산식으로 계산해 견준다"의 기간 값 · 읽는 창 — [ADR-0225](0225-interest-history-values-read-only-the-window-before-each-reference-time-and-the-period-in-progress-is-measured-at-the-call.md), 지금 상태는 [digest Spec "관심사 히스토리"](../specs/digest.md#관심사-히스토리)
- 2026-11-18: 결과 "새로 생긴 관심 · 사라진 관심은 계산 결과를 견주는 기준이 하나 더 필요하다" — [ADR-0226](0226-new-and-lost-interests-are-neighbouring-periods-crossing-one-threshold-on-the-interest-value.md), 지금 상태는 [digest Spec "관심사 히스토리"](../specs/digest.md#관심사-히스토리)
- 2026-11-22: 결정 "관심은 저장하지 않는다" — 스토리라인 관심의 키가 되는 묶음(사용자 스토리라인)은 `user` 가 저장하고 내지만 관심 값은 여전히 조회 때 계산한다([ADR-0283](0283-storyline-interest-is-keyed-by-user-storyline-and-its-value-is-spread-to-each-top-for-selection.md)), 지금 상태는 [digest Spec "관심도"](../specs/digest.md#관심도) digest-R49
