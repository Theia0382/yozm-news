# ADR-0338: 변화 리포트는 개요 없이 갈래 섹션만 쓴다

- 상태: 승인
- 날짜: 2026-11-26
- 근거: [이슈 #242](https://github.com/Theia0382/yozm-news/issues/242) — 측정 인스턴스에서 만든 리포트의 개요가 아래 갈래 섹션을 한 번 더 요약해 같은 사건 · 판정이 두 번 나왔다. 개요는 리포트 목록 · 알림이 쓰지 않고 한 편 화면의 첫 문단과 다음 리포트의 입력(직전 리포트 글)에만 쓰인다. 구현 `cd3cdb7`
- 관련: [ADR-0273](0273-change-reports-are-written-in-fixed-sections-per-material-kind-keyed-as-required-fields.md)(부분 대체 — 결정의 "섹션은 개요와 …" · "출력은 개요 문자열과 …"의 개요), [ADR-0311](0311-a-change-report-input-fits-the-smaller-of-the-model-budget-and-the-weekly-or-monthly-cap-by-a-share-for-earlier-reports-and-a-fixed-trimming-order.md)(부분 대체 — 직전 글 자르기의 "개요 · 섹션 제목은 남긴다"의 개요), [ADR-0312](0312-the-reasoning-effort-setting-is-shared-by-digests-and-change-reports-and-report-lengths-are-constants.md)(부분 대체 — 글 길이 기준의 개요 길이), [ADR-0316](0316-a-change-report-view-carries-each-visible-sections-numbers-as-typed-values-and-resolves-flow-lines-like-citations.md)(부분 대체 — "개요와 숨은 섹션은 숫자가 없다"의 개요)

## 맥락

- 개요는 출력 서술의 필수 칸이었다 — 기간 전체를 한 문단으로 정리하고, 갈래 섹션이 같은 내용을 갈래마다 다시 쓴다.
- 화면에서 개요는 제목 아래 첫 문단이었다. 섹션의 고정 제목 · 요지 한 줄(reader-R16)이 그 자리에서 갈래마다 핵심을 보인다.
- 다음 리포트의 입력에 실리는 직전 리포트 글도 개요와 섹션을 함께 실어 같은 내용이 두 번 들었다.

## 결정

- **출력 서술의 루트는 갈래 칸(`sections`) 하나다.** 지시 · 읽기 · 대조 · 저장 · 로컬 대체물 · 조회 · 화면에서 개요를 없앤다. 섹션은 갈래 섹션만 위치 0 부터 저장한다.
- **갈래 칸의 제목은 그 갈래의 요지 한 줄이다** — 화면이 갈래의 고정 제목 아래 보인다.
- **직전 리포트 글은 섹션 제목 · 본문만 싣고, 섹션이 없으면 싣지 않는다.** 출력 본문 토큰 어림에서 개요 칸을 뺀다.

규칙은 [report Spec](../specs/report.md#출력-서술과-읽기) report-R5 · R9 · R20 · R23 · R24 에 있다.

## 결과

- 리포트의 출력이 갈래마다 한 번만 정리한다 — 출력 토큰이 줄었다.
- 대조 뒤 갈래 섹션이 하나도 남지 않은 출력(모든 갈래가 빠진 출력을 두 번 받거나 칸이 상한을 넘어 버려짐)은 읽지 못한 출력(`UNREADABLE_OUTPUT`)으로 끝낸다 — 개요가 없어 보일 글이 없다. 예전에 빈 개요를 읽지 못한 것으로 친 것과 같은 꼴이고, 사용자는 실패 알림과 다시 만들기를 받는다.
- 저장된 개요 행은 [ADR-0340](0340-sections-and-snapshot-fields-of-removed-report-kinds-are-kept-unread-and-snapshot-version-three-reads-version-two.md)대로 지우지 않고 읽지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 개요를 만들고 화면에서만 숨긴다(다음 리포트 입력으로만 쓴다) | 사용자가 보지 않는 글에 사용자 키 비용을 쓴다 |
| 개요를 남기고 길이만 줄인다 | 섹션과 같은 내용을 되풀이하는 것은 그대로다 |
