# ADR-0173: 스토리라인 잇기 기준 점수의 기본값은 평가 세트의 격자와 운영자 표본 검수로 정밀도를 앞세워 정한다

- 상태: 승인. 부분 대체 — 기준 점수의 기본값([ADR-0195](0195-storyline-linking-band-and-probability-defaults-chosen-for-precision-by-simulator-grid-evaluation-instance-and-operator-sample-review.md))
- 날짜: 2026-11-15
- 근거: [이슈 #126](https://github.com/Theia0382/yozm-news/issues/126) — 평가 세트로 기준 점수를 격자로 견주고, 실측 인스턴스를 옛 기본값과 새 기본값으로 다시 이어 표본을 검수함(옛 기본값의 표본은 운영자가 일부를 판정하고 나머지는 그 기준으로 미리 판정한 것, 새 기본값의 표본은 운영자가 모두 판정)
- 관련: [ADR-0121](0121-storyline-linking-scores-centroid-cosine-plus-idf-weighted-key-term-overlap-without-time-decay-jev-or-topic-tags.md)(결정 절 "가중치와 기준 점수의 값은 평가 세트로 정했다. 이 값들은 측정 전 목표다" 가운데 기준 점수 — 보충), [ADR-0132](0132-storyline-relations-are-judged-by-jev-on-candidate-pairs-and-linked-only-above-a-probability-threshold.md)(관계 판정 — 기준은 그대로), [ADR-0109](0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md)(기본값을 평가 세트와 운영자 표본 검수로 정한 선례), [ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)(근거 수치를 공개 문서에 적지 않는다)

## 맥락

- 잇기 기준 점수의 기본값은 평가 세트에서 고른 측정 전 목표였다([ADR-0121](0121-storyline-linking-scores-centroid-cosine-plus-idf-weighted-key-term-overlap-without-time-decay-jev-or-topic-tags.md)). 평가 세트는 며칠치이고, 스토리라인에 들어야 할 사건과 혼자 남아야 할 사건을 씨앗으로 잰다.
- 그 값으로 실측 인스턴스를 이으면 사건 수가 많은 스토리라인 가운데 여럿이 한 사안의 흐름이 아니라 같은 분야의 무관한 사건을 모은 묶음이었다(사건사고 · 재판 · 날씨처럼 날마다 비슷한 기사가 나오는 분야). 점수의 대부분이 사건 중심의 코사인이고, 기본 임베딩 모델에서는 같은 분야 기사의 코사인이 좁은 범위에 몰려 있다.
- 평가 세트에서 기준 점수를 내리면 재현율이 오르고 정밀도가 내리며, 올리면 그 반대다. 이웃한 값 사이의 F1 차이는 씨앗 단위로 다시 뽑은 구간 안이라 F1 로는 값을 가르지 못했다.
- 스토리라인은 타임라인 · 구독 · 다이제스트의 큰 슬롯의 재료다. 무관한 사건이 섞인 스토리라인은 화면에서 바로 드러나고, 스토리라인에 들지 못한 사건은 다이제스트 후보로 남는다.

## 결정

> 부분 대체 — [ADR-0195](0195-storyline-linking-band-and-probability-defaults-chosen-for-precision-by-simulator-grid-evaluation-instance-and-operator-sample-review.md). 지금 상태: [storyline Spec §7](../specs/storyline.md#7-설정)
- **기준 점수의 기본값은 0.86 이다.** 값의 정본은 설정 클래스와 [storyline Spec §7](../specs/storyline.md#7-설정)이다.
- **값은 평가 세트의 격자와 운영자 표본 검수로 정한다.** 격자는 옛 기본값의 아래와 위를 함께 견준다. 운영자 표본은 실측 인스턴스에서 뽑는다 — 기준 바로 위 점수 띠의 소속, 사건 수가 많은 스토리라인, 무작위 스토리라인, 소속 없는 사건을 씨앗을 밝혀 뽑고, 운영자가 소속마다 그 사건이 그 흐름에 드는지, 스토리라인마다 한 사안인지를 판정한다. 값을 바꾸면 실측 인스턴스를 그 값으로 다시 이은 뒤 같은 표본을 다시 뽑아 검수한다 — 검수하지 않은 값으로 정하지 않는다.
- **같은 품질로 보이는 값 가운데서는 정밀도를 앞세운다.** 평가 세트에서 정밀도가 크게 오르는 지점이고, 실측 인스턴스에서 큰 분야 모음이 흩어지는 값을 고른다. 표본의 판정은 제목만이 아니라 기사 요약문까지 보고 한다.
- **핵심 말 가중치 · 시간 창은 그대로 둔다.** 이번 측정은 기준 점수만 견줬다.
- 근거 수치는 공개 문서에 적지 않는다([ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)).

## 결과

- 이어져야 할 사건의 일부가 소속 없이 남고, 몇 주에 걸친 흐름의 앞뒤가 한 스토리라인에 남지 않는다. 재현율의 손실은 주로 사건이 어느 스토리라인에도 붙지 않는 데서 온다.
- 스토리라인 사이 관계는 이 손실을 메우지 못한다 — 관계는 스토리라인끼리 잇고, 소속 없는 사건을 되살리지 않는다.
- 새 기본값에서도 분야 모음이 남는다. 무관한 사건이 먼저 묶인 뒤 그 위에 한 사안의 흐름이 올라타는 스토리라인이 있고, 그런 스토리라인에서는 흐름 안 사건과 흐름 밖 사건의 소속 점수가 겹쳐 기준 점수로는 가르지 못한다. 잇기 점수가 같은 분야와 같은 사안을 가르지 못하는 것은 기준 점수가 아니라 점수의 한계다([미결 사항](../open-questions.md) 35).
- 기본값은 측정한 소스 구성과 기본 임베딩 모델에 맞춘 값이다. 다른 소스 구성이나 모델에서는 운영자가 표본을 검수해 맞춘다.
- 바꾼 기준은 그 뒤 판정에만 쓴다. 기본값이 바뀐 판으로 올린 인스턴스의 지난 소속은 그대로이고, 지난 소속을 새 기준으로 다시 잇는 기능은 없다.
- 14일을 넘는 전개와 시간 창의 적정성, 닫힌 사건이 섞인 잇기는 재지 못했다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 옛 기본값을 그대로 둔다 | 실측 인스턴스의 큰 스토리라인 가운데 여럿이 분야 모음이었고, 기준 바로 위 점수 띠의 소속은 틀린 것이 맞는 것보다 많았다(운영자의 기준으로 미리 판정한 표본) |
| 기준 점수를 내린다 | 평가 세트의 정밀도가 무너지고, 내린 값의 표본에서 스토리라인이 분야 단위로 불어났다 |
| 평가 세트의 F1 이 가장 높은 값을 고른다 | 이웃한 값 사이의 차이가 구간 안이라 가르지 못한다. 스토리라인에 들 사건만의 F1 과 혼자 남을 사건까지 넣은 F1 이 서로 다른 값을 가리킨다 |
| 더 높은 값으로 정밀도를 더 얻는다 | 정밀도의 이득이 구간 안이고, 재현율과 긴 흐름을 더 잃는다 |
| 관계 판정으로 재현율을 되찾는다는 전제로 정한다 | 측정에서 관계는 긴 흐름의 손실을 메우지 못했다 |
| 잇기의 애매한 점수 구간에 Jev 소속 판정을 둔다 | 잇기의 꼴을 바꾸는 일이고 판정의 품질 · 비교 대상 · 비용을 재지 않았다. 기본값을 정하는 일과 따로 본다 |

## 후속 기록

- 2026-11-17: 결정 "값은 평가 세트의 격자와 운영자 표본 검수로 정한다" — 띠 · 확률 기준에도 쓰고 앞에 시뮬레이터 격자를 둔다([ADR-0195](0195-storyline-linking-band-and-probability-defaults-chosen-for-precision-by-simulator-grid-evaluation-instance-and-operator-sample-review.md))
- 2026-11-17: 결과 "새 기본값에서도 분야 모음이 남는다" — 기준 바로 위의 분야 모음 소속을 띠 판정이 거른다([ADR-0194](0194-storyline-linking-asks-jev-only-for-band-candidates-and-reuses-recorded-probabilities-until-either-side-doubles.md) · [ADR-0195](0195-storyline-linking-band-and-probability-defaults-chosen-for-precision-by-simulator-grid-evaluation-instance-and-operator-sample-review.md))
- 2026-11-17: 검토한 대안 "잇기의 애매한 점수 구간에 Jev 소속 판정을 둔다" — [ADR-0194](0194-storyline-linking-asks-jev-only-for-band-candidates-and-reuses-recorded-probabilities-until-either-side-doubles.md)
