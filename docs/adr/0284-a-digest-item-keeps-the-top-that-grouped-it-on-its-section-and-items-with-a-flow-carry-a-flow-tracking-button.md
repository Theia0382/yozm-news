# ADR-0284: 다이제스트 항목은 묶은 꼭대기를 섹션에 남기고, 흐름이 있는 항목에 흐름 추적 버튼을 둔다

- 상태: 승인
- 날짜: 2026-11-22
- 근거: [이슈 #244](https://github.com/Theia0382/yozm-news/issues/244) — 다이제스트 항목에서 그 흐름을 바로 따라가게 하려는데, 항목이 어느 흐름으로 묶였는지가 생성 뒤에 남지 않았다. 저장 자리 · 버튼을 둘 항목 · 화면 이름을 정했다. 구현 커밋 `9d04fbb` · `abcba83`
- 관련: [ADR-0262](0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md)(보충 — 섹션이 항목을 묶은 꼭대기도 갖는다), [ADR-0282](0282-flow-tracking-points-to-a-user-storyline-which-user-handlers-store-under-the-settings-lock-while-readers-get-the-result-of-the-same-rule.md)(추적 상태 조회), [ADR-0193](0193-the-per-user-storyline-information-is-the-received-news-counted-from-the-digest-shown-for-each-date.md)(받은 소식 — 그대로)

## 맥락

- 항목은 고른 사건을 포함 나무의 꼭대기로 묶은 것이고(ADR-0262), 섹션은 항목의 사건과 선정 이유를 갖는다. 묶은 꼭대기는 남지 않는다.
- 사건의 꼭대기는 여럿일 수 있고 관계는 바뀐다 — 읽을 때 사건에서 다시 풀면 생성 때 묶은 꼭대기와 다를 수 있다.
- 화면은 스토리라인 목록 · 타임라인에서 "구독"이라는 이름으로 버튼을 뒀다.

## 결정

- **항목 묶기가 고른 꼭대기를 섹션에 저장한다.** 큰 판정 행의 항목은 그 행 머리의 꼭대기, 꼭대기 없이 혼자 한 항목은 없다. 프롬프트에는 싣지 않는다.
- **조회는 저장한 꼭대기를 지금 꼭대기로 풀어 낸다.** 합쳐졌으면 남는 쪽의, 다른 나무에 들었으면 그 나무의 꼭대기이고, 지워졌으면 없다.
- **흐름이 있는 항목(주요 뉴스 · 헤드라인)에 흐름 추적 버튼을 둔다.** 누르는 대상은 그 지금 꼭대기다. 흐름 없는 항목과 개요에는 없다. 추적 상태는 다이제스트 응답에 싣지 않고 추적 상태 조회로 읽는다.
- **화면의 이름은 모든 화면에서 "흐름 추적"이다.** 개발 이름은 스토리라인 구독(`storyline subscription`) 그대로다. 주제 쪽의 "구독"은 그대로다.

## 결과

- 이 결정보다 앞서 만든 다이제스트의 항목에는 버튼이 없다.
- 저장한 꼭대기가 지워진 항목은 흐름을 다시 찾지 않는다.
- 스토리라인 목록 · 타임라인 · 다이제스트 항목이 같은 버튼 · 같은 상태를 쓴다.
- 항목의 흐름으로 가는 링크와 흐름의 정리는 개인 스토리라인 화면이 정한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 읽을 때 항목의 사건에서 꼭대기를 다시 푼다 | 꼭대기가 여럿인 사건은 묶기의 고르기를 조회가 다시 해야 하고, 생성 때와 다른 흐름이 나올 수 있다 |
| 추적 상태를 다이제스트 응답에 싣는다 | 다이제스트 조회가 사용자 스토리라인을 맞춰야 하고, 추적 · 해제 뒤 다이제스트를 다시 읽어야 한다 |
| 다이제스트 항목만 "흐름 추적", 스토리라인 화면은 "구독" | 같은 일에 이름이 둘이다 |
| 정리된 스토리라인이 있는 항목에만 버튼을 둔다 | 정리 전의 흐름을 따라갈 길이 없다 |

## 후속 기록

- 2026-11-23: 결과 "항목의 흐름으로 가는 링크와 흐름의 정리는 개인 스토리라인 화면이 정한다" — [ADR-0290](0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md) · [`reader` reader-R9](../specs/reader.md#다이제스트)
