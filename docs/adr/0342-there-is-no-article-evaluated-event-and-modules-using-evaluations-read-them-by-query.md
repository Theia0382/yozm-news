# ADR-0342: 평가 결과 이벤트 `ArticleEvaluated` 는 두지 않고, 평가를 쓰는 모듈은 조회로 읽는다

- 상태: 승인
- 날짜: 2026-11-26
- 근거: [이슈 #268](https://github.com/Theia0382/yozm-news/issues/268) — 기점 `4703c71` 에서 `ArticleEvaluated` 를 코드 · 문서 전체에서 찾으니 코드에는 0건이고 문서 셋(`overview` §5 · tag Spec · ADR-0112)에만 있었다. 평가를 읽는 모듈(`digest` · `event` · `admin`)의 import 를 대조했다
- 관련: [ADR-0112](0112-tags-split-into-a-tag-module-and-evaluation-is-the-model-gateway-that-assembles-calls-and-records-each-judgement.md)(평가 결과를 알리는 `ArticleEvaluated` 는 `tag` 가 낸다는 항 — 부분 대체), [ADR-0207](0207-digest-candidates-enter-a-period-by-first-evaluation-time-with-a-published-time-floor.md)(다이제스트 후보는 첫 평가 시각으로 기간에 든다)

## 맥락

- ADR-0112 는 `tag` 를 떼면서 평가 결과를 알리는 `ArticleEvaluated` 를 `tag` 가 낸다고 적었다. 발행 시점과 내용은 범위 밖으로 남겼다.
- `overview` §5 모듈 지도는 `tag -- ArticleEvaluated --> digest` 선을 그리고, `digest` 가 이 이벤트를 받아도 즉시 생성하지 않고 지표 갱신에만 쓴다고 적었다. tag Spec 은 이 이벤트를 (미설계) 로 적었다.
- 코드에는 이 이벤트의 타입 · 발행 · 리스너가 없다. 평가를 쓰는 모듈은 모두 `tag` 의 공개 조회로 읽는다.
  - `digest` 는 후보 선별에서 기간 안에 처음 평가된 기사를 첫 평가 시각으로 조회한다(`ArticleEvaluations.firstEvaluatedIn`, ADR-0207)
  - `event` 는 기간 집계의 중요도를 조회한다(`ArticleEvaluations.importances`)
- "지표"는 설계된 것이 없다. 쓸 곳이 없는 이벤트가 1층 그림에 남아, 의존을 그리는 그림이 코드와 맞지 않았다.

## 결정

- **평가 결과를 알리는 이벤트를 두지 않는다.** `tag` 가 내는 이벤트는 활성 전환 `TagSetActivated` 하나다.
- **평가를 쓰는 모듈은 `tag` 의 공개 조회로 읽는다.** 평가 회차가 끝났다는 알림에 기대지 않고, 필요한 때 필요한 기간 · 기사의 평가를 묻는다.
- 평가가 끝난 것을 곧바로 알아야 하는 기능이 생기면 그 기능을 설계하는 작업이 이벤트의 발행 시점 · 내용 · 받는 쪽을 함께 정한다.
- 정본은 [`overview` §5](../overview.md#5-모듈-지도)와 [tag Spec](../specs/tag.md)이다.

## 결과

- `overview` §5 의존 그래프에 계획만 있는 이벤트 선이 없다. 그림의 선은 코드의 모듈 사이 의존과 같다.
- 평가를 읽는 모듈은 조회 시점의 평가를 본다. 평가 직후에 무언가를 하려면 그 모듈의 반복 작업이 조회로 새 평가를 찾는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 계획(점선)으로 남기고 (미설계) 로 둔다 | 발행자 · 소비자 · 쓸 곳이 모두 없는 추측성 기능이 1층 그림에 남는다 |
| 이번에 발행 시점 · 내용을 정해 구현한다 | 받을 모듈이 없어 설계의 근거가 없다 |
