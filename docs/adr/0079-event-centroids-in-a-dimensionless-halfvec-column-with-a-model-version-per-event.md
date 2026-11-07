# ADR-0079: 사건 중심 벡터는 차원 없는 `halfvec` 에 사건마다 모델 버전과 함께 둔다

- 상태: 승인
- 날짜: 2026-11-06
- 근거: [이슈 #97](https://github.com/Theia0382/yozm-news/issues/97) — pgvector 0.8 에서 차원 없는 `halfvec` 컬럼 · 값마다 다른 차원 · 모델 버전별 식 · 부분 HNSW 인덱스를 실측하고, 사건 테이블을 구현(`1b2f83f`)
- 관련: [ADR-0027](0027-pgvector-for-vector-storage.md)(벡터 저장 — 결정 절 "벡터마다 임베딩 모델 버전을 기록한다" 항목의 둘째 문장을 사건 중심 벡터에 대해 이 ADR 이 부분 대체한다), [ADR-0075](0075-article-vectors-in-a-dimensionless-vector-column-one-row-per-model-version.md)(기사 벡터 — 결과 절의 사건 중심 벡터 문장을 이 ADR 이 부분 대체한다)

## 맥락

- 오래 남기는 벡터는 사건 중심 벡터(`halfvec`)이고, 모델을 바꾸면 새 컬럼을 단다고 정했다([ADR-0027](0027-pgvector-for-vector-storage.md)). 기사 벡터는 운영 모델의 차원에 근거가 없어 차원 없는 컬럼에 모델 버전마다 행을 두기로 바꿨다([ADR-0075](0075-article-vectors-in-a-dimensionless-vector-column-one-row-per-model-version.md)).
- 운영 임베딩 모델과 그 차원은 아직 정해지지 않았다. 로컬 대체물의 차원은 256 이다.
- pgvector 0.8 은 차원 없는 `halfvec` 컬럼을 받고, 값마다 차원이 달라도 된다. 장기 검색용 HNSW 인덱스는 모델 버전마다 차원을 정한 식(`centroid::halfvec(n)`)과 부분 조건으로 걸 수 있다(실측).

## 결정

- **사건 중심 벡터는 차원을 정하지 않은 `halfvec` 컬럼에 두고, 사건마다 그 사건을 연 기사 표현의 모델 버전을 둔다.** 후보는 지금 임베더의 모델 버전 사건만이다 — 거리를 재기 전에 모델 버전으로 거른다. 길이는 쓰기 전에 앱이 확인한다.

## 결과

- 운영 모델을 고르거나 바꿔도 사건 테이블의 마이그레이션이 없다. 로컬 대체물과 운영 모델의 사건이 한 컬럼에 함께 있을 수 있다.
- DB 가 차원을 지켜 주지 않는다 — 모델 버전 거르기와 쓰기 경로의 확인이 장치다.
- 기사 벡터도 비교할 때 `halfvec` 으로 바뀌어, 코사인이 반 정밀도 반올림만큼 흔들린다. 같은 입력이면 같은 값이다.
- 모델을 바꾼 뒤 열린 사건의 중심을 새 모델로 다시 계산하는 일은 정하지 않았다 — 새 모델의 기사는 새 사건을 연다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 차원을 정한 `halfvec(n)` 컬럼(ADR-0027 · ADR-0075 문면) | n 의 근거가 없어 운영 모델을 고를 때 새 컬럼 마이그레이션이 생기고, 로컬 대체물의 차원이 운영 모델에 묶이거나 개발과 운영의 컬럼이 달라진다 |
| 차원 없는 `vector`(`float4`) | 누적 평균의 반올림 오차가 줄지만, 오래 남기는 벡터를 `halfvec` 으로 두기로 한 결정과 달리 저장이 두 배다 |

## 후속 기록

- 2026-11-07: 결과 절 "운영 모델을 고르거나 바꿔도 사건 테이블의 마이그레이션이 없다"의 장기 검색 인덱스 — [ADR-0089](0089-long-term-hnsw-indexes-on-closed-events-created-per-model-version-at-runtime-by-the-worker-role.md) · [event Spec §5](../specs/event.md#사건-정리) event-R13
