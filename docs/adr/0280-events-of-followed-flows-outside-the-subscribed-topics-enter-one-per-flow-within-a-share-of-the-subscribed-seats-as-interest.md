# ADR-0280: 주제 구독 밖에 있는 관심 흐름의 사건은 흐름마다 하나, 구독 사건 자리의 몫 상한 안에서 `INTEREST` 로 든다

- 상태: 승인
- 날짜: 2026-11-22
- 근거: [이슈 #243](https://github.com/Theia0382/yozm-news/issues/243) — 관심 흐름의 소식이 구독 주제 밖이라 빠지는 경우가 측정 표본(다이제스트 56 개)에서 9 쌍이었다. 같은 흐름이면 태그가 비슷하겠지만 어느 정도 보장되는 것이 좋고, 관심 흐름이 다이제스트를 많이 차지하지 않게 방어적으로 두며, 구독 주제에 걸리는 사건은 지금처럼 순위로 다투게 하기로 정했다. 구현 커밋 `c9274d3` · `b9af18f`
- 관련: [ADR-0278](0278-followed-flows-are-tops-at-or-above-the-follow-threshold-or-subscribed-less-the-excluded-and-the-keep-threshold-is-0-2.md)(관심 흐름), [ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)(부분 대체 — 구독 밖 노출은 큰 사건 슬롯만 맡는다), [ADR-0209](0209-maximum-candidates-and-big-slots-are-user-settings-and-the-big-slot-takes-at-most-half.md)(부분 대체 — 큰 슬롯을 끈 사용자의 구독 밖 노출), [ADR-0260](0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md)(부분 대체 — `INTEREST` 를 구독 사건으로 한정한 것), [ADR-0216](0216-the-interest-selection-reason-marks-subscribed-candidates-that-enter-only-by-interest-with-a-weight-above-neutral.md)(부분 대체 — `INTEREST` 는 구독 후보), [ADR-0241](0241-selection-reasons-mark-only-citations-outside-subscribed-topics-named-interest-and-big-topic.md)(보충 — `INTEREST` 범례 문구), [ADR-0271](0271-when-the-input-token-budget-cuts-candidates-the-big-slot-keeps-at-most-half-of-what-is-left-and-uses-what-subscribed-events-leave.md)(예산 자르기 순서)

## 맥락

- 구독은 사용자가 정한 필터이고 구독 밖 노출은 큰 슬롯이 맡는다(ADR-0028 · ADR-0209). 선정 이유 `INTEREST` 는 중립 순위에는 없고 관심도로 들어온 구독 후보다(ADR-0216).
- 사용자는 다이제스트만 보고 흐름을 따라간다. 구독하거나 거듭 좋아요한 흐름의 소식이 사건의 주제가 구독 밖이라는 이유로 큰 판정일 때만 오면, 흐름 신호가 소식을 데려오지 못한다.
- 관심 흐름의 사건 대부분은 구독 주제 안이라 순위로 다툰다 — 구독 밖이라 빠지는 경우는 적다.

## 결정

- **관심 흐름의 사건 가운데 구독 행과 겹치지 않는 사건은 새 길로 후보가 된다.** 사건 제외(지역 · 싫어요 · 거른 꼭대기)는 같다. 큰 슬롯이 쓴 사건은 빠진다. 구독 행과 겹치는 사건은 새 길을 타지 않고 구독 필터의 순위로 다툰다.
- **흐름마다 한 사건, 합은 구독 사건 자리 × 0.2 를 내림한 수까지, 구독 사건 자리에서 먼저 쓴다.** 순위가 높은 사건부터 훑어 아직 대표가 없는 관심 흐름을 가진 사건을 담고 그 사건의 관심 흐름을 모두 채운 것으로 친다. 관심 흐름에 그 기간 사건이 없으면 자리를 차지하지 않는다.
- **새 길 사건의 선정 이유는 `INTEREST` 다.** 중립 순위에는 새 길이 없다.
- 입력 토큰 예산으로 자를 때는 구독 사건의 꼬리를 먼저, 그 다음 새 길 사건의 꼬리를 뺀다.

## 결과

- 큰 슬롯을 끈 사용자에게도 관심 흐름의 구독 밖 소식이 온다. 구독 사건이 새 길 사건 수만큼 준다 — 큰 슬롯 사건은 최대 후보 수의 절반까지라 구독 사건의 자리는 그 나머지 이상이고, 새 길은 보통(40)이면 4 개 이상, 적게(20)면 2 개 이상까지 쓸 수 있다.
- `INTEREST` 가 "구독 주제 가운데 관심도로 든 사건"과 "구독 주제 밖 관심 흐름의 사건"을 함께 뜻한다 — 화면 범례가 둘을 함께 말하고, 선정 이유별 반응 측정에서 둘이 섞인다.
- 새 길 사건은 구독 행 밖이라 구독 제안(digest-R41)의 노출 재료가 된다 — 관심 흐름의 주제가 구독 제안으로 이어질 수 있다.
- 몫 0.2 는 측정 전 목표다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 새 길을 두지 않는다 — 흐름 관심은 순위 · 거름 · 큰 슬롯에만 | 구독한 흐름의 소식도 주제 구독 밖이면 큰 판정일 때만 온다 |
| 관심 흐름의 사건을 구독 후보에 더해 순위로만 다툰다(상한 없음) | 관심 흐름이 많은 사용자에게서 구독 사건 자리를 많이 차지할 수 있다 |
| 새 선정 이유(구독한 흐름)를 둔다 | 저장 제약 · 열거 · 배지 정렬 · API 명세 · 화면 배지가 늘고, 관심으로 들어왔다는 뜻은 `INTEREST` 와 같다 |
| 상한 없이 다양성 몫이 남긴 자리에서만 담는다 | 구독 후보가 자리를 다 채우는 날(대부분)에는 들지 못해 누락을 막지 못한다 |
| 고정 수(예 3)까지 담는다 | 최대 후보 수 20 인 사용자에게는 몫이 크고 60 인 사용자에게는 작다 |
| 관심 흐름의 사건을 구독 주제 안 · 밖 모두 새 길로 담는다 | 구독 주제 안의 사건은 이미 순위로 다투고, 같은 사건을 두 길로 다루면 자리 계산이 복잡해진다 |

## 후속 기록

- 2026-11-22: 관심 흐름의 재료 — 추적 중인 사용자 스토리라인의 모든 꼭대기라, 추적한 흐름이 이어진 뒤 흐름의 사건도 이 길로 든다. 규칙은 그대로다([ADR-0283](0283-storyline-interest-is-keyed-by-user-storyline-and-its-value-is-spread-to-each-top-for-selection.md)), 지금 상태는 [digest Spec "관심도"](../specs/digest.md#관심도) digest-R51 · R52
