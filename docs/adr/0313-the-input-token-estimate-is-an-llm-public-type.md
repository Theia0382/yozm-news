# ADR-0313: 입력 토큰 어림은 `llm` 의 공개 타입이 정본이다

- 상태: 승인
- 날짜: 2026-11-24
- 근거: [이슈 #239](https://github.com/Theia0382/yozm-news/issues/239) — 변화 리포트의 입력 맞추기가 토큰 어림을 써야 하는데, 기점 `f07e122` 의 어림 비율 · 함수(`CandidateCost.TOKENS_PER_CODE_POINT` · `tokens`)가 `digest` 의 하위 패키지에 있어 `report` 가 읽을 수 없음을 확인함
- 관련: [ADR-0210](0210-candidate-selection-cuts-to-the-input-token-budget-with-one-estimate-the-prompt-follows.md)(부분 대체 — 결정 "어림 규칙의 정본은 `digest` 의 `domain` 한 곳"의 비율 · 함수의 자리), [ADR-0311](0311-a-change-report-input-fits-the-smaller-of-the-model-budget-and-the-weekly-or-monthly-cap-by-a-share-for-earlier-reports-and-a-fixed-trimming-order.md)(리포트 입력 맞추기)

## 맥락

- 보내기 전에는 공급자가 세는 토큰 수를 알 수 없어 코드 포인트로 어림한다. 비율(코드 포인트 하나에 1.0 토큰)과 올림 함수는 다이제스트의 후보 선별 상수(`CandidateCost`)에 있었다(ADR-0210).
- 변화 리포트도 입력을 모델의 입력 예산 안으로 맞추려면 같은 어림이 필요하다. 다른 모듈의 하위 패키지는 읽을 수 없고, 같은 값을 `report` 에 두면 정본이 둘이 된다.
- 어림이 견주는 상대인 입력 토큰 예산은 `llm` 의 허용 모델 목록에 있다.

## 결정

- **코드 포인트 → 토큰 어림의 비율 · 함수는 `llm` 의 공개 타입 `TokenEstimate` 가 정본이다.** 다이제스트 · 변화 리포트가 그것을 부른다.
- **다이제스트의 몫 상수(기사 줄 · 사건 · 프로필의 몫, 지시 몫)와 사건 · 예산 어림은 `digest` 에 남는다.**

규칙은 [llm Spec](../specs/llm.md#tokenestimate) 의 `TokenEstimate` · llm-R4 에 있다.

## 결과

- 어림과 예산이 한 모듈에 있다 — 비율이 모델마다 달라져야 하면 `LlmModel` 곁에서 바뀐다.
- `llm` 에 과제와 무관한 어림 타입이 하나 는다. 다이제스트의 동작은 바뀌지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `platform` 의 공개 타입으로 둔다(LLM 출력 읽기와 같은 자리) | 예산(`llm`)과 어림이 갈라지고, 비율이 모델마다 달라지면 다시 옮겨야 한다 |
| `report` 에 같은 비율을 둔다 | 같은 지식의 정본이 둘이 된다 |
