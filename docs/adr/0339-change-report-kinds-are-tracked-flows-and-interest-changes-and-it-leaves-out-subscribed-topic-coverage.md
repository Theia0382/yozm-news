# ADR-0339: 변화 리포트의 갈래는 추적한 흐름 · 관심 변화 둘이고, 구독 주제 보도량을 다루지 않는다

- 상태: 승인
- 날짜: 2026-11-26
- 근거: [이슈 #242](https://github.com/Theia0382/yozm-news/issues/242) — 측정 인스턴스의 리포트 16편에서 구독 주제 보도량 판정 76줄 가운데 75줄이 비슷함 · 비교 없음이었다(주간 57줄: 비슷함 37 · 비교 없음 19 · 늘었음 1, 월간 19줄: 비교 없음 19). 주제 단위 보도는 주마다 거의 같고, 수를 뺀 뒤(ADR-0334) 글이 말하는 것은 "비슷하다"뿐이었다. 구현 `cd3cdb7` · `8dc8e64`
- 관련: [ADR-0303](0303-a-change-report-covers-only-what-relates-to-the-user-in-three-sections-tracked-flows-subscribed-topic-coverage-and-interest-changes.md)(부분 대체 — 결정 "갈래는 셋이다"), [ADR-0306](0306-a-change-report-compares-subscribed-topic-coverage-and-flow-coverage-each-only-with-its-own-previous-value.md)(부분 대체 — 두 결정의 구독 주제 보도량 자리), [ADR-0304](0304-a-change-report-takes-previous-values-from-the-previous-reports-snapshot-matched-one-to-one-and-keeps-them-in-its-own-snapshot.md)(부분 대체 — 주제 잇기), [ADR-0274](0274-change-reports-cite-only-storylines-and-events-and-keep-topics-in-the-stats-snapshot.md)(부분 대체 — "주제 보도량 · 관심의 값은 스냅숏에"의 주제 보도량), [ADR-0316](0316-a-change-report-view-carries-each-visible-sections-numbers-as-typed-values-and-resolves-flow-lines-like-citations.md)(부분 대체 — 보도량 섹션의 숫자), [ADR-0273](0273-change-reports-are-written-in-fixed-sections-per-material-kind-keyed-as-required-fields.md)(부분 대체 — 갈래 목록), [ADR-0064](0064-topic-coverage-counts-articles-by-first-evaluation-time-with-query-time-judgement-over-key-lineage-in-the-app.md)(보충 — 주제별 보도량의 쓰는 곳)

## 맥락

- 구독 주제 보도량은 인스턴스가 모은 기사 전체를 주제마다 센 수다 — 사용자가 본 기사와 관계가 없고, 사용자의 구독은 주제 단위라 주마다 크게 달라지지 않는다.
- 흐름 보도량은 흐름이 식거나 커지는 것을 잡는다 — 둘째 주간 이후 36줄 가운데 16줄이 늘었음 · 줄었음 · 새로 생김이었다.
- 구독 주제는 관심 변화 갈래에서 구독 변경 · 새로 생기거나 사라진 관심으로 이미 보인다.

## 결정

- **갈래는 추적한 흐름(`STORYLINES`) · 관심 변화(`INTERESTS`) 둘이다.** 재료 조립이 주제별 보도량을 읽지 않는다 — `report` 가 `tag` 의 주제별 보도량 · 계보 표를 쓰지 않는다.
- **추적한 흐름이 없고 관심 변화 · 구독 변경도 없으면 리포트는 `EMPTY` 다** — 주제를 구독하고 있어도 그렇다.
- 섹션 종류 · 입력 갈래 · 출력 서술 · 스냅숏 · 섹션 숫자 · 화면용 API · 화면 · 외부 전송 고지에서 구독 주제 보도량을 뺀다.

규칙은 [report Spec](../specs/report.md#재료) report-R14 와 [reader Spec](../specs/reader.md#변화-리포트) reader-R16 에 있다.

## 결과

- 리포트는 흐름의 전개와 관심의 변화만 말한다.
- 구독 주제만 있고 흐름 추적 · 관심 변화가 없는 사용자는 리포트를 받지 않는다(`EMPTY`).
- 두 보도량을 견주지 않는다는 규칙(ADR-0306)은 견줄 것이 하나만 남아 필요가 없다.
- 저장된 구독 주제 섹션 · 스냅숏 칸은 [ADR-0340](0340-sections-and-snapshot-fields-of-removed-report-kinds-are-kept-unread-and-snapshot-version-three-reads-version-two.md)대로 지우지 않고 읽지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 화면 표에만 남기고 LLM 에는 보내지 않는다 | 판정이 대부분 비슷함 · 비교 없음이라 표도 말하는 것이 없다. 갈래 · 스냅숏 · 화면 표를 계속 둔다 |
| 판정 상수(늘었다 · 줄었다의 비율)를 낮춰 변화를 더 자주 보인다 | 인스턴스 수집량의 오르내림을 사용자의 변화처럼 보인다 |
