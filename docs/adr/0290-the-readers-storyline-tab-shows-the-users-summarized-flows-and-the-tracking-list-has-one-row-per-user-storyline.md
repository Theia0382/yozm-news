# ADR-0290: 독자의 스토리라인 탭은 정리된 내 흐름을 보이고, 추적 중인 흐름 목록은 사용자 스토리라인마다 한 줄이다

- 상태: 승인
- 날짜: 2026-11-23
- 근거: [이슈 #245](https://github.com/Theia0382/yozm-news/issues/245) — 독자 탭이 모든 사용자의 공용 스토리라인 목록을 보였고, 추적 중인 흐름 목록은 한 흐름이 누른 스토리라인 수만큼 줄로 나뉘었다. 리드가 탭의 내용 · 공용 타임라인의 자리 · 목록의 단위를 정했다. 구현 커밋 `e16498b` · `fc8a373` · `27decd5` · `27a214d`
- 관련: [ADR-0282](0282-flow-tracking-points-to-a-user-storyline-which-user-handlers-store-under-the-settings-lock-while-readers-get-the-result-of-the-same-rule.md)(부분 대체 — 추적 중인 흐름 목록의 줄), [ADR-0284](0284-a-digest-item-keeps-the-top-that-grouped-it-on-its-section-and-items-with-a-flow-carry-a-flow-tracking-button.md)(보충 — 항목의 흐름으로 가는 링크와 흐름의 정리), [ADR-0242](0242-the-storyline-subscription-list-lives-on-the-key-gated-storyline-screen.md)(보충 — 목록의 제목), [ADR-0281](0281-a-user-storyline-is-a-flow-that-received-interest-signals-and-tracks-the-tops-reached-forward-along-follows-from-its-seeds-as-one-group.md)(보충 — 사용자에게 드러나는 것), [ADR-0289](0289-shown-flows-are-user-storylines-with-a-summary-which-the-summary-job-creates-and-removes.md)(보이는 흐름)

## 맥락

- 공용 스토리라인의 전체 목록은 운영자 화면에도 있다.
- 사용자 스토리라인 하나는 꼭대기 여럿을 추적 대상으로 가질 수 있다.
- 공용 타임라인(사건마다 기사와 받은 날짜)은 정리가 대신하지 못하는 정보다.

## 결정

- **독자의 탭은 그 사용자의 정리된 흐름만 마지막으로 정리한 순으로 보인다.** 공용 스토리라인의 독자 목록 처리기는 없다. 전체 목록은 운영자 화면에 있다.
- **정리된 흐름 하나를 보이는 개인 흐름 화면을 둔다.** 흐름 전체의 정리, 추적 대상 꼭대기마다의 정리, 그 흐름에 묶인 지난 다이제스트 항목, 흐름 추적 버튼이 있다.
- **공용 타임라인은 남기고 개인 흐름 화면의 꼭대기마다 그 타임라인으로 가는 링크를 둔다.** 글이 없는 꼭대기는 링크만 둔다.
- **다이제스트 항목을 묶은 흐름이 정리돼 있으면 항목에 정리의 제목을 개인 흐름 화면 링크로 붙인다.** 응답은 `reader` 가 엮는다.
- **추적 중인 흐름 목록은 사용자 스토리라인마다 한 줄이다.** 줄은 지금 있는 추적 씨앗, 그 가운데 가장 이어지는 상태, 정리의 제목을 싣는다. 제목은 정리가 있으면 정리의 것이고 없으면 화면이 첫 추적 씨앗의 카드에서 읽는다.
- **응답 · 경로의 이름은 사용자 스토리라인(`userStoryline` · `/api/user-storylines`)이다.**

## 결과

- 정리가 하나도 없는 사용자의 탭은 비어 있고 어떻게 생기는지 안내한다.
- 지난 다이제스트 항목은 항목을 묶은 꼭대기를 저장한 다이제스트부터 찾아진다.
- 추적 중인 흐름 목록의 최근 기사 시각은 첫 추적 씨앗의 것이다.
- 정리가 없는 추적 흐름은 목록에서 공용 타임라인으로 간다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 탭에 공용 목록과 내 흐름을 함께 둔다 | 탭이 무엇을 보이는 곳인지 흐려지고, 전체 목록은 운영자 화면과 겹친다 |
| 공용 타임라인을 없앤다 | 사건마다의 기사와 받은 날짜를 볼 곳이 없다 |
| 추적 중인 흐름 목록을 추적 씨앗마다 한 줄로 둔다 | 한 흐름이 여러 줄이고 정리의 제목을 붙일 줄이 정해지지 않는다 |
| 응답의 이름을 `flow` 로 한다 | 개발 이름은 한국어와 코드가 같은 말이고, 코드의 다른 뜻(흐름 소속 판정)과 겹친다 |
