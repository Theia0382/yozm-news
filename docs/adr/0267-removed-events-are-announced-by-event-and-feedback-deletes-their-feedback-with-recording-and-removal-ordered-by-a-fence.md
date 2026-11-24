# ADR-0267: 사건을 지우면 `event` 가 지운 사건 ID 를 알리고 `feedback` 이 그 사건의 반응을 지우며, 기록과 삭제는 울타리로 차례를 세운다

- 상태: 승인
- 날짜: 2026-11-20
- 근거: [이슈 #226](https://github.com/Theia0382/yozm-news/issues/226) — 기존 꼴(지운 것을 ID 로 알리고 가리키는 모듈이 따른다)과 가리킬 곳 없는 행을 남기지 않는 기준으로 사건 삭제 알림을 골랐고, 재판정으로 원래 사건이 비어 지워지면 그 반응도 지우는 것과 지운 항목 섹션의 항목 기록을 지우는 것을 정했다. 구현 커밋 `cc5da40`(사건 삭제 알림) · `a948926`(반응 따르기 · 울타리 · 지운 항목 섹션의 항목 기록)
- 관련: [ADR-0197](0197-feedback-on-removed-articles-is-deleted-from-both-the-current-state-and-the-history-and-recording-and-removal-are-ordered-by-a-fence-lock.md)(전체 대체 — 지운 기사의 반응 지우기와 기사 확인 울타리), [ADR-0060](0060-article-deletion-announced-by-removed-article-ids-and-followed-by-referencing-modules.md)(보충 — 지운 것을 ID 로 알리고 가리키는 모듈이 따르는 꼴을 사건 삭제로 넓힌다), [ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md)(항목 기록 · 사건 기록), [ADR-0266](0266-feedback-is-recorded-under-the-original-event-id-and-feedback-resolves-merged-events-through-event-when-reading-with-the-last-pressed-value-winning.md)(원래 사건 ID 로 기록), [ADR-0186](0186-digests-follow-removed-articles-by-deleting-their-rows-and-show-no-summary-without-any-citation.md)(다이제스트가 지운 기사를 따라 섹션을 지운다), [ADR-0105](0105-storylines-link-incrementally-from-event-change-notifications-and-events-publish-changed-event-ids.md)(사건 변화 알림)

## 맥락

- 반응은 사건 키다([ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md)). 사건은 기사가 모두 빠지면 지워진다 — 운영자가 기사를 지울 때와 재판정으로 마지막 기사가 다른 사건으로 옮겨 갈 때다. 그 사건에 합쳐져 있던 사건 행도 함께 지워진다. 지운 사건 ID 는 다시 쓰이지 않는다.
- 지운 사건은 주제를 셀 소속 기사가 없어 그 반응이 관심도에 쓰이지 못한다. 싫어요 거름에도 다시 나올 사건이 없다.
- `event` 는 사건 변화를 바뀐 사건 ID 로만 알린다(`EventsChanged` — 무엇이 일어났는지는 싣지 않는다). 그것으로 삭제를 가리려면 받는 쪽이 사건 변화마다 사건을 다시 읽어야 한다.
- 따르는 리스너는 삭제가 커밋된 뒤에 돈다. 기록이 사건이 있는지 확인한 뒤 커밋하기 전에 삭제가 지나가면 지운 사건을 가리키는 행이 남는다.
- 다이제스트는 인용이 모두 지운 기사인 항목 섹션을 지운다([ADR-0186](0186-digests-follow-removed-articles-by-deleting-their-rows-and-show-no-summary-without-any-citation.md)). 그 항목에 누른 항목 기록은 가리킬 항목을 잃는다. `digest` 는 `feedback` 을 읽으므로 `feedback` 이 `digest` 의 이벤트를 받으면 순환이다.

## 결정

- **`event` 는 사건을 지울 때 지운 사건 ID 를 알린다(`EventsRemoved`)** — 빈 사건과 그 사건에 합쳐져 함께 지운 사건이고, 사건을 지우는 같은 트랜잭션에서 발행한다. 사건을 지우는 길은 하나라 지운 기사 따르기와 재판정 두 계기가 모두 알린다. 사건 변화 알림(`EventsChanged`)은 그대로 함께 낸다.
- **`feedback` 은 그 알림을 받아 그 사건들의 현재 상태 행과 이력 행을 모두 지운다.** 항목 기록은 건드리지 않는다 — 그 항목에 누른 사실은 섹션이 남아 화면에 보이는 동안 참이다. 재판정으로 원래 사건이 비어 지워지면 그 반응은 옮겨 간 사건을 따라가지 않고 지워진다.
- **기록과 삭제는 `feedback` 의 울타리 하나로 차례를 세운다.** 기록은 울타리를 공유로 잡은 뒤 항목의 사건 가운데 지금 있는 사건을 확인하고 그 사건만 기록하며, 삭제는 울타리를 배타로 잡고 지운다.
- **지운 항목 섹션의 항목 기록은 `digest` 가 섹션을 지우는 같은 트랜잭션에서 `feedback` 의 공개 API(`DigestItemRemoval`)로 지운다** — 다이제스트마다 지운 항목의 키를 넘긴다. 울타리를 배타로 잡는다. 잠금 순서는 다이제스트 행 → 울타리이고, 울타리를 먼저 잡는 기록은 다이제스트 행을 잠그지 않아 교착이 없다.

## 결과

- 삭제가 지나간 뒤에는 지운 사건을 가리키는 반응 행과 지운 항목을 가리키는 항목 기록이 남지 않는다 — 삭제 뒤에 시작한 기록은 그 사건이 없어 사건 기록을 쓰지 않고, 진행 중이던 기록은 삭제보다 먼저 커밋돼 함께 지워진다.
- 사용자가 남긴 반응이 운영자의 소스 삭제나 재판정으로 사라진다.
- 화면을 열어 둔 사이 지워진 사건이 든 항목도 누를 수 있다 — 항목 기록은 남고 그 사건의 기록은 쓰지 않는다.
- 사건 삭제와 지운 항목 섹션 따르기 동안 모든 사용자의 기록이 그 트랜잭션만큼 기다린다. 기록마다 잠금 문장 하나와 지금 있는 사건을 묻는 조회 하나가 는다.
- 다이제스트의 섹션 사건 행은 지운 사건 ID 를 그대로 둔다 — 화면에 보인 항목 구성이고, 읽을 때 지운 사건은 풀기에서 빠진다.
- `feedback` 은 기사를 들지 않아 지운 기사 알림을 받지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 반응을 지우지 않고 읽을 때 빠지게 둔다 | 가리킬 사건이 없는 행이 탈퇴 때까지 남는다 — 지운 것을 가리키는 행을 남기지 않는 기준과 다르다 |
| `feedback` 이 사건 변화 알림(`EventsChanged`)을 받아 사건을 다시 읽고 없는 사건의 행을 지운다 | 열기 · 붙이기 · 합치기 · 닫기마다 리스너가 돌아 사건을 읽는다. 지운 것만 알리는 이벤트를 두면 그 비용이 없다 |
| 울타리 없이 기록 때 사건이 있는지만 확인한다 | 확인과 커밋 사이에 삭제가 지나가면 지운 사건을 가리키는 행이 남고 다시 지울 계기가 없다 |
| 지운 항목 섹션의 항목 기록을 둔다 | 그 다이제스트 화면에 항목이 없어 보이지 않을 뿐, 가리킬 항목 없는 행이 탈퇴 때까지 남는다 |
| `digest` 가 섹션 삭제를 이벤트로 알리고 `feedback` 이 받는다 | `digest` 가 `feedback` 을 읽어 반대 방향 의존은 순환이다 |

## 후속 기록

- 2026-11-22: 결과 "다이제스트의 섹션 사건 행은 지운 사건 ID 를 그대로 둔다"의 예외(생성 때 보인 구성의 기록)를 변화 리포트의 인용으로 넓힘 — [ADR-0275](0275-report-citations-resolve-removed-and-merged-events-and-storylines-when-read-and-a-section-that-lost-every-citation-is-hidden.md) · [report Spec "리포트 조회"](../specs/report.md#리포트-조회) report-R11 · R12
