# ADR-0214: 스토리라인 구독은 사건 대표 기사의 주제 몫으로 주제별 관심도에 든다

- 상태: 승인. 부분 대체 — 사건 대표 기사의 주제로 몫을 세는 것([ADR-0255](0255-event-topics-and-regions-are-keys-on-at-least-half-of-evaluated-members-importance-is-the-maximum-and-digest-counts-them-at-query-time.md))
- 날짜: 2026-11-18
- 근거: [이슈 #187](https://github.com/Theia0382/yozm-news/issues/187) — 스토리라인 · 사건 · 평가의 공개 조회로 스토리라인의 주제를 모을 수 있음을 대조하고, 모으는 규칙을 리드와 정함. 구현 커밋 `e6d32cf` · `1487ffe`
- 관련: [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(결정 "주제는 스토리라인 층 밖이다"와 미뤄 둔 스토리라인의 주제 — 보충), [ADR-0222](0222-interest-per-subtopic-is-smoothed-reactions-over-exposures-with-a-half-life-and-exposures-are-all-ready-citations.md)(관심도 산식 — 구독 변경의 몫)

## 맥락

- 주제는 기사의 분류이고 스토리라인 층 밖이다([ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)). 스토리라인에 주제 태그를 저장하면 사건이 붙고 합쳐질 때마다, 기사 태그가 체계 · 기준 변경으로 바뀔 때마다 다시 맞춰야 한다.
- 스토리라인 구독 변경은 관심 신호다. 주제별 관심도에 넣으려면 스토리라인의 주제가 필요하다. 그 재료는 기사 단위라 `storyline` 은 직접 모으지 못한다.
- 스토리라인은 사건 여럿이고 사건마다 보도량이 크게 다르다. 여러 주제에 걸친 스토리라인이 있다.

## 결정

> 부분 대체(둘째 · 셋째 문장) — [ADR-0255](0255-event-topics-and-regions-are-keys-on-at-least-half-of-evaluated-members-importance-is-the-maximum-and-digest-counts-them-at-query-time.md). 지금 상태: [digest Spec "관심도"](../specs/digest.md#관심도) digest-R25
- **스토리라인에 주제를 저장하지 않고, 관심도를 계산할 때 `digest` 가 모은다.** 지금 그 스토리라인의 닫은 사건마다 대표 기사 하나를 읽고, 평가가 있는 대표의 주제 중분류(활성 키)로 몫을 센다 — 몫 = 그 중분류가 붙은 대표 수 ÷ 대표 수. 미분류 대표도 분모에 든다.
- **구독 변경 하나는 그 몫만큼 나뉘어 주제마다 더해진다.** 그 주제로 볼 기준 몫은 두지 않는다.
- 원인이 사용자인 변경만 신호다. 지금도 있는 스토리라인(합쳐졌으면 남는 쪽)의 변경만 센다 — 지워진 스토리라인은 주제를 모을 수 없다.

## 결과

- 사건마다 한 번 세어 보도량이 많은 사건에 끌리지 않고, 읽기가 사건 수로 묶인다.
- 스토리라인이 자라면 같은 구독 변경의 몫이 바뀐다 — 조회할 때 계산한다.
> 부분 대체(아래 항목의 둘째 문장과 그다음 항목) — [ADR-0255](0255-event-topics-and-regions-are-keys-on-at-least-half-of-evaluated-members-importance-is-the-maximum-and-digest-counts-them-at-query-time.md). 지금 상태: [digest Spec "관심도"](../specs/digest.md#관심도) digest-R25
- 스토리라인 구독 하나는 주제마다 몫(1 이하)만큼만 더해, 하위 중분류마다 1 로 세는 대분류 구독보다 주제 하나를 더 움직이지 않는다. 주제가 여럿 붙은 대표가 있으면 몫의 합은 1 을 넘을 수 있다.
- 대표가 없거나 평가가 없는 사건, 닫힌 사건이 아직 없는 스토리라인은 주제를 보태지 않는다.
- 스토리라인을 주제로 거르는 화면이 생기면 저장을 다시 본다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 스토리라인 구독을 주제별 관심도에 넣지 않는다 | 스토리라인을 구독해 보인 관심이 다이제스트 순위에 닿지 않는다 |
| 구독한 스토리라인의 기사를 후보 순위에서 직접 올린다 | 주제별 관심도와 다른 새 기능이다 — 가중치 · 선정 이유 · 다양성 몫과의 관계를 따로 정해야 한다 |
| 스토리라인 기사 전부의 주제로 센다 | 보도량이 많은 사건에 끌리고 읽기가 기사 수만큼 커진다 |
| 주제마다 기준 몫을 넘어야 그 주제로 본다 | 기준 상수가 하나 더 생긴다 — 몫으로 나누면 필요 없다 |
