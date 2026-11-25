# ADR-0334: 변화 리포트의 LLM 입력은 기사 수 · 관심 값 없이 판정 · 변화 · 상태 · 기간 종류를 낱말로 싣는다

- 상태: 승인
- 날짜: 2026-11-25
- 근거: [이슈 #242](https://github.com/Theia0382/yozm-news/issues/242) — 측정 인스턴스에서 만든 리포트 16편의 본문이 "구독 주제 보도량은 사회 2,318건", "관심이 0.2 에서 0.96 으로" 같은 수와 `FELL` · `STEADY` · `NO_COMPARISON` · `QUIET` 같은 값 이름을 그대로 옮겼다. 구독 주제 보도량은 그 인스턴스가 모은 기사 전체를 주제마다 센 수(tag-R30)이고 사용자의 제외 소스 · 형식 · 지역을 보지 않는다. 구현 `d467815`
- 관련: [ADR-0306](0306-a-change-report-compares-subscribed-topic-coverage-and-flow-coverage-each-only-with-its-own-previous-value.md)(부분 대체 — 결과의 "생성 지시가 두 수를 견주지 않게"), [ADR-0303](0303-a-change-report-covers-only-what-relates-to-the-user-in-three-sections-tracked-flows-subscribed-topic-coverage-and-interest-changes.md)(갈래 셋은 그대로), [ADR-0305](0305-a-change-report-describes-flows-by-the-user-storyline-summary-and-events-by-the-representative-article-title-and-cites-a-flow-by-its-head.md)(흐름 · 사건을 이름으로 부르는 것과 같은 방향)

## 맥락

- 보도량(구독 주제 · 흐름 · 사건의 기사 수)은 운영자가 켠 소스에 따라 달라지는 인스턴스의 수집량이다. 사용자가 본 기사 수가 아니다.
- 관심 값은 관심도 산식의 내부 점수다. 사용자가 그 수로 알 수 있는 것이 정해져 있지 않다.
- 생성 모델은 사용자가 고른 외부 공급자다. 입력에 든 수는 글에 그대로 옮겨지고, 글을 수의 나열로 만든다.
- 판정(늘었다 · 줄었다 · 새로 나타났다)과 관심의 생김 · 사라짐은 사용자가 읽을 수 있는 변화다.

## 결정

- **사용자 구획에 기사 수 · 관심 값을 싣지 않는다.** 흐름 · 구독 주제의 보도량은 직전 기간과 견준 판정 하나, 사건 줄은 기사 수 없이(순서는 기간 안 기사 수가 많은 쪽부터), 관심 줄은 변화만 싣는다.
- **값은 영문 값 이름이 아니라 한국어 낱말로 싣는다** — 기간 종류 · 흐름의 상태 · 보도의 변화 · 관심의 변화 · 구독 행동. 낱말의 정본은 값마다의 망라 분기 하나(`ReportPrompts.meaning`)이고, 지시가 늘어놓는 낱말도 같은 함수에서 나온다.
- **스냅숏과 직전 값 잇기는 바꾸지 않는다** — 스냅숏은 기사 수 · 관심 값을 그대로 담고, 화면은 그 스냅숏을 읽는다(화면이 수를 보이는 꼴은 [ADR-0337](0337-the-change-report-screen-shows-no-numbers-in-text-interest-as-history-cells-and-judgements-as-coloured-words.md)).

규칙은 [report Spec](../specs/report.md#프롬프트) report-R23 에 있다.

## 결과

- 리포트 글은 "늘었다 · 줄었다 · 새로 생긴 관심"으로 말하고 "얼마나"는 말하지 않는다.
- 두 보도량을 견주지 말라는 지시가 필요 없다 — 입력에 수가 없다. 두 보도량을 각자 직전 값과만 견주는 판정은 그대로다.
- 외부 전송 고지의 변화 리포트 항목이 판정 · 변화로 바뀐다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 구독 주제 보도량 갈래를 리포트에서 없앤다 | 구독 주제의 보도가 늘고 준 것은 사용자에게 뜻이 있다. 갈래 enum · 출력 서술 · 저장 제약 · 스냅숏을 함께 바꾼다 |
| 수를 넘기고 "본문에 수를 쓰지 않는다" 지시만 더한다 | 인스턴스 수집량과 내부 점수가 외부 공급자로 계속 가고, 글에 새는지를 관측할 장치가 없다 |
| 값 이름(`ROSE` 등)과 뜻을 함께 넘긴다 | 측정에서 값 이름이 글에 그대로 옮겨졌다 |
