# ADR-0110: 스토리라인 평가는 고정 덤프의 시스템 사건에 라벨을 붙이고, 무작위 씨앗 사건과 그 스토리라인의 기간 전수로 겹치는 묶음을 잰다

- 상태: 승인
- 날짜: 2026-11-11
- 근거: [이슈 #120](https://github.com/Theia0382/yozm-news/issues/120) — 라벨 단위 · 표본 설계 · 지표 · 결과 입력을 조사해 리드와 정하고 도구를 구현(`df0cfae` · `0bc45cb`)
- 관련: [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(결정 "겹치는 묶음의 평가 지표는 스토리라인을 구현할 때 정한다" — 보충), [ADR-0098](0098-label-rule-2-samples-hold-no-big-event-gold-and-big-event-precision-is-not-measured-without-marks.md)(결정 "큰 사건 정답의 단위와 표시는 사건보다 큰 단위를 설계할 때 함께 정한다"의 표시 — 보충), [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)(결과 "큰 판정 정답의 단위는 스토리라인이다"의 표시와 정밀도 — 보충), [ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md)(시트에 시스템 사건을 싣지 않는 결정 — 사건 묶기 평가의 것이라 바꾸지 않는다), [ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)(평가 데이터의 자리), [ADR-0106](0106-provisional-storyline-linking-opens-only-with-a-partner-event-and-adds-the-single-best-storyline-without-removal.md)(기준선), [ADR-0111](0111-storyline-gold-links-by-matter-with-mixed-events-on-their-central-happening-and-events-without-a-happening-following-their-matter.md)(정답 스토리라인의 범위)

## 맥락

- 스토리라인은 사건들을 잇고, 사건 하나가 스토리라인 여럿에 들며, 스토리라인 사이에 포함 · 이어짐이 있다([ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)). 잇기 방법과 그 기준은 정답을 붙인 표본으로 고른다.
- 스토리라인은 사건 ID 만 저장한다. 사건을 비우고 다시 묶으면 사건 ID 가 바뀐다.
- 스토리라인의 전개는 몇 주에 걸친다. 그 기간의 사건 전부에 정답을 붙일 수는 없다.
- 사건 묶기 평가의 B-cubed 는 기사 하나가 묶음 하나에 든다고 본다. 스토리라인은 기사 하나가 정답 · 시스템 묶음 여럿에 든다.
- 운영자 API 는 스토리라인을 내지 않는다.

## 결정

- **라벨은 고정 덤프의 시스템 사건에 붙인다.** 덤프 하나의 기사를 한 사건 묶기 기준으로 다시 묶어 고정하고, 그 덤프를 되살린 인스턴스에서 시트를 만든다. 스토리라인 평가는 주어진 사건 위의 잇기를 재므로, 시스템 사건이 평가 단위인 것은 평가 조건이다. 사건 묶기 평가가 시트에 시스템 사건을 싣지 않는 결정([ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md))은 사건 묶기를 잴 때의 것이다.
- **표본은 무작위 씨앗 사건과, 씨앗이 든 스토리라인의 기간 전수다.** 씨앗은 시트의 사건에서 씨앗 값으로 고정해 뽑고, 같은 씨앗 값으로만 늘린다. 라벨러는 씨앗의 스토리라인을 정하고 그 구성 사건을 기간 안에서 모두 찾는다. 사건 묶기 평가에서 빼는 범주의 사건도 모집단에 두고 "없음"으로 적는다.
- **주 지표는 확장 B-cubed F1 이다(Amigó 외 2009).** 씨앗 사건의 기사로 평균하고 사건마다 같은 무게를 준다 — 기사 가중은 보조다. 정답 스토리라인별 대응과, 며칠 떨어진 사건 쌍을 이었는지 보는 먼 쌍 재현율을 함께 낸다. 정답에 포함이 있으면 최상위 · 말단 · 모든 층 세 보기로 잰다.
- **관계는 대응시킨 스토리라인 쌍의 종류 · 방향으로, 큰 판정은 스토리라인 × 날짜로 잰다.** 대응은 자카드가 가장 큰 정답 스토리라인 하나이고, 시스템 스토리라인 가운데 그 정답과 겹치는 몫이 하한에 못 미치면 대응하지 않는다. 대응이 없는 시스템 스토리라인의 큰 표시는 틀림으로 센다. 큰 스토리라인 정답은 날마다 전수로 표시한다.
- **후보 방법의 결과는 기사 ID 로 적은 결과 파일로 받고, 측정은 운영자 API 를 부르지 않는다.** 정답은 덤프의 기사로 펼쳐 견준다. 같은 덤프 위의 결과와, 같은 계보 덤프에서 되살린 다른 실행의 결과를 같은 라벨로 잰다. 계보 덤프가 다른 결과는 재지 않는다.
- **결과와 견주어 라벨을 고치는 대조 검토는 첫 라벨을 시스템 결과 없이 끝낸 뒤에만 한다.** 고칠지는 라벨 기준으로 다시 판단하고, 시스템이 함께 두었다는 것은 근거가 아니다. 대조로 고친 사건 수를 결과 기록에 남긴다.

## 결과

- 도구는 사건 묶기 평가 도구 옆에 둔다(`tools/event-eval/storyline_eval.py`). 지표 상수(먼 쌍의 일수 · 대응 하한 · 부트스트랩)는 측정 전 목표이고, 결과 기록에 함께 남는다.
- 사건 묶기 기준이 바뀌면 새 덤프에 라벨을 다시 붙인다. 같은 덤프 안에서는 기간 · 씨앗을 늘려 보정한다.
- 운영 인스턴스의 스토리라인 결과를 결과 파일로 내는 길은 아직 없다.
- 다른 실행의 결과에는 사건 묶음의 차이가 잡음으로 섞인다. 결과 기록에 실행 종류(같은 덤프 · 다른 실행)를 남긴다.
- 라벨러가 구성 사건을 놓치면 시스템의 맞는 연결이 틀림으로 센다. 대조 검토는 견준 결과가 찾은 것만 드러낸다.
- 정답 스토리라인별 대응의 대상은 크기에 비례해 뽑힌 씨앗 스토리라인이라, 그 평균은 서술값이다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 라벨을 기사에 붙이고 시트에 시스템 사건을 싣지 않는다 | 라벨러가 정답 사건을 다시 만들어야 해, 사건 층이 라벨 작업 안에 되살아난다 |
| 정답 사건과 정답 스토리라인 두 층을 붙인다 | 양이 크고, 사건 묶기의 오류가 잇기의 평가에 섞인다 |
| 기간의 사건 전부에 라벨을 붙인다 | 몇 주 기간을 감당할 수 없다 |
| 라벨러가 고른 스토리라인만 전수로 적는다 | 과잉 연결을 재지 못하고, 고른 것이 지표를 정한다 |
| 확장 B-cubed 하나만 낸다 | 몇 주 전개를 잇는지와 구독 단위의 품질을 한 숫자로 볼 수 없다 |
| 모든 층을 겹친 정답 하나와 견준다 | 층마다 겹쳐 소속시키지 않는 시스템이 늘 깎인다 |
| 운영 인스턴스의 사건을 그대로 덤프로 뜬다 | 한 기간에 사건 묶기 기준 둘이 섞인다 |
| 결과 파일을 사건 ID 로 받는다 | 같은 덤프 위의 결과만 잴 수 있어, 판정 시점이 다른 실행을 같은 정답으로 견주지 못한다 |
| 측정이 운영자 API 로 사건 · 기사를 다시 읽는다 | 시트가 이미 담고 있고, 측정 때의 인스턴스가 덤프와 다르면 어긋난다 |

## 후속 기록

- 2026-11-13: 결정의 층 보기와 관계 지표 — 시스템 쪽도 포함을 닫아 보기마다 견주고, 양 끝이 같은 정답에 대응된 관계는 판정에서 뺀다. 관계 품질은 시스템 스토리라인 쌍의 직접 판정으로 잰다 — [ADR-0133](0133-storyline-evaluation-closes-system-containment-per-layer-view-and-measures-relations-by-direct-pair-judgement.md), 지금 상태는 [스토리라인 평가하기](../operations/storyline-evaluation.md)
