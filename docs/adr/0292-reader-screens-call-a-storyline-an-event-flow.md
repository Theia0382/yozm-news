# ADR-0292: 독자 화면은 스토리라인을 "사건 흐름"으로 부른다

- 상태: 승인. 부분 대체 — 운영자 화면 · 운영 문서의 이름([ADR-0320](0320-operator-screens-operations-docs-and-the-evaluation-tool-also-call-a-storyline-an-event-flow-and-a-big-one-a-big-topic.md))
- 날짜: 2026-11-23
- 근거: [이슈 #245](https://github.com/Theia0382/yozm-news/issues/245) — 독자 화면에서 "흐름 추적" · "이어진 흐름"처럼 "흐름"이라는 말이 이미 쓰이는데 탭과 몇 문구만 "스토리라인"이었다. 이름을 정했다. 구현 커밋 `fc8a373` · `27a214d`
- 관련: [ADR-0284](0284-a-digest-item-keeps-the-top-that-grouped-it-on-its-section-and-items-with-a-flow-carry-a-flow-tracking-button.md)(화면 이름 "흐름 추적"), [ADR-0290](0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md)(탭의 내용)

## 맥락

- 개발 이름은 한국어와 코드가 같은 말이다 — 스토리라인 = `storyline`. 화면 문구는 따로 정한다.
- 운영자 화면은 공용 스토리라인의 판정 근거와 기준을 다루고 운영 문서와 같은 말을 쓴다.

## 결정

- **독자 화면의 탭과 화면 제목은 "사건 흐름", 본문은 "흐름"이다.**
- **기간에 크게 보도된 흐름의 배지는 "큰 화제"다.**
> 부분 대체(운영자 화면 · 운영 문서) — [ADR-0320](0320-operator-screens-operations-docs-and-the-evaluation-tool-also-call-a-storyline-an-event-flow-and-a-big-one-a-big-topic.md). 지금 상태: [web Spec §2](../specs/web.md#2-용어)
- **개발 이름(스토리라인 · 사용자 스토리라인 · 스토리라인 구독)과 운영자 화면 · 운영 문서의 "스토리라인"은 그대로다.**

## 결과

> 부분 대체 — [ADR-0320](0320-operator-screens-operations-docs-and-the-evaluation-tool-also-call-a-storyline-an-event-flow-and-a-big-one-a-big-topic.md). 지금 상태: [web Spec §2](../specs/web.md#2-용어)
- 같은 개체를 독자 화면과 운영자 화면이 다른 말로 부른다.
- 관심사 히스토리의 "관심의 흐름" 같은 문구는 사건 흐름과 헷갈리지 않게 다른 말을 쓴다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| "스토리라인"을 그대로 둔다 | 한 화면 안에서 같은 것을 두 말로 부른다 |
| 개발 이름도 "흐름"으로 바꾼다 | 코드 · 문서 · 표 이름 전체가 바뀌고, 흐름 소속 판정 같은 다른 뜻과 겹친다 |
| 운영자 화면도 함께 바꾼다 | 운영 문서 · 운영자 API 의 말과 어긋난다 — 따로 정한다 |
