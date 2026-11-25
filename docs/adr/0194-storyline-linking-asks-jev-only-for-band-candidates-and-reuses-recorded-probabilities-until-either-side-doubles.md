# ADR-0194: 스토리라인 잇기는 띠 안 후보만 Jev 에 같은 사안의 전개인지 묻고, 물은 확률을 쌍마다 기록해 크기가 두 배가 될 때까지 다시 쓴다

- 상태: 승인. 부분 대체 — 평가기 일시 실패의 상한([ADR-0255](0255-storyline-linking-and-relation-queues-defer-transient-failures-until-a-retry-time-and-never-drop-them.md))
- 날짜: 2026-11-17
- 근거: [이슈 #169](https://github.com/Theia0382/yozm-news/issues/169) — 문구 후보를 운영자가 판정한 소속 표본으로 견주고, 잇기 시뮬레이터 · 평가 인스턴스 · 운영자 표본 검수로 띠를 재고 정함
- 관련: [ADR-0121](0121-storyline-linking-scores-centroid-cosine-plus-idf-weighted-key-term-overlap-without-time-decay-jev-or-topic-tags.md)(결정 "Jev 판정과 주제 태그를 쓰지 않는다"의 Jev 부분 · "기준 점수와 시간 창은 설정"의 기준 점수 — 부분 대체, 판정의 꼴 · 검토한 대안 "점수의 애매한 구간만 Jev 에 묻는다" — 보충), [ADR-0106](0106-provisional-storyline-linking-opens-only-with-a-partner-event-and-adds-the-single-best-storyline-without-removal.md)(결정 "기준 이상 가운데 점수가 가장 높은 하나" · "기준 이상인 이웃을 점수 순으로 보아 소속 없는 첫 사건" — 부분 대체), [ADR-0105](0105-storylines-link-incrementally-from-event-change-notifications-and-events-publish-changed-event-ids.md)(결정의 실패 상한 — 부분 대체, 소속마다 남기는 근거 — 보충), [ADR-0132](0132-storyline-relations-are-judged-by-jev-on-candidate-pairs-and-linked-only-above-a-probability-threshold.md)(결과 "잇기는 여전히 Jev 를 쓰지 않는다" — 부분 대체), [ADR-0071](0071-event-membership-judged-from-article-ids-with-questions-built-in-evaluation.md) · [ADR-0072](0072-event-membership-question-wording-as-one-code-constant-with-its-version-in-raw-records.md)(판정 꼴 · 문구 상수를 흐름 소속 판정에도 쓴다 — 보충), [ADR-0195](0195-storyline-linking-band-and-probability-defaults-chosen-for-precision-by-simulator-grid-evaluation-instance-and-operator-sample-review.md)(띠 · 확률 기준의 처음 값)

## 맥락

- 잇기는 사건 중심의 코사인과 핵심 말 겹침으로 정한 점수가 기준 이상이면 붙인다([ADR-0121](0121-storyline-linking-scores-centroid-cosine-plus-idf-weighted-key-term-overlap-without-time-decay-jev-or-topic-tags.md)). 같은 분야의 서로 다른 사건도 점수가 높아, 무관한 사건이 먼저 묶인 위에 한 사안의 흐름이 올라타는 분야 모음 스토리라인이 남았다. 그 소속은 기준 바로 위에 있어 기준을 올려도 흐름 안 사건과 함께 빠진다([ADR-0173](0173-storyline-linking-threshold-default-chosen-for-precision-on-an-evaluation-set-grid-and-operator-sample-review.md)).
- 기준을 정밀도 쪽으로 올리면서 이어져야 할 사건의 일부가 소속 없이 남았다.
- Jev 는 사건 소속 판정과 관계 판정을 이미 하고, 판정은 `evaluation` 의 공개 API 가 요청 조립 · 호출 · 원본 기록을 맡는다. 판정에는 운영자 비용이 들고, 잇기는 사건이 바뀔 때마다 다시 판정한다.
- 잇기 판정은 모듈 잠금을 잡은 트랜잭션 안에서 돈다. 외부 호출을 그 안에 두면 잠금을 쥔 채 기다린다.

## 결정

- **점수가 애매한 띠만 Jev 에 묻는다.** 기준은 아래 기준 · 위 기준 · 확률 기준 셋이다. 위 기준 이상은 묻지 않고 붙인다. 아래 기준 이상 위 기준 미만(띠)의 후보는 점수 순으로 몇 개까지 한 번에 묻고, 확률이 확률 기준 이상인 것 가운데 확률이 가장 높은 하나를 고른다(같으면 점수, 그다음 ID). 아래 기준이 위 기준과 같으면 띠가 비어 점수만으로 정한다. 띠는 위 기준의 아래 · 위를 함께 덮는다 — 기준 위의 분야 모음 소속을 거르고, 기준 아래에서 놓친 소속을 되찾는다.
- **붙이기와 짝 모두에 둔다.** 짝은 붙일 곳이 없고 소속이 없을 때만 본다 — 짝 띠는 붙이기 띠의 답을 받은 뒤에 묻는다. 지금 소속인 스토리라인도 띠 안이면 묻고, 거절돼도 소속을 빼지 않는다. 다시 넣기와 후보를 읽는 코사인 하한은 아래 기준에서 정한다.
- **질문은 "이 기사와 다음 기사들이 한 사안의 전개를 다루는가 — 비슷한 종류의 따로 일어난 일들이 아니라"다.** 판정할 사건은 대표 기사(제목 · 요약문)로, 후보 스토리라인은 판정할 사건과 가장 가까운 구성 사건 몇 개의 대표 기사 제목으로, 짝 이웃은 그 사건의 대표 기사 제목으로 서술한다. 문구는 분야 예시 없는 일반 원리로 쓰고, 운영자가 판정한 소속 표본에서 후보 문구를 견줘 골랐다.
- **판정은 `evaluation` 의 새 공개 API(흐름 소속 판정) 하나다.** `storyline` 은 판정할 기사 ID 와 후보 흐름마다 서술 기사 ID 를 넘기고, `evaluation` 이 요청을 조립 · 호출하고 판정 원본을 남긴다. 평가기 포트는 사건 소속 판정의 메서드를 문구만 바꿔 다시 쓴다 — 요청 꼴 · 실패 분류가 같다. 문구는 코드 상수 하나이고 판을 판정 원본에 남긴다.
- **물은 확률은 쌍마다 기록하고, 어느 쪽 크기든 물었을 때의 두 배가 되면 다시 묻는다.** 붙이기는 (사건, 스토리라인), 짝은 (사건, 이웃 사건)마다 한 행이고 이 기록이 확률의 정본이다. 크기는 판정할 사건의 기사 수와 상대의 크기(스토리라인은 구성 사건 수, 이웃은 기사 수)다. 스토리라인이 합쳐지면 합쳐질 쪽의 기록을, 사건이 지워지거나 합쳐지면 그 사건의 기록을 지운다. 기준을 바꿔도 기록은 그대로 쓴다.
- **묻기는 트랜잭션 밖이다.** 회차는 잠금 안에서 판정을 시도하고, 띠 안 후보의 기록이 모자라면 아무것도 쓰지 않고 물을 것을 낸다. 트랜잭션 밖에서 묻고, 짧은 트랜잭션에서 기록한 뒤 다시 판정한다 — 사건 하나에 몇 번까지, 남으면 점유를 풀어 다음 회차가 잇는다.
> 부분 대체(둘째 문장) — [ADR-0255](0255-storyline-linking-and-relation-queues-defer-transient-failures-until-a-retry-time-and-never-drop-them.md). 지금 상태: [storyline Spec §5](../specs/storyline.md#잇기-대기와-회차) storyline-R4
- **확률을 얻지 못하면 기다린다.** 평가기 일시 실패는 그 사건의 판정 가능 시각을 미루고 실패 수를 올리며, 상한이면 대기에서 뺀다. 평가기를 쓸 수 없으면 회차를 멈춘다. 판정할 기사를 평가기가 거부하면 확률 없이 기록하고, 그 띠 후보는 붙이지 않는다 — 같은 기사라 다시 보내도 같다. 확률 없는 기록으로는 고르지 않는다.
- **소속의 근거에 판정 확률과 판정 원본 ID 를 더한다.** 띠 안에서 판정으로 정한 소속만 갖는다. 운영자 조회 · 화면이 보인다.
- **설정 이름은 사건 배정과 같은 꼴(아래 기준 · 위 기준 · 확률 기준)이고, 옛 기준 점수 설정이 남아 있으면 기동하지 않는다.** 오류에 새 설정 이름을 적는다. 옛 기준 이력은 띠가 빈 기준으로 옮긴다.

## 결과

- 기본값(띠가 있는 기준)이면 잇기에 Jev 비용이 든다 — 운영자 비용이다. 물은 기록으로 같은 쌍을 거듭 묻지 않지만, 하루 비용은 운영에서 잰다.
- 잇기가 Jev 를 기다린다. 묻는 사건은 회차 안에서 차례로 판정되어 잇기 처리량이 준다.
- 판정은 문구 판이 바뀌면 달라진다. 물은 기록에는 판이 없다 — 문구를 바꾸는 변경은 물은 기록을 비운다.
- 띠 밖의 소속은 점수만으로 정한다. 위 기준보다 높은 점수의 분야 모음 소속은 남고, 아래 기준보다 낮은 점수의 소속은 되찾지 못한다.
- 한 사건 · 한 쌍의 판정이 Jev 의 답에 따라 갈릴 수 있다 — 다시 물을 때까지 그 답을 쓴다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 지금 기준 아래에만 띠를 둔다 | 분야 모음 소속은 기준 위에 있어 거르지 못한다 |
| 붙이기에만 둔다 | 분야 모음은 짝으로 먼저 열린다 |
| 후보 스토리라인을 대표 사건 하나나 최근 사건으로 서술한다 | 판정할 사건과 다른 갈래의 사건이 서술되면 같은 흐름이라도 거절된다 |
| 평가기 포트에 흐름 판정 메서드를 따로 둔다 | 요청 조립 · 실패 분류 · 확인 요청이 두 벌이 된다 — 지금 메서드가 문구를 받는다 |
| 평가기 실패 때 점수만으로 정한다 | 같은 사건이 평가기의 상태에 따라 다르게 이어진다 |
| 사건이 바뀔 때마다 띠 안 후보를 다시 묻는다 | 사건이 자랄 때마다 같은 쌍을 거듭 물어 비용이 커진다 |
| 판정할 기사 거부를 일시 실패처럼 미룬다 | 같은 기사라 다시 보내도 같고, 상한까지 시간만 쓴다 |
| 옛 기준 점수 설정을 위 기준의 뜻으로 남긴다 | 이름(기준 점수)과 뜻(묻지 않고 붙이는 위 기준)이 어긋난다 |
| 옛 설정을 무시하고 기동한다 | 기준을 덮어쓴 인스턴스가 모르게 기본값으로 돌아간다 |
| 기사 한 건짜리 사건끼리는 짝을 짓지 않는다 | 큰 스토리라인의 기사 한 건짜리 사건 비율이 사건 전체보다 낮았고, 표본 검수에서도 따로 다룰 근거가 나오지 않았다 |

## 후속 기록

- 2026-11-25: 결정 "판정은 evaluation 의 새 공개 API(흐름 소속 판정) 하나다"의 이름 — [ADR-0326](0326-development-names-call-a-shared-storyline-storyline-a-user-storyline-user-storyline-and-a-judgement-candidate-of-unknown-kind-candidate.md) · [evaluation Spec §3](../specs/evaluation.md#3-공개-인터페이스)
