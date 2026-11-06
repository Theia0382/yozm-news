# ADR-0078: 사건 소속 판정에 실패해 끝낸 기사는 실패 표지를 단 새 사건으로 연다

- 상태: 승인
- 날짜: 2026-11-06
- 근거: [이슈 #97](https://github.com/Theia0382/yozm-news/issues/97) — 사건 배정을 구현(`3f6dcd5`)
- 관련: [ADR-0058](0058-evaluator-failures-in-three-kinds-and-unavailable-evaluator-skips-article-retries.md)(기사 거부는 "실패로 남긴다" — 배정에서 그 뜻을 이 ADR 이 정한다, 후속 기록), [ADR-0077](0077-event-assignment-queue-judged-one-article-at-a-time-with-evaluation-failure-kinds.md)(판정 실패로 끝내는 갈래), [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)(사건 중심 배정)

## 맥락

- 애매한 구간의 기사가 사건 소속 판정을 받지 못하고 끝나는 경우가 있다 — 기사 거부, 일시 실패의 재시도 소진([ADR-0077](0077-event-assignment-queue-judged-one-article-at-a-time-with-evaluation-failure-kinds.md)).
- 평가 작업은 이런 기사를 "실패로 남긴다"([ADR-0058](0058-evaluator-failures-in-three-kinds-and-unavailable-evaluator-skips-article-retries.md)). 배정에서 실패로 남긴다는 것이 무엇인지 정해야 한다.
- 사건을 읽는 쪽(사건 단위 중복 제거 · 큰 사건 집계 · 운영자 화면)은 표현이 있는 기사에 사건이 있다고 보면 단순하다. 사건 병합은 사건을 합치기만 하고 기사를 떼어 내지 않는다.

## 결정

- **판정 실패로 끝낸 기사는 그 기사 하나로 새 사건을 연다.** 배정의 정한 것(`decided_by`)에 판정 실패(`JUDGEMENT_FAILED`)를 남긴다. 점수는 가장 높은 후보의 점수이고, 후보는 확률 없이 남긴다.

## 결과

- 표현이 있는 기사는 모두 사건이 있다 — 사건을 읽는 쪽에 "사건 없음" 갈래가 없다.
- 실패는 배정의 표지로 남아 운영자 화면이 보일 수 있다. 같은 기사를 다시 판정하지 않는다.
- 같은 사건의 기사가 따로 떨어진 사건이 될 수 있다 — 병합 전까지 사건 크기를 적게 센다. 기준이 바뀌면 재판정이 저장된 후보로 다시 판정한다([ADR-0081](0081-event-assignment-criteria-in-settings-and-rejudgement-within-stored-scores.md)).

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 배정 없이 실패 행으로 남긴다 | 사건 없는 기사가 생겨 사건을 읽는 모든 곳이 "사건 없음"을 다뤄야 하고, 실패 행의 보존 · 운영자 처리가 새로 생긴다 |
| 산식 점수가 가장 높은 후보에 붙인다 | 애매한 구간은 산식으로 확신할 수 없는 구간이라 잘못 붙을 수 있고, 잘못 붙은 기사가 중심 · 핵심 말을 끌어 사건이 번진다. 병합은 떼어 내지 않아 메울 장치가 없다 |
