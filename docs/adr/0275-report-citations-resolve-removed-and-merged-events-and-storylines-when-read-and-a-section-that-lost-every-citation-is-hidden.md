# ADR-0275: 리포트 인용은 지운 · 합친 사건과 스토리라인을 읽을 때 풀고, 인용을 모두 잃은 섹션은 보이지 않는다

- 상태: 승인
- 날짜: 2026-11-22
- 근거: [이슈 #237](https://github.com/Theia0382/yozm-news/issues/237) — 기점 `d25590c` 의 사건 삭제 알림(event-R25) · 스토리라인 합침 · 지움 알림(storyline-R35)과 그것을 따르는 기존 꼴(반응 · 다이제스트 인용 · 섹션 사건 행 · 스토리라인 구독)을 대조하고, 읽을 때 푸는 꼴을 골랐다
- 관련: [ADR-0267](0267-removed-events-are-announced-by-event-and-feedback-deletes-their-feedback-with-recording-and-removal-ordered-by-a-fence.md)(보충 — 결과의 섹션 사건 행 예외를 리포트 인용으로 넓힌다), [ADR-0266](0266-feedback-is-recorded-under-the-original-event-id-and-feedback-resolves-merged-events-through-event-when-reading-with-the-last-pressed-value-winning.md) · [ADR-0191](0191-the-consumer-query-of-storyline-subscriptions-is-opened-with-the-subscriptions-and-resolves-ids-without-writing.md)(같은 꼴 — 합침은 읽을 때 푼다), [ADR-0186](0186-digests-follow-removed-articles-by-deleting-their-rows-and-show-no-summary-without-any-citation.md)(같은 꼴 — 인용이 없는 글은 보이지 않는다), [ADR-0060](0060-article-deletion-announced-by-removed-article-ids-and-followed-by-referencing-modules.md)(지운 것을 ID 로 알리고 가리키는 모듈이 따른다)

## 맥락

- 리포트 인용은 사건 · 스토리라인을 ID 로 가리킨다. 사건 · 스토리라인은 합쳐지거나 지워진다.
- 기존 꼴은 데이터의 성격에 따라 갈린다.
  - 합침은 어디서나 원래 ID 로 두고 읽을 때 푼다 — 반응(ADR-0266), 스토리라인 구독의 소비자 조회(ADR-0191).
  - 지움은 지금 쓰이는 데이터면 알림을 받아 행을 지운다 — 반응(ADR-0267), 다이제스트 인용(ADR-0186). ADR-0267 은 읽을 때 거르는 꼴을 "지운 것을 가리키는 행을 남기지 않는 기준과 다르다"는 이유로 기각했다.
  - 생성 때 보인 구성의 기록이면 지운 ID 를 남기고 읽을 때 뺀다 — 다이제스트의 섹션 사건 행(ADR-0267 결과).
- 알림으로 지우는 길은 그것만으로 끝나지 않는다. 합쳐진 스토리라인을 인용한 뒤 남는 스토리라인이 지워지면 알림에 남는 쪽만 실린다(storyline-R35). 생성 중 지운 것을 막으려면 저장 때 거르기와 잠금 순서가 필요하다.
- 다이제스트는 인용이 모두 지운 기사인 섹션을 지운다 — 지운 것의 내용으로 쓴 글을 보이지 않는다(ADR-0186).

## 결정

- **리포트 인용 행은 생성 때의 ID 그대로 둔다.** 조회가 합친 사건 · 스토리라인은 남는 것으로 풀고, 지운 것은 뺀다. `report` 는 사건 · 스토리라인의 알림을 받지 않는다. 리포트 인용은 생성 때 리포트가 짚은 것의 기록이라 다이제스트 섹션 사건 행과 같은 예외에 든다.
- **인용을 모두 잃은 섹션은 보이지 않는다.** 인용이 하나 이상 있던 갈래 섹션의 인용이 조회에서 하나도 남지 않으면 그 섹션을 숨긴다. 갈래 섹션이 하나 이상 있었고 모두 숨었으면 개요도 숨긴다. 처음부터 인용이 없던 섹션은 그대로 보인다. 행은 지우지 않는다.

규칙은 [report Spec](../specs/report.md#리포트-조회) report-R11 · R12 에 있다.

## 결과

- 리스너 · 저장 때 거르기 · 잠금 순서가 없다. 생성 중 지움과의 경합이 없고, 스토리라인 알림의 빈칸도 조회가 함께 메운다.
- 지운 사건 · 스토리라인을 가리키는 행이 탈퇴 때까지 남는다. 리포트 인용은 신호 · 집계의 재료가 아니고, 한 편의 인용 수는 섹션 몇 개 × 인용 몇 개다.
- 조회마다 `event` · `storyline` 의 풀기 조회를 부른다.
- 갈래 하나의 서술이 통째로 보이지 않을 수 있다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 모두 이벤트로 고쳐 쓴다 — 합침도 남는 ID 로 다시 쓰고 지운 것은 지운다 | 생성 때의 기록을 고쳐 쓰고, 사건 합침은 알림에 실리지 않아 따로 찾아야 하며, 조회 앞 따라가기와 생성 중 지움을 막는 장치가 함께 필요하다 |
| 합친 것은 읽을 때 풀고 지운 것은 이벤트로도 지운다 | 알림의 빈칸 때문에 읽을 때 빼기를 함께 두어야 해 이 결정의 장치를 모두 갖추고, 그 위에 리스너 둘 · 저장 때 거르기 · 잠금 순서를 더한다 |
| 인용을 모두 잃은 섹션도 글을 보인다 | 지운 사건 · 스토리라인의 내용으로 쓴 문장이 보인다 — 다이제스트가 피한 것이다 |

## 후속 기록

- 2026-11-24: 결정 절 "조회가 합친 사건 · 스토리라인은 남는 것으로 풀고, 지운 것은 뺀다"의 통계 스냅숏 흐름 줄과 "인용을 모두 잃은 섹션은 보이지 않는다"의 숨긴 수 — [ADR-0316](0316-a-change-report-view-carries-each-visible-sections-numbers-as-typed-values-and-resolves-flow-lines-like-citations.md) · [report Spec](../specs/report.md#리포트-조회) report-R12 · R25
