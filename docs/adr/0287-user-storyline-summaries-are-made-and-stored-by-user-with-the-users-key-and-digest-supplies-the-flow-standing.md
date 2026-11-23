# ADR-0287: 사용자 스토리라인의 정리는 `user` 가 사용자 키로 만들고 저장하며, 흐름 판정은 `digest` 가 공급한다

- 상태: 승인
- 날짜: 2026-11-23
- 근거: [이슈 #245](https://github.com/Theia0382/yozm-news/issues/245) — 관심이 쌓인 흐름을 사용자 LLM 으로 정리해 보이려는데, 그 글을 어느 모듈이 만들고 어디에 두는지가 정해지지 않았다. 리드가 자리를 `user` 로 정했다. 구현 커밋 `4e6da46` · `d6ebeb9` · `776e35e` · `d552119` · `83665ae`
- 관련: [ADR-0282](0282-flow-tracking-points-to-a-user-storyline-which-user-handlers-store-under-the-settings-lock-while-readers-get-the-result-of-the-same-rule.md)(부분 대체 — 사용자 스토리라인을 저장하는 계기에 정리 작업이 더해진다), [ADR-0193](0193-the-per-user-storyline-information-is-the-received-news-counted-from-the-digest-shown-for-each-date.md)(부분 대체 — 사용자마다 달라지는 스토리라인 정보가 하나 는다), [ADR-0278](0278-followed-flows-are-tops-at-or-above-the-follow-threshold-or-subscribed-less-the-excluded-and-the-keep-threshold-is-0-2.md)(부분 대체 — 판정을 읽는 길은 화면용 조회가 아니라 `user` 가 선언한 공급 인터페이스다), [ADR-0003](0003-byok-personalized-digest.md)(보충 — 사용자 키로 만드는 생성물이 하나 는다), [ADR-0233](0233-digest-failure-notices-come-through-an-event-declared-by-the-receiving-user-module-for-every-failed-digest.md)(같은 꼴 — 받는 쪽이 선언하는 이벤트), [ADR-0281](0281-a-user-storyline-is-a-flow-that-received-interest-signals-and-tracks-the-tops-reached-forward-along-follows-from-its-seeds-as-one-group.md)(정리의 대상)

## 맥락

- 사용자 스토리라인(관심 신호를 준 흐름)은 `user` 가 저장한다. 그 흐름이 관심 흐름인지는 `digest` 의 관심도가 판정한다.
- `digest` 는 `user` 의 공개 조회를 읽어 `user` 에 기댄다. `user` 가 `digest` 를 부르면 두 모듈이 서로 기댄다.
- 공용 스토리라인은 모든 사용자가 함께 쓰고 생성형 LLM 의 출력을 담지 않는다. 정리는 한 사용자의 키로 만든 그 사용자의 글이다.
- 모든 LLM 호출과 사용자 키의 복호화는 `llm` 의 관문을 거친다.

## 결정

- **정리(제목 · 흐름 요약 · 최근 전개)는 `user` 가 만들고 `user` 의 표에 사용자 스토리라인마다 저장한다.** 공용 스토리라인의 표에는 두지 않는다. 사용자 스토리라인이 지워지면 함께 지워진다.
- **생성은 `llm` 의 관문으로 그 사용자의 저장한 키와 쓸 모델로 부른다.** `user` 는 키를 다루지 않는다. 생성기는 포트이고 로컬 대체물이 함께 있다.
- **다이제스트가 끝났다는 알림은 `user` 가 선언한 이벤트(`DigestCompleted`)로 받는다.** `digest` 가 끝낸 행을 쓰는 트랜잭션에서 낸다.
- **흐름 판정(관심 흐름 · 남길 흐름의 꼭대기)은 `user` 가 선언하고 `digest` 가 구현하는 공급 인터페이스(`StorylineStandingNow`)로 묻는다.** `user` 는 관심을 계산하지 않고 기준을 갖지 않는다.
- **정리 작업은 사용자 스토리라인을 먼저 맞춰 저장한다.** 설정 행을 잠근 뒤 흐름 추적의 처리기와 같은 맞추기를 부른다.
- **정리를 읽는 공개 조회(`UserStorylineSummaries`)는 저장한 행만 읽는다.** 맞추지 않고 잠그지 않는다. 화면 응답은 `reader` 가 엮는다.
- **지운 기사를 넣은 정리는 지운다.** 글이 그 기사의 제목 · 요약문에서 나왔기 때문이다. 다음 정리가 남은 사건으로 다시 만든다.

## 결과

- `user` 가 `llm` · `article` · `event` 에 기대고 LLM 을 부르는 작업 하나를 갖는다.
- `digest` 는 정리를 모른다 — 끝났다는 이벤트를 내고 판정을 답할 뿐이다.
- 정리를 읽는 화면은 관심 재료를 읽지 않는다.
- 사용자 키로 만든 글이 `user` 의 표에 평문으로 있다 — 그 사용자의 화면에만 나간다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `digest` 가 만들고 저장한다 | 정리의 대상인 사용자 스토리라인이 `user` 의 개체라 저장 · 맞추기 · 삭제를 두 모듈이 나눠 갖는다 |
| 새 모듈이 만들고 저장한다 | 사용자 스토리라인을 토대로 한 글인데 개체와 글이 다른 모듈에 놓이고, 지움을 이벤트로 따라가야 한다 |
| 저장은 `user`, 생성은 다른 모듈 | 저장 때 견줌(추적 대상 · 판)이 모듈을 건너고, 생성기 포트의 주인이 저장 모델을 모른다 |
| 공용 스토리라인에 정리를 둔다 | 한 사용자의 키로 만든 글을 모든 사용자가 본다 — 비용 주체와 격리가 깨진다 |
| `user` 가 `digest` 의 공개 조회를 부른다 | 두 모듈이 서로 기댄다 |
