# ADR-0081: 사건 기준 · 저장 기준은 설정에 두고(기본값은 코드), 기준이 바뀌면 저장된 점수 · 확률 안에서 재판정한다

- 상태: 승인. 부분 대체 — 재판정 범위([ADR-0087](0087-closed-event-assignments-are-not-rejudged-and-closing-and-merging-are-not-undone.md))
- 날짜: 2026-11-06
- 근거: [이슈 #97](https://github.com/Theia0382/yozm-news/issues/97) — 사건 배정의 기준을 둘 곳과 재판정을 리드와 정하고 구현(`3f6dcd5` · `738c775`)
- 관련: [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)(결과 절 "운영자가 표본을 검수해 임계값을 조정한다"를 설정으로 구현한다, 후속 기록), [ADR-0055](0055-tag-and-store-thresholds-change-with-history-and-warn-instead-of-blocking.md)(태그 기준 · 저장 기준 — 저장 기준의 뜻이 같다), [ADR-0080](0080-event-centroids-and-key-terms-accumulated-with-article-counts-and-updated-incrementally.md)(옮길 때의 누적 갱신)

## 맥락

- 배정은 시간 창 · 위 기준 · 아래 기준 · 판정 확률 기준을 쓴다. 값은 측정 전 목표이고 측정 뒤 바뀐다. 코사인 분포는 임베딩 모델마다 달라, 모델을 바꾼 인스턴스 운영자가 기준을 맞출 길이 있어야 한다(결과 절 "운영자가 표본을 검수해 임계값을 조정한다" — [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)).
- 태그는 저장 기준 이상의 확률을 저장하고 태그 기준으로 조회할 때 판정해, 기준을 바꾸면 곧바로 모든 평가에 적용된다([ADR-0055](0055-tag-and-store-thresholds-change-with-history-and-warn-instead-of-blocking.md)). 배정은 사건 중심 · 기사 수를 고치는 누적 상태라 조회할 때 판정할 수 없다.
- 리드는 태그처럼 재판정이 있어야 하고, 사건 기준과 저장 기준을 나눠 저장된 확률 안에서 재판정하기를 요구했다.

## 결정

- **사건 기준(시간 창 · 위 · 아래 · 확률 기준)과 저장 기준은 설정이다.** 기본값의 정본은 설정 클래스이고(측정 전 목표), 운영자는 환경 변수로 바꾼다. 범위 · 관계를 어기면 기동하지 않는다.
- **배정은 저장 기준 이상인 후보의 점수와 판정 확률을 함께 남긴다.** 배정마다 판정에 쓴 기준을 기준 이력의 ID 로 남긴다 — 회차는 지금 기준을 이력에 등록하고(같은 값이면 그 행), 그 ID 를 쓴다.
> 부분 대체(둘째 문장) — [ADR-0087](0087-closed-event-assignments-are-not-rejudged-and-closing-and-merging-are-not-undone.md). 지금 상태: [event Spec §5](../specs/event.md#기준과-재판정) event-R18
- **재판정은 자동이다.** 같은 반복 작업이 배정 회차 다음에 지금 기준과 다른 기준으로 판정된 배정을 기간 제한 없이 다시 판정한다. 사건 소속 판정 · 임베더를 부르지 않고 저장된 점수 · 확률만 쓴다. 결과에 따라 같은 사건에 남기거나, 저장 후보인 다른 열린 사건으로 옮기거나, 새 사건으로 떼어 낸다. 그 기사가 연 사건은 떼어 내지 않는다. 옮길 수 없으면(옮길 사건이 열려 있지 않음 · 그 사건 모델 버전의 표현이 없음) 기준 ID 만 바꾼다.
- **띠 안 후보에 저장된 확률이 없으면 지금 배정을 유지한다.** 확률이 없는 것은 대개 산식으로 붙어 묻지 않았기 때문이다.

## 결과

- 모델을 바꾼 운영자가 재기동으로 기준을 맞출 수 있고, 바꾸면 지난 배정에도 반영된다. 배정마다 어떤 기준으로 판정했는지 남는다.
- 재판정은 저장값 안이다 — 아래 기준을 판정 때의 저장 기준 아래로 내리면 그 사이 점수의 후보는 저장되지 않아 보이지 않고, 창을 줄여도 저장된 후보는 그대로다(감쇠는 판정 때 값). 판정 뒤에 생긴 사건으로는 옮기지 못한다. 처음부터 다시 묶는 장치는 없다.
- 창이나 저장 기준만 바꿔도 모든 배정이 한 바퀴 다시 판정된다(결과는 그대로이고 기준 ID 만 바뀐다). 회차마다 상한이 있다.
- 기준 이력 테이블과 재판정 회차가 생긴다. 바꾸기 전 미리보기는 없다.
- 닫힌 사건 안의 배정을 재판정할지는 사건 닫기를 만드는 작업이 정한다.

## 검토한 대안

> 부분 대체("재판정 대상을 열린 사건이나 최근 기간으로 한정한다" 행) — [ADR-0087](0087-closed-event-assignments-are-not-rejudged-and-closing-and-merging-are-not-undone.md). 지금 상태: [event Spec §5](../specs/event.md#기준과-재판정) event-R18

| 대안 | 기각 이유 |
|---|---|
| 기준을 코드 상수로 둔다 | 모델을 바꾼 인스턴스 운영자가 새 이미지 없이 기준을 맞출 수 없다 |
| 운영자 설정(DB + 변경 이력, 태그 기준처럼) | 화면 · API · 이력 테이블이 생기는데, 배정은 누적 상태라 태그 기준처럼 미리보기를 줄 수 없어 얻는 것이 적다 |
| 재판정을 운영자가 기간을 정해 요청한다 | 설정을 바꾸고 요청을 잊으면 옛 기준이 남고, 배정에 어떤 기준으로 판정했는지 남지 않는다 |
| 재판정 대상을 열린 사건이나 최근 기간으로 한정한다 | 기준을 바꾸면 곧바로 반영되는 태그와 감각이 다르다. 일수에는 근거가 없다 |
| 재판정 때 Jev 를 다시 부른다 | 운영자 비용이 다시 들고, 저장된 확률 안에서 재판정한다는 요구와 다르다 |
| 띠 안 후보에 확률이 없으면 붙일 수 없는 것으로 본다 | 위 기준을 조금만 올려도 산식으로 붙었던 기사가 모두 새 사건으로 떨어져 사건이 쪼개진다 — 그 기사들은 실제로 가까웠다 |

## 후속 기록

- 2026-11-07: 결정 절 "재판정은 자동이다"의 연 사건을 아는 방법과 합쳐진 사건의 저장 후보 — [ADR-0085](0085-rejudgement-knows-opened-events-from-an-assignment-flag-and-reads-merged-candidates-as-survivors.md) · [event Spec §5](../specs/event.md#기준과-재판정) event-R18
- 2026-11-07: 결과 절 "닫힌 사건 안의 배정을 재판정할지는 사건 닫기를 만드는 작업이 정한다" — [ADR-0087](0087-closed-event-assignments-are-not-rejudged-and-closing-and-merging-are-not-undone.md) · [event Spec §5](../specs/event.md#기준과-재판정) event-R18
- 2026-11-11: 맥락 절 "값은 측정 전 목표이고 측정 뒤 바뀐다"와 결정 절 "기본값의 정본은 설정 클래스이고(측정 전 목표)"의 사건 기준 · 저장 기준 값 — [ADR-0109](0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md) · [event Spec §7](../specs/event.md#7-설정)
