# ADR-0188: 스토리라인 구독은 `storyline` 이 알리는 합침 · 지움과 사용자의 조회 · 저장 앞에서 따라가고, 사라진 구독은 남은 사건을 가리켜 알린다

- 상태: 승인. 부분 대체 — 구독 행을 고쳐 쓰는 따라가기([ADR-0282](0282-flow-tracking-points-to-a-user-storyline-which-user-handlers-store-under-the-settings-lock-while-readers-get-the-result-of-the-same-rule.md))
- 날짜: 2026-11-16
- 근거: [이슈 #184](https://github.com/Theia0382/yozm-news/issues/184) — 구현 커밋 `bc30a27`(`storyline` 의 알림 · 묶음 찾기) · `19821b4`(`user` 의 따라가기 · 사라진 구독 알림)
- 관련: [ADR-0105](0105-storylines-link-incrementally-from-event-change-notifications-and-events-publish-changed-event-ids.md)(보충 — 결정 "스토리라인을 가리키는 쪽(앞으로의 구독)이 생기면 지운 스토리라인 ID 를 알린다"와 결과 "구독이 생길 때 지운 ID 알림을 더한다"가 미룬 장치를 정한다), [ADR-0108](0108-storylines-with-the-same-events-merge-and-a-storyline-left-with-one-event-merges-or-dissolves.md)(보충 — 결과 "가리키는 쪽이 생기면 지운 ID 를 알리는 장치가 맡는다"), [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(보충 — 결정 "스토리라인 구독(사용자와 스토리라인의 관계와 그 변경 이력)은 … `user` 에 둔다"의 따르는 방식), [ADR-0175](0175-tag-preferences-follow-tag-set-activation-events-and-are-rewritten-to-active-keys.md)(같은 꼴 — 참조 대상의 이벤트와 조회 · 저장 앞에서 따라간다), [ADR-0186](0186-digests-follow-removed-articles-by-deleting-their-rows-and-show-no-summary-without-any-citation.md)(같은 꼴 — 기사 정보를 복사하지 않고 ID 로만 가리킨다), [ADR-0060](0060-article-deletion-announced-by-removed-article-ids-and-followed-by-referencing-modules.md)(같은 꼴 — 지운 ID 를 알리고 가리키는 쪽이 따른다)

## 맥락

- 스토리라인 구독은 `user` 가 스토리라인 ID 로 갖는다. 스토리라인은 정돈에서 다른 스토리라인에 합쳐지거나(합쳐진 행은 남는 스토리라인을 가리킨다) 지워진다(구성 사건이 없거나 사건 하나만 남아 푼다). 지운 스토리라인은 행이 남지 않아, 그 ID 로는 무엇이었는지 알 수 없다. 스토리라인에는 이름이 없다.
- 합쳐진 ID 는 조회로 남는 쪽을 찾을 수 있다. 그러나 구독 행을 그대로 두면 "현재 구독은 이력의 투영"이 문자 그대로 성립하지 않고(옛 ID 를 더하고 남는 ID 를 뺀 이력), 지운 스토리라인의 구독은 사용자 모르게 사라진다.
- 다른 모듈의 변화를 받는 쪽이 자기 데이터를 고치는 꼴은 이미 있다 — 기사 삭제(`ArticlesRemoved`), 사건 변화(`EventsChanged`), 태그 체계 활성 전환(`TagSetActivated` — 이벤트와 조회 · 저장 앞에서 따라간다).
- 기사는 운영자가 소스 · 피드를 지울 때 함께 지워진다(언론사 삭제 요청). 다이제스트는 기사 정보를 복사하지 않고 ID 로만 가리켜, 지운 기사의 제목이 남지 않는다.

## 결정

- **`storyline` 은 정돈이 합치거나 지운 스토리라인을 이벤트 `StorylinesRetired` 로 알린다.** 정돈 트랜잭션에서 한 번 발행한다. 사용자 정보는 싣지 않는다.
- **이벤트는 무엇이 일어났는지 싣지 않는다** — 받는 쪽이 스토리라인 묶음 찾기로 지금 상태를 읽어, 남는 스토리라인이 있으면 합쳐진 것이고 없으면 지워진 것이다. 풀기(사건 하나만 남아 지운 것)는 남은 사건 ID 를 함께 싣는다 — 행이 사라진 뒤 무엇이었는지 가리킬 재료다.
> 부분 대체 — [ADR-0282](0282-flow-tracking-points-to-a-user-storyline-which-user-handlers-store-under-the-settings-lock-while-readers-get-the-result-of-the-same-rule.md). 지금 상태: [user Spec "스토리라인 구독"](../specs/user.md#스토리라인-구독) user-R52 · R53
- **`user` 는 그 이벤트를 받아 구독을 바로 고쳐 쓰고, 사용자의 구독 조회 · 저장 앞에서도 같은 정본으로 따라간다.** 합쳐진 스토리라인의 구독은 남는 스토리라인으로 옮기고(구독 시각은 가장 이른 것), 지운 스토리라인의 구독은 뺀다. 고친 것은 원인(합쳐짐 · 사라짐)과 함께 이력에 남긴다 — 관심 신호는 원인이 사용자인 행만이다.
- **사라진 구독은 사용자에게 알린다.** 알림은 스토리라인 ID 와 풀기에서 남은 사건 ID 만 담는다. 알림함을 열 때 그 사건의 지금 대표 기사 제목을 읽어 보인다 — 기사가 지워졌으면 제목이 없다.

## 결과

- 구독 행은 리스너가 처리한 뒤 언제나 남는 스토리라인 ID 다 — 그 전의 짧은 틈과 리스너가 놓친 사용자는 그 사용자의 다음 조회 · 저장이 고친다. 그래서 다른 모듈이 구독을 읽을 때는 여전히 남는 쪽으로 풀어야 한다.
- 이력의 고쳐 쓰기 시각은 합치거나 지운 때에 가깝다.
- `storyline` 이 처음으로 이벤트를 낸다. 정돈마다 합치거나 지운 것이 있으면 발행 기록이 하나 생긴다.
- 지운 기사의 제목이 알림에 남지 않는다 — 기사를 따르는 장치가 따로 없다. 지우기(구성 사건이 모두 지워짐)와 리스너가 놓친 사라짐은 가리킬 사건이 없어 제목 없이 알린다. 풀기의 남은 사건도 이미 지워졌거나 합쳐졌을 수 있다 — 합쳐졌으면 남는 사건의 대표 기사, 지워졌으면 제목이 없다.
- 알림함을 열 때마다 그 쪽의 사라진 구독 항목 수만큼 사건 · 기사를 읽는다.
- 알림은 키 없이 열리므로, 키가 없는 사용자도 이 알림에서 대표 기사 제목을 본다 — 구독했던 흐름마다 제목 하나다.
- 스토리라인을 정돈이 아닌 길로 지우거나 다시 묶는 기능은 같은 이벤트를 내야 구독이 따라간다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 조회 때 풀기만 한다(구독 행은 그대로) | 현재 구독이 이력의 투영으로 문자 그대로 성립하지 않고, 지운 스토리라인의 구독 행이 남으며 사용자는 사라진 것을 모른다 |
| 사용자의 조회 · 저장 앞에서만 따라간다(이벤트 없음) | 사용자가 구독을 다룰 때까지 고쳐 쓰기가 늦어 이력 시각이 실제와 어긋나고, 사라진 구독을 알릴 때가 없다 |
| 합침 · 지움을 두 이벤트로 나눠 내용을 싣는다 | 받는 쪽이 이벤트 내용을 믿으면 처리 순서가 바뀔 때(합침보다 남는 쪽의 지움이 먼저 처리) 틀린다 |
| 알림에 대표 기사 제목을 담는다 | 기사 정보의 사본이 `article` 밖에 생겨 지운 기사의 제목이 남는다 |
| 알림에 제목을 담고 지운 기사를 따라 지운다 | 사본과 그것을 따르는 장치(알림 내용을 기사 ID 로 찾는 리스너)가 하나 더 생긴다 |
| 사라진 수만 알린다 | 사용자가 무엇을 잃었는지 모른다 |
| 풀린 스토리라인의 구독을 남은 사건이 다시 든 스토리라인으로 옮긴다 | 다시 판정은 뒤의 일이고 결과가 무관한 스토리라인일 수 있다 — 사용자가 고르지 않은 흐름을 구독하게 된다 |

## 후속 기록

- 2026-11-18: 결정 "합침 · 지움을 따른다" — 관계 판정의 조각 합치기도 같은 알림을 낸다([ADR-0234](0234-fragments-of-one-matter-are-recognised-by-the-relation-judgement-and-merged-above-a-merge-probability-without-a-way-back.md))
- 2026-11-22: 결정 "사라진 구독은 사용자에게 알린다" — 알림은 추적하던 사용자 스토리라인이 이을 흐름 없이 사라질 때 남긴다([ADR-0282](0282-flow-tracking-points-to-a-user-storyline-which-user-handlers-store-under-the-settings-lock-while-readers-get-the-result-of-the-same-rule.md)), 지금 상태는 [user Spec "스토리라인 구독"](../specs/user.md#스토리라인-구독) user-R54
