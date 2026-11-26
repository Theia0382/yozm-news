# ADR-0310: 변화 리포트의 생성 판정은 다이제스트를 따라 읽지 못한 응답과 빠진 갈래를 한 번 다시 묻는다

- 상태: 승인. 부분 대체 — 다시 묻는 계기와 두 읽기 고르기([ADR-0335](0335-a-change-report-asks-once-more-when-its-writing-keeps-an-alias-of-the-request.md))
- 날짜: 2026-11-24
- 근거: [이슈 #239](https://github.com/Theia0382/yozm-news/issues/239) — 수용 기준 "끝까지 쓴 응답을 읽지 못하거나 칸이 빠지면 다시 묻는다 — 다이제스트의 생성 판정을 따를지 정한다". 기점 `f07e122` 의 리포트 판정(`ReportVerdict` — 다시 묻지 않음)과 실패 코드(출력 쪽 셋과 `NO_GENERATOR`)를 다이제스트(digest-R27 · R28) · 사용자 스토리라인의 정리(user-R71)의 꼴과 대조함
- 관련: [ADR-0221](0221-generator-results-are-success-or-failure-and-the-use-case-asks-once-more-only-for-a-complete-unreadable-answer.md)(보충 — 다이제스트의 결과 갈래와 다시 묻기를 리포트에 적용), [ADR-0270](0270-an-item-missing-from-the-output-is-asked-once-more-and-then-kept-as-a-headline-and-the-counts-are-recorded.md)(보충 — 빠진 항목의 다시 묻기를 리포트의 빠진 갈래에 적용, 대체 · 기록은 적용하지 않음), [ADR-0273](0273-change-reports-are-written-in-fixed-sections-per-material-kind-keyed-as-required-fields.md)(갈래를 키로 한 필수 칸)

## 맥락

- 리포트 출력은 개요와 입력 갈래를 키로 한 칸이다(ADR-0273). 칸이 없거나 제목 · 본문이 상한을 넘어 버려지면 그 갈래의 섹션이 없다(report-R6 · R7). 다이제스트의 빠진 항목과 달리 대체하는 길이 없다 — 사용자는 그 주 · 달의 한 덩어리를 통째로 잃는다.
- 리포트는 주 · 달에 한 번이라 한 번의 실패 · 빠짐의 손실이 다이제스트보다 크다. 다시 묻는 비용은 입력 전체를 한 번 더 보내는 것이고 그 한 번에만 든다.
- 기존 꼴은 둘이다 — 다이제스트는 끝까지 쓴 응답을 못 읽거나 칸이 빠지면 한 번 다시 묻고(ADR-0221 · ADR-0270), 사용자 스토리라인의 정리는 다시 묻지 않는다.
- 실제 생성기가 서기 전의 리포트는 관문의 실패 종류를 받을 코드가 없었고, `local` 밖에서는 생성기가 없어 언제나 `NO_GENERATOR` 로 끝났다.

## 결정

> 부분 대체(아래 두 항목 — 다시 묻는 계기와 두 읽기 고르기) — [ADR-0335](0335-a-change-report-asks-once-more-when-its-writing-keeps-an-alias-of-the-request.md). 지금 상태: [report Spec](../specs/report.md#생성-판정과-다시-묻기) report-R8
- **리포트의 생성 판정은 다이제스트를 따른다.** 끝까지 쓴 응답을 읽지 못하면 읽기의 진단을, 읽었는데 빠진 갈래가 있으면 빠진 갈래 키를 지시에 덧붙여 한 번 다시 묻는다. 한 생성에 다시 묻기는 한 번이다.
- **빠진 갈래 때문에 다시 물은 호출은 실패로 끝내지 않는다.** 두 읽기 가운데 빠진 갈래가 적은 쪽(같으면 다시 물은 쪽)을 쓰고, 다시 물은 호출이 읽히지 않거나 잘림 · 거절 · 실패면 첫 읽기를 쓴다. 다시 물어도 빠진 갈래는 대체하지 않고 섹션이 없다.
- **잘림 · 거절은 다시 묻지 않는다.** 토큰은 공급자가 답한 호출의 합이다. 다시 물은 사실은 리포트 행에 적지 않고 정보 로그로 남긴다.
- **실패 코드는 관문의 실패 종류마다 하나와 키 없음이고, 다이제스트의 이름을 쓴다** — `KEY_MISSING` · `KEY_INVALID` · `PERMISSION_DENIED` · `QUOTA_EXHAUSTED` · `PROVIDER_UNAVAILABLE` · `PROVIDER_UNEXPECTED`.
- **실제 생성기는 `local` 밖에서 언제나 서므로 생성기가 없는 갈래 · 코드(`NO_GENERATOR`)를 두지 않는다.** 생성 유스케이스는 생성기를 생성자로 받는다(ADR-0221 과 같은 성질).
- **리포트 하나의 생성은 한 실행이 하고, 그 실행의 생성기 호출은 다시 묻기를 더해 많아야 둘이다.** (사용자, 리포트 기간)마다 리포트가 하나인 제약은 일정을 만드는 작업이 정한다.

규칙은 [report Spec](../specs/report.md#생성-판정과-다시-묻기) report-R8 · R13 · R24 에 있다.

## 결과

- 다이제스트와 리포트가 같은 판정 규칙을 갖는다 — 판정 구조는 과제의 출력 타입에 기대므로 모듈마다 따로 둔다.
- 다시 묻는 호출은 입력을 한 번 더 보내 비용이 두 배가 될 수 있다. 상한은 입력 맞추기의 상한이 정한다([ADR-0311](0311-a-change-report-input-fits-the-smaller-of-the-model-budget-and-the-weekly-or-monthly-cap-by-a-share-for-earlier-reports-and-a-fixed-trimming-order.md)).
- 한 실행이 생성 호출을 둘까지 해 실행의 최장 시간이 두 배가 된다 — 멈춘 생성의 거두기 · 임대는 일정을 만드는 작업이 이것을 셈한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 못 읽을 때만 다시 묻고 빠진 갈래는 받아들인다 | 대체가 없는 리포트에서는 빠진 갈래가 그대로 사라진다 |
| 다시 묻지 않는다(정리의 꼴) | 못 읽는 응답 하나로 그 주 · 달의 리포트가 실패하고, 리포트를 다시 만드는 길이 따로 없다 |
| 다이제스트처럼 빠진 수를 행에 적는다 | 빠진 갈래를 대체하지 않아 화면이 그 수를 쓸 곳이 없다 — 로그로 충분하다 |
