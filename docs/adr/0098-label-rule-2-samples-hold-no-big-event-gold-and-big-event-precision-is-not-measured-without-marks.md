# ADR-0098: 라벨 기준 2판 표본에는 큰 사건 정답을 두지 않고, 큰 사건 표시가 없는 표본은 큰 사건 정밀도를 재지 않는다

- 상태: 승인
- 날짜: 2026-11-08
- 근거: [이슈 #100](https://github.com/Theia0382/yozm-news/issues/100) — 정답 사건의 범위를 좁히며 큰 사건 정답의 단위를 리드와 다시 정하고 도구를 그에 맞춤(`144b11d`)
- 관련: [ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md)(결정 넷째 항목의 첫 문장 "큰 사건의 정답은 라벨러가 정답 사건 × 날짜로 표시한다" — 부분 대체), [ADR-0097](0097-event-clustering-evaluation-labels-reports-of-one-happening-narrower-than-the-system-event.md)(정답 사건의 범위), [ADR-0082](0082-big-events-judged-at-query-time-by-distinct-sources-and-mean-importance-both-reaching-criteria.md)(지금 시스템의 큰 사건 판정 — 바꾸지 않는다)

## 맥락

- 큰 사건 정밀도는 날짜마다 시스템이 낸 큰 사건을, 그 사건의 그날 라벨 기사에서 가장 많은 정답 사건이 그날 정답 큰 사건인지로 판정한다. 정답 큰 사건은 라벨러가 정답 사건 × 날짜에 표시한다([ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md)).
- 정답 사건의 범위를 한 번 일어난 일로 좁혔다([ADR-0097](0097-event-clustering-evaluation-labels-reports-of-one-happening-narrower-than-the-system-event.md)). "구독과 무관하게 그날 알려 줄 만한 일"은 그 갈래들을 잇는 큰 단위의 성질이라, 좁힌 사건 하나하나에 표시하면 곧 바뀔 단위로 정답을 만들게 된다.
- 도구는 큰 사건 표시가 하나도 없는 표본에서도 시스템이 낸 큰 사건을 모두 틀림으로 세어 정밀도 0 을 기록했다.

## 결정

- **라벨 기준 2판 표본에는 큰 사건을 표시하지 않는다.** 큰 사건 정답의 단위와 표시는 사건보다 큰 단위를 설계할 때 함께 정한다.
- **큰 사건 표시가 하나도 없는 표본은 큰 사건 정밀도를 재지 않는다.** 도구는 0 을 기록하지 않고 재지 않았음을 기록한다.

## 결과

- `measure` 는 표시가 없으면 큰 사건 · 미리보기 API 를 부르지 않고, 결과 기록(형식 2)의 큰 사건 결과와 큰 사건 기준을 `null` 로 남긴다. 표시가 없는 표본에 제안 큰 사건 기준(`--big-criteria`)을 주면 운영자 API 를 부르기 전에 멈춘다(종료 2).
- 큰 사건 정밀도 계산은 도구에 그대로 있다 — 표시를 다시 두는 라벨 기준이 쓴다.
- 2판 표본으로는 지금 시스템의 큰 사건 판정과 큰 사건 기준을 재지 못한다. [로드맵](../roadmap.md)의 사건 묶기 완료 기준 가운데 큰 사건 판정 정밀도도 이 표본으로는 채우지 못한다. 큰 사건 기준의 근거는 큰 단위의 정답이 생길 때 다시 잡는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 좁힌 정답 사건 × 날짜에 큰 사건을 표시한다 | 큰 사건은 갈래를 잇는 큰 단위의 성질이라, 곧 바뀔 단위로 정답을 만든다 |
| 여러 정답 사건을 묶는 칸을 시트에 더해 그 묶음에 표시한다 | 사건보다 큰 단위의 설계를 앞당기게 된다 — 그 설계와 함께 정한다 |
| 표시가 없어도 큰 사건을 재고 정밀도를 0 으로 둔다 | 정답이 없는데 시스템이 모두 틀린 것으로 기록되어, 기준을 고치는 근거로 잘못 읽힌다 |
