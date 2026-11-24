# ADR-0274: 변화 리포트는 스토리라인 · 사건만 인용하고, 주제는 통계 스냅숏으로 둔다

- 상태: 승인. 부분 대체 — 리포트가 `article` 에 의존하지 않는다는 결과([ADR-0305](0305-a-change-report-describes-flows-by-the-user-storyline-summary-and-events-by-the-representative-article-title-and-cites-a-flow-by-its-head.md))
- 날짜: 2026-11-22
- 근거: [이슈 #237](https://github.com/Theia0382/yozm-news/issues/237) — 기점 `d25590c` 의 데이터 모델 초안(스토리라인 · 기사 인용)과 사건 단위로 바뀐 다이제스트 · 반응을 대조하고, 스토리라인 · 사건 인용을 골랐다
- 관련: [ADR-0025](0025-long-term-personal-news-history-in-v1.md)(변화 리포트 입력에 원문이 없다), [ADR-0262](0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md) · [ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md)(다이제스트 · 반응의 단위는 사건)

## 맥락

- 데이터 모델 초안은 리포트 섹션이 스토리라인 또는 기사를 인용한다고 적었다.
- 리포트 입력에는 원문이 없고(ADR-0025), 갈래는 스토리라인 · 주제 · 관심 단위다 — 기사 단위 재료가 아니다.
- 다이제스트는 사건을 고르고 사건들을 항목으로 묶으며(ADR-0262), 반응 · 관심도도 사건마다 기록한다(ADR-0265). 장기 기록이 모두 사건 단위다.
- 사건은 대표 기사 조회와 합쳐진 사건 풀기 조회가 있어 화면 링크와 따르기가 모두 된다.
- 주제(태그 키)는 태그 체계를 바꿔 활성하면 계보를 따라 바뀐다.

## 결정

- **리포트 섹션은 스토리라인 또는 사건을 인용한다.** 인용 행 하나가 둘 가운데 하나를 ID 로 가리킨다. 화면은 사건의 대표 기사로 링크를 그린다.
- **주제는 인용으로 두지 않는다.** 주제 보도량 · 관심의 값은 생성에 쓴 통계 스냅숏에 담고, 화면은 스냅숏에 실린 이름으로 그린다.

규칙은 [report Spec](../specs/report.md#출력-대조) report-R7 에 있다.

## 결과

> 부분 대체(둘째 문장) — [ADR-0305](0305-a-change-report-describes-flows-by-the-user-storyline-summary-and-events-by-the-representative-article-title-and-cites-a-flow-by-its-head.md). 지금 상태: [report Spec](../specs/report.md#재료) report-R16
- 리포트 · 다이제스트 · 반응 · 관심도가 같은 단위로 이어진다. 리포트는 `article` 에 의존하지 않는다.
- 화면은 사건 인용의 링크를 그리려고 대표 기사를 한 번 더 읽는다.
- 기사가 지워져도 사건에 기사가 남으면 사건 인용과 글이 남는다 — 지운 기사의 제목이 입력에 들어갔다면 그 문장도 남는다. 인용 기사 일부만 지워진 다이제스트 섹션이 요약을 남기는 것과 같은 수준이다.
- 주제 키가 바뀌어도 인용이 깨지지 않는다 — 스냅숏은 생성 때의 이름을 싣는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 스토리라인 · 기사(초안 그대로) | 리포트 입력에 기사 줄이 들어가야 하고, `report → article` 의존과 지운 기사 따르기가 새로 생기며, 다른 장기 기록과 단위가 섞인다 |
| 스토리라인만 | 특정 사건을 짚어 말할 때 링크가 없고, 사건 단위인 다른 기록과 잇지 못한다 |
| 주제도 인용으로 | 주제 키가 계보로 바뀌어 인용 행을 따라가야 하고, 화면이 그릴 이름은 스냅숏에 이미 있다 |
