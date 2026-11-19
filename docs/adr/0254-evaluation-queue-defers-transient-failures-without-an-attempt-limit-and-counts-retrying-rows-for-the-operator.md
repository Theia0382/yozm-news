# ADR-0254: 평가 대기의 일시 실패는 시도 상한 없이 1시간까지 늘어나는 간격으로 미루고, 운영자 건수는 시도 소진 대신 일시 실패를 겪은 대기를 센다

- 상태: 승인
- 날짜: 2026-11-19
- 근거: [이슈 #196](https://github.com/Theia0382/yozm-news/issues/196) — 평가기의 일시 실패가 이어질 때 평가 대기의 기사가 실패로 남는 조건을 코드로 정리하고, 같은 평가기를 쓰는 사건 배정 · 병합의 처분과 견줘 구현. 일시 실패가 세 시간 이어지는 회차를 고치기 전 코드에서 실패하는 시험으로 재현
- 관련: [ADR-0059](0059-evaluation-job-limits-concurrency-and-retries-with-db-leases-on-queue-rows.md)(결정 "재시도는 대기 행의 상태로 한다"의 "다섯 번째에 실패로 남긴다"와 값의 "시도 상한" — 부분 대체), [ADR-0058](0058-evaluator-failures-in-three-kinds-and-unavailable-evaluator-skips-article-retries.md)(일시 실패의 재시도 — 평가 작업에서 상한을 두지 않는다, 보충), [ADR-0062](0062-tag-set-versions-from-operator-text-activated-forward-with-key-lineage-and-recent-reevaluation.md)(재평가가 되돌리는 실패 행 — 시도 소진 행이 생기지 않는다, 보충), [ADR-0213](0213-event-merge-defers-events-whose-check-did-not-finish-until-a-retry-time-on-the-event-row-and-never-drops-them.md)(병합 미룸 — 같은 방식), [ADR-0255](0255-storyline-relation-queue-defers-transient-failures-until-a-retry-time-and-never-drops-them.md)(관계 대기 — 같은 방식)

## 맥락

- 평가 대기는 일시 실패마다 시도 수를 늘리고 1분부터 두 배씩(1시간에서 멈춤) 평가 가능 시각을 미루며, 다섯 번째 일시 실패에서 실패(`RETRIES_EXHAUSTED`)로 남겼다([ADR-0059](0059-evaluation-job-limits-concurrency-and-retries-with-db-leases-on-queue-rows.md)). 첫 실패부터 15분 남짓이다.
- 일시 실패는 평가기 쪽 장애(서버 오류 · 요청 제한 · 마감 초과 · 연결 실패)와 그 기사 탓을 가리지 않는다. 장애가 15분보다 길면 그동안 평가할 수 있게 된 기사가 차례로 실패로 남는다 — 평가기 사용 불가를 일시 실패로 셀 때와 같은 결과다([ADR-0058](0058-evaluator-failures-in-three-kinds-and-unavailable-evaluator-skips-article-retries.md)이 기각한 대안).
- 실패로 남은 행을 되돌리는 길은 체계 활성화의 재평가뿐이다([ADR-0062](0062-tag-set-versions-from-operator-text-activated-forward-with-key-lineage-and-recent-reevaluation.md)). 원문의 내용이 기준 버전과 같으면 활성화가 아무것도 하지 않으므로, 운영자는 체계를 바꾸거나 DB 를 고쳐야 했다. 실패로 남은 기사는 평가가 없어 태그 · 중요도가 없고, 다이제스트 후보와 주제별 보도량에 들지 못한다.
- 같은 평가기를 쓰는 다른 자리는 이렇다.
  - 사건 배정 — 시도가 소진되면 판정 실패로 끝내고 그 기사로 새 사건을 연다([ADR-0077](0077-event-assignment-queue-judged-one-article-at-a-time-with-evaluation-failure-kinds.md) · [ADR-0078](0078-articles-ending-in-a-judgement-failure-open-a-new-event-marked-as-failed.md)). 기사는 잃지 않지만 판정 없이 정해진다
  - 사건 병합 — 확인을 끝내지 못한 사건을 1시간까지 늘어나는 간격으로 미루고, 미룬 횟수로 대상에서 빼지 않는다([ADR-0213](0213-event-merge-defers-events-whose-check-did-not-finish-until-a-retry-time-on-the-event-row-and-never-drops-them.md))
- 태그 평가에는 판정 없이 정할 값이 없다 — 평가가 없으면 태그 · 중요도가 없다.
- 회차는 평가 가능 시각 순으로 50건을 점유한다. 미룬 기사는 평가 가능 시각이 뒤로 가서 회차 맨 앞을 차지하지 않는다.

## 결정

- **일시 실패는 시도 상한 없이 미룬다.** 시도 수를 늘리고 결과를 받은 시각 + min(1분 × 2^(시도 − 1), 1시간) 뒤로 미룬다. 실패로 남기지 않는다 — 실패 까닭은 기사 거부 하나다.
- **이미 시도 소진으로 남은 행은 마이그레이션이 지금 평가할 수 있는 대기로 되돌린다.** 시도 수 · 마지막 문구는 그대로 둔다.
- **운영자 건수는 시도 소진 대신 일시 실패를 겪은 대기를 센다.** 실패로 남지 않은 대기 가운데 시도 수가 1 이상인 행이다 — 대기 건수에 든다.
- 기사 거부 · 평가기 사용 불가 · 판정 예외의 처리는 그대로다([ADR-0058](0058-evaluator-failures-in-three-kinds-and-unavailable-evaluator-skips-article-retries.md) · [ADR-0059](0059-evaluation-job-limits-concurrency-and-retries-with-db-leases-on-queue-rows.md)).

## 결과

- 평가기가 일시 실패로만 답하는 동안 대기의 기사는 실패로 남지 않는다. 장애가 풀리면 늦어도 1시간 안에 다시 평가하고, 운영자가 손쓸 일이 없다.
- 장애 동안 기사마다 많아야 1시간에 한 번 묻는다. 회차는 평가할 수 있게 된 기사를 모두 묻고 실패하므로 장애 동안에도 평가기에 요청을 보낸다 — 회차 하나는 많아야 50건이고, 회차는 겹치지 않는다.
- 그 기사 탓으로 일시 실패를 거듭 내는 기사가 있으면 1시간에 한 번 묻기를 끝없이 한다 — 기사 하나에 하루 24번이다. 그 기사는 미뤄져 다른 기사의 평가를 막지 않는다. 운영자는 일시 실패를 겪은 대기의 수가 줄지 않는 것으로 알아차린다.
- 운영자 API · 화면의 평가 대기 건수에서 시도 소진(`exhausted`)이 빠지고 일시 실패를 겪은 대기(`retrying`)가 든다.
- 실패 까닭의 DB 제약이 기사 거부 하나로 좁아진다. 이 마이그레이션 뒤에 앱을 옛 판으로 되돌리면, 옛 앱은 시도 소진을 쓰는 트랜잭션에서 실패한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 회차에서 일시 실패가 거듭되면 회차를 멈추고 시도 수를 올리지 않는다 | 한 기사 탓의 일시 실패도 회차를 멈춰, 그 기사가 회차 맨 앞에서 평가 전체를 막는다. 맨 앞 기사의 시도만 세면 장애가 길 때 결국 모든 기사가 상한에 이른다 |
| 시도 상한을 늘리거나 간격을 길게 한다 | 상한을 넘는 장애에서 같은 일이 생긴다 — 상한을 옮길 뿐이다 |
| 시도 상한에 이른 행을 긴 간격 뒤 대기로 되돌린다 | 상한 없이 미루기와 결과가 같은데 상태가 둘이다 — 되돌릴 행이 실패로 세였다가 대기로 돌아간다 |
| 운영자가 실패 행을 되돌리는 운영 API 를 둔다 | 장애가 풀린 뒤 운영자가 알아차리고 손을 써야 한다. 그때까지 기사는 평가 없이 남는다 |
| 사건 배정처럼 상한에서 판정 없이 정한다 | 태그 평가는 판정 없이 정할 값이 없다 |
| 최근 성공 여부로 평가기 쪽 장애를 알아내 그 동안만 시도를 세지 않는다 | 대기 밖의 상태(마지막 성공 시각)와, 장애와 기사 탓을 가르는 기준이 하나 더 생긴다. 상한 없이 미뤄도 거듭 실패하는 기사는 대기를 막지 않는다 |
| 운영자 건수의 시도 소진 칸을 남긴다 | 늘 0 인 칸이 남고, 일시 실패가 이어지는 장애를 운영자가 볼 길이 없다 |
