# ADR-0316: 변화 리포트 조회는 보이는 갈래 섹션마다 스냅숏의 숫자를 타입 있는 값으로 싣고, 흐름 줄은 인용과 같은 규칙으로 풀며, 숨긴 섹션 수를 낸다

- 상태: 승인. 부분 대체 — 보도량 섹션의 숫자([ADR-0339](0339-change-report-kinds-are-tracked-flows-and-interest-changes-and-it-leaves-out-subscribed-topic-coverage.md)) · 개요([ADR-0338](0338-a-change-report-writes-only-kind-sections-without-an-overview.md))
- 날짜: 2026-11-24
- 근거: [이슈 #241](https://github.com/Theia0382/yozm-news/issues/241) — 기점 `a599598` 의 리포트 조회(`ReportView.statsSnapshot` 이 JSON 문자열)와 스냅숏 꼴(`ReportSnapshot` 의 읽기는 다음 리포트가 견줄 기사 수만)을 대조하고, 화면이 비교를 보일 값을 정함. 구현 `a561477`
- 관련: [ADR-0304](0304-a-change-report-takes-previous-values-from-the-previous-reports-snapshot-matched-one-to-one-and-keeps-them-in-its-own-snapshot.md)(보충 — 화면이 스냅숏 하나로 비교를 보이는 꼴), [ADR-0275](0275-report-citations-resolve-removed-and-merged-events-and-storylines-when-read-and-a-section-that-lost-every-citation-is-hidden.md)(보충 — 스냅숏 흐름 줄에도 같은 풀기, 숨긴 섹션 수를 알린다), [ADR-0192](0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md)(`reader` 는 판단 없이 엮는다)

## 맥락

- 리포트 화면은 리포트 한 편의 스냅숏이 담은 이번 값 · 직전 값 · 판정으로 비교를 보인다(ADR-0304). 스냅숏 꼴의 정본은 `report` 의 `ReportSnapshot` 하나다.
- 조회는 스냅숏을 JSON 문자열로 냈다. 화면용 API 를 내는 `reader` 나 프론트엔드가 그 문자열을 풀면 꼴의 두 번째 사본이 생기고, 판 번호를 올릴 때 조용히 어긋난다.
- 스냅숏의 흐름 줄은 생성 때의 헤드 ID 만 갖는다. 그 사이 흐름은 합쳐지거나 지워질 수 있다 — 인용과 같은 문제다(ADR-0275).
- 인용을 모두 잃은 섹션은 숨는다. 화면이 받는 값에는 숨긴 수가 없어 사용자는 리포트가 생성 때보다 줄었는지 알 수 없었다.

## 결정

> 부분 대체 — 둘째 문장의 보도량 섹션은 [ADR-0339](0339-change-report-kinds-are-tracked-flows-and-interest-changes-and-it-leaves-out-subscribed-topic-coverage.md), 셋째 문장의 개요는 [ADR-0338](0338-a-change-report-writes-only-kind-sections-without-an-overview.md). 지금 상태: [report Spec](../specs/report.md#리포트-조회) report-R25

- **조회가 보이는 갈래 섹션마다 그 갈래의 숫자를 타입 있는 값으로 싣는다.** 흐름 섹션은 흐름마다 보도량(이번 · 직전 · 판정 · 기간 안 추적), 보도량 섹션은 구독 주제마다 보도량, 관심 섹션은 주제 · 흐름마다 관심 값과 변화다. 개요와 숨은 섹션은 숫자가 없다 — 섹션 숨기기와 숫자 숨기기가 한 판단이다. 스냅숏 문자열은 공개 값에서 뺀다.
- **스냅숏의 파싱은 하나다.** 화면이 쓸 모든 칸을 읽는 길과 다음 리포트가 견줄 기사 수를 읽는 길이 같은 파서에서 나온다. 판정 · 변화의 값 집합은 공개 enum 하나로 둔다.
- **흐름 줄은 인용과 같은 규칙으로 푼다.** 생성 때 헤드는 지금 스토리라인으로 바꾸고(합쳐졌으면 남는 것), 지운 헤드의 줄은 빼며, 풀린 뒤 같은 스토리라인이 된 줄은 첫 줄만 둔다. 숫자는 생성 때 센 값 그대로다.
- **조회가 숨긴 갈래 섹션 수를 낸다.** 화면은 숨긴 섹션이 있으면 그 수를 알리고, 갈래 섹션이 모두 숨은 리포트에는 글 대신 안내를 보인다. 지운 흐름 줄은 이 수에 세지 않는다 — 단위가 섹션이다.
- 흐름 · 사건의 표시 이름(정리된 흐름의 제목 · 대표 기사)은 `reader` 가 붙인다. 지금 무엇을 가리키는지는 `report` 가 정한다.

규칙은 [report Spec](../specs/report.md#리포트-조회) report-R11 · R12 · R25 와 [reader Spec](../specs/reader.md#변화-리포트) reader-R16 에 있다.

## 결과

- `reader` · 프론트엔드는 스냅숏 꼴을 모른다. 스냅숏 판을 올리는 변경은 `report` 한 곳을 고친다.
- 글이 숨은 갈래의 숫자는 보이지 않는다. 모델이 다시 물어도 빠뜨린 갈래의 숫자도 보이지 않는다.
- 스냅숏은 입력을 예산에 맞춰 덜어 내기 전의 통계라, 글이 말하지 않은 흐름의 줄이 숫자 표에 있을 수 있다.
- 생성 뒤 지워지거나 합쳐진 흐름의 줄 숫자는 화면에서 사라진다.
- 조회가 스토리라인을 풀 때 인용과 스냅숏 헤드를 한 번에 묻는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `reader` · 프론트엔드가 스냅숏 문자열을 푼다 | 꼴의 사본 — 판을 올리면 어긋난다 |
| 숫자를 한 편에 섹션과 따로 싣고 `reader` 가 보이는 섹션에만 붙인다 | 숨은 섹션의 숫자를 빼는 판단이 `reader` 에 생긴다 |
| 리포트 아래 "숫자로 보기" 블록 하나에 스냅숏 전부 | 숨긴 섹션의 대상까지 다시 보이고, 글이 숨은 갈래의 숫자만 남는다. 글과 숫자가 떨어진다 |
| 흐름 줄에서 지운 헤드는 "사라진 흐름"으로, 겹친 줄은 모두 둔다 | 지운 것을 가리키는 표시가 숫자로 남고, 같은 이름의 줄이 둘 보여 가릴 수 없다 |
| 숨긴 수를 내지 않고 모두 숨은 경우만 안내한다 | 일부 섹션만 숨으면 리포트가 줄었다는 사실이 보이지 않는다 |
| 합쳐진 인용마다 "합쳐진 흐름"을 표시한다 | 인용 값의 꼴이 바뀌고, 합침은 흐름이 자라는 정상 과정이라 표시가 잦다 |

## 후속 기록

- 2026-11-26: 결정 "흐름 섹션은 흐름마다 보도량(…)"에 생성 때 헤드의 상태 — [ADR-0341](0341-a-monthly-report-does-not-compare-flow-coverage-with-the-previous-monthly-and-shows-the-flow-state-kept-at-generation.md) · [report Spec](../specs/report.md#리포트-조회) report-R25
