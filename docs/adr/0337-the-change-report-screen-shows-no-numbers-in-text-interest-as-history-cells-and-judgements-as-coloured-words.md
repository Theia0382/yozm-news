# ADR-0337: 변화 리포트 화면은 표의 수를 글자로 보이지 않고, 관심은 관심사 히스토리의 칸처럼 색으로, 판정 · 변화는 색 낱말로 보인다

- 상태: 승인
- 날짜: 2026-11-25
- 근거: [이슈 #242](https://github.com/Theia0382/yozm-news/issues/242) — 측정 인스턴스의 리포트 화면이 구독 주제 보도량("사회 2,504 · 2,318")과 관심 값(소수 둘째 자리)을 표에 글자로 보였다. 보도량은 인스턴스 전체의 수집량이고 관심 값은 내부 점수다. 관심사 히스토리의 칸은 값을 글자로 보이지 않고 색 네모로 보인다. 구현 `d33eaab`
- 관련: [ADR-0304](0304-a-change-report-takes-previous-values-from-the-previous-reports-snapshot-matched-one-to-one-and-keeps-them-in-its-own-snapshot.md)(보충 — 화면이 보이는 비교의 꼴), [ADR-0316](0316-a-change-report-view-carries-each-visible-sections-numbers-as-typed-values-and-resolves-flow-lines-like-citations.md)(응답의 숫자 칸은 그대로), [ADR-0334](0334-change-report-llm-input-carries-judgements-in-words-without-article-counts-or-interest-values.md)(LLM 입력도 수 없이 낱말로)

## 맥락

- 화면 표의 수는 사용자에게 뜻이 정해져 있지 않다 — 보도량은 운영자가 켠 소스에 달렸고, 관심 값의 크기는 산식의 몫이다.
- 사용자가 읽을 것은 늘었는지 · 줄었는지, 관심이 새로 생겼는지 · 사라졌는지와 관심의 대략의 크기다.
- 관심사 히스토리는 관심 값을 색 네모(관심 쪽 주 색 · 싫어요 쪽 경고 색, 진하기 = 크기)로 보이고 값은 칸의 `title` 과 화면 낭독에만 둔다.

## 결정

- **흐름 보도량 표(주간)는 이름과 판정 칸 하나이고, 판정은 색 낱말이다** — 늘었음 · 새로 생김은 주 색, 줄었음은 경고 색, 비슷함 · 비교 없음은 바탕색. 이번 · 직전 기간 기사 수는 판정 칸의 `title` 과 화면 낭독에만 둔다. 월간 흐름 표는 판정 대신 생성 때 흐름의 상태를 같은 색 낱말 꼴로 보인다([ADR-0341](0341-a-monthly-report-does-not-compare-flow-coverage-with-the-previous-monthly-and-shows-the-flow-state-kept-at-generation.md)).
- **관심 표의 기간 시작 · 끝 칸은 관심사 히스토리의 칸과 같은 색 네모다.** 값은 칸의 `title` 과 화면 낭독에만 둔다. 색 판단은 히스토리와 하나다(`shared/lib/interestLevel.ts`).
- **관심 변화 칸은 색 낱말이다** — 새로 생긴 관심은 주 색, 사라진 관심은 경고 색, 변화 없음은 바탕색.
- **화면용 API 는 수를 그대로 싣는다** — 화면이 글자로 그리지 않을 뿐이다.

규칙은 [web Spec](../specs/web.md#변화-리포트) web-R17 · web-R26 ~ R29 에 있다. 응답이 수를 그대로 싣는 것은 [reader Spec](../specs/reader.md#변화-리포트) reader-R16 이다.

## 결과

- 표를 색으로 훑어 늘고 준 것 · 생기고 사라진 관심을 알아본다.
- 수가 궁금한 사용자는 칸에 마우스를 올리거나 화면 낭독으로 읽는다. 터치 화면에서는 수가 보이지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 관심 값은 색 칸만, 보도량 수는 글자로 남기고 "이 서비스가 모은 기사 수"라고 밝힌다 | 사용자가 그 수로 알 것이 없다 — 판정이 같은 정보를 읽기 쉽게 준다 |
| 화면용 API 에서도 수를 뺀다 | 응답 칸을 줄이는 변경이 따른다. 수는 접근성 문구와 뒤의 화면 선택지로 남겨 둔다 |
