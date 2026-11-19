# ADR-0259: 평가기 요청 마감 · 판정 한 번의 가장 긴 시간 · 일시 실패 뒤 간격은 evaluation 의 공개 타입 하나가 정본이고, 판정을 부르는 대기의 점유 기간은 판정 한 번의 가장 긴 시간에서 계산한다

- 상태: 승인
- 날짜: 2026-11-19
- 근거: [이슈 #196](https://github.com/Theia0382/yozm-news/issues/196) — 평가 대기 · 관계 대기의 일시 실패 처분을 고치며 판정을 부르는 대기의 일정을 대조. 같은 간격 산식이 `tag` · `event` · `storyline`(잇기 · 관계) 네 곳에 따로 있고, 점유 기간은 Jev 요청 마감 15초로 근거를 계산해 숫자로 적혀 있었다. 잇기 회차가 사건 하나에 판정을 시도 상한만큼 물을 수 있음을 회차 코드로 확인
- 관련: [ADR-0059](0059-evaluation-job-limits-concurrency-and-retries-with-db-leases-on-queue-rows.md)(평가 작업의 점유 기간 · 재시도 간격 — 값을 두는 자리를 정한다, 보충), [ADR-0063](0063-jev-called-through-one-system-one-schema-context-overflow-probed-with-empty-state-latest-model-alias-by-default.md)(결과 절의 "요청 마감이 그 부등식에 묶인다" — 점유 기간이 요청 마감에서 계산된다, 보충), [ADR-0213](0213-event-merge-defers-events-whose-check-did-not-finish-until-a-retry-time-on-the-event-row-and-never-drops-them.md)(병합 미룸의 간격 — 정본 자리, 보충), [ADR-0254](0254-evaluation-queue-defers-transient-failures-without-an-attempt-limit-and-counts-retrying-rows-for-the-operator.md) · [ADR-0255](0255-storyline-linking-and-relation-queues-defer-transient-failures-until-a-retry-time-and-never-drop-them.md)(같은 간격으로 미룬다)

## 맥락

- 판정 공개 API 한 번은 평가기를 한 번 부르고, Jev 평가기는 요청을 많아야 둘 보낸다 — 본 요청과, 문맥 초과일 때 같은 질문을 빈 state 로 다시 묻는 확인 요청이다. 요청마다 마감이 15초다([ADR-0063](0063-jev-called-through-one-system-one-schema-context-overflow-probed-with-empty-state-latest-model-alias-by-default.md)). 이 마감은 Jev 어댑터 안의 상수였다.
- 판정을 부르는 대기 넷(평가 대기 · 사건 배정 · 스토리라인 잇기 · 관계)은 점유 기간을 숫자로 두고, 근거를 주석에 "묶음 × 요청 둘 × 15초" 꼴로 적었다. 요청 마감이 바뀌어도 점유 기간은 따라가지 않는다.
- 잇기 대기의 근거는 사건마다 묻기 둘로 셌지만, 회차는 시도마다 한 번 물을 수 있어 사건 하나에 시도 상한(3)만큼 묻는다. 가장 긴 경우 32 × 3 × 30초 = 48분이 점유 기간 40분을 넘었다 — 점유가 판정 중에 만료되면 다른 회차가 같은 사건을 다시 가져간다.
- 일시 실패 뒤 다시 묻기까지의 간격(1분부터 두 배, 1시간에서 멈춤)을 평가 대기 · 사건 배정(병합 미룸도 같은 식) · 잇기 대기 · 관계 대기가 같은 산식으로 따로 두었다. 관련 ADR 은 서로를 "같은 간격"으로 인용한다.
- `llm` 은 생성 호출의 마감과 가장 긴 호출 시간을 공개 상수로 내고, `digest` 가 임대를 그 값에서 계산한다([ADR-0201](0201-the-gateway-resends-only-transient-generation-failures-and-sets-the-request-deadline-per-call-kind.md) · [ADR-0232](0232-a-running-digest-is-never-started-again-and-stalled-digests-end-interrupted-after-a-lease-or-a-start-deadline.md)).

## 결정

- **`evaluation` 의 공개 타입 `EvaluatorTiming` 이 평가기 시간의 정본이다.** 평가기 요청 하나의 마감(15초), 판정 한 번이 평가기를 기다리는 가장 긴 시간(요청 수의 상한 2 × 요청 마감 = 30초), 일시 실패 뒤 다시 묻기까지의 간격(`retryDelay` — min(1분 × 2^(일시 실패 수 − 1), 1시간))이다. Jev 어댑터도 요청 마감을 여기서 읽는다.
- **판정을 부르는 대기의 점유 기간은 판정 한 번의 가장 긴 시간 × 묶음 크기 × 항목마다 판정 수 + 여유로 계산한다.** 항목마다 판정 수는 평가 대기 · 사건 배정이 1, 잇기 대기가 시도 상한(3), 관계 대기가 쌍 상한(5)이다. 여유는 대기마다 판정 밖의 시간(대기 행 · 기록 트랜잭션)을 덮게 두고 지금 값을 지킨다 — 평가 대기 30분 · 사건 배정 20분 · 관계 대기 20분은 그대로이고, 잇기 대기만 48분 + 2분 = 50분으로 는다.
- **일시 실패 뒤 다시 묻는 대기는 모두 `retryDelay` 를 부른다.** 사건 병합의 미룸은 미룬 까닭과 무관하게 같은 간격이다([ADR-0213](0213-event-merge-defers-events-whose-check-did-not-finish-until-a-retry-time-on-the-event-row-and-never-drops-them.md)).

## 결과

- 요청 마감이나 판정 한 번의 요청 수가 바뀌면 점유 기간이 함께 바뀐다. 묶음 크기 · 시도 상한 · 쌍 상한을 바꿔도 점유 기간이 따라간다.
- 잇기 회차의 점유가 판정을 기다리는 동안 만료되지 않는다. 대신 회차가 멈춰 점유가 남은 사건을 다른 회차가 다시 가져가기까지 10분 늦어진다.
- 대기마다 간격을 따로 조정할 수 없다 — 다르게 하려면 이 결정을 바꾼다.
- 평가기를 부르지 않는 대기(사건 묶기의 기사 표현 · 빠진 태그 채움)의 점유 기간은 이 값과 무관하다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `storyline` 안의 잇기 · 관계 대기만 정본 하나로 묶는다 | `tag` · `event` 의 같은 산식이 남는다 — 같은 평가기에 대한 같은 지식이 세 곳에 있다 |
| `event` 에 병합 미룸용 메서드를 남겨 정본을 부르게 한다 | 같은 값을 가리키는 이름이 하나 더 생긴다. 같은 간격을 쓴다는 결정은 ADR-0213 에 있다 |
| 점유 기간은 숫자로 두고 시험이 "점유 기간 ≥ 판정 시간" 부등식을 확인한다 | 산식이 시험에만 있어 프로덕션 값의 근거가 코드에 없다. 마감이 바뀌면 시험이 깨질 뿐 값은 따라가지 않는다 |
| 여유 없이 계산값을 그대로 점유 기간으로 쓴다 | 판정 밖의 시간이 점유 기간에 들지 않는다. 지금 값이 모두 바뀌어 측정해 온 기준이 흔들린다 |
