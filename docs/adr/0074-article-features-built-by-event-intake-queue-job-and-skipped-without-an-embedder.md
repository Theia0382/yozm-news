# ADR-0074: 사건 묶기의 기사 표현은 `event` 대기와 작업 역할의 반복 작업이 만들고, 임베더가 없으면 건너뛰어 대기에 쌓는다

- 상태: 승인
- 날짜: 2026-11-05
- 근거: [이슈 #94](https://github.com/Theia0382/yozm-news/issues/94) — `event` 모듈을 열며 `ArticleStored` 의 발행 · 처리 역할, 이벤트 발행 재처리의 시도 상한, 평가 대기 · 평가 작업의 모양, 운영 임베더가 아직 없는 배포 구성을 확인
- 관련: [ADR-0042](0042-app-roles-as-web-worker-profiles-marked-by-platform-annotations.md)(리스너는 짧은 처리 · 무거운 일은 작업으로 — 그대로 적용한다), [ADR-0057](0057-local-substitutes-registered-only-in-local-profile-without-operator-switch.md)(구현이 없는 외부 의존의 동작은 포트를 쓰는 쪽이 정한다 — 임베더의 그 동작을 이 ADR 이 정한다), [ADR-0059](0059-evaluation-job-limits-concurrency-and-retries-with-db-leases-on-queue-rows.md)(대기 행의 DB 점유), [ADR-0060](0060-article-deletion-announced-by-removed-article-ids-and-followed-by-referencing-modules.md)(지운 기사 따르기), [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)(사건 묶기)

## 맥락

- 사건 묶기의 첫 단계는 새 기사의 표현(임베딩 · 핵심 말)을 만드는 것이다([ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)). 임베딩은 외부 의존(포트 `ArticleEmbedder`)이고, 운영 구현은 표현을 저장하는 변경보다 뒤에 들어온다. 그 사이 `local` 이 아닌 구성(배포 정의 · 배포 스모크)에는 임베더가 없다 — 로컬 대체물은 `local` 에서만 등록된다([ADR-0057](0057-local-substitutes-registered-only-in-local-profile-without-operator-switch.md)).
- `ArticleStored` 는 수집 작업 안에서 발행되어 작업 역할에서 처리된다. 리스너가 실패하면 발행은 실패로 남고, 재처리 작업이 다시 낸다 — 한 발행의 처리 시도는 6회까지이고 넘으면 운영자 처리 화면에 남는다.
- 리스너는 짧게 처리하고 무거운 일은 작업으로 보낸다([ADR-0042](0042-app-roles-as-web-worker-profiles-marked-by-platform-annotations.md)). 임베딩은 모델 추론이고, 여러 텍스트를 한 번에 넘기면 싸진다.
- 다음 단계인 사건 배정은 애매한 구간에서 사건 소속 판정을 부른다. 그 공개 API 는 작업 역할의 작업에서 트랜잭션 밖으로 불러야 한다([ADR-0073](0073-event-membership-api-in-both-roles-looks-up-the-evaluator-on-call-and-fails-without-one.md)) — 배정은 어떤 형태든 작업으로 돈다.
- 계산 도중 그 기사가 지워지면, 늦게 쓴 표현이 지운 기사의 행으로 남을 수 있다. 평가는 대기 행의 점유 토큰 조건부 쓰기와 리스너의 지우기 순서로 이를 막는다([ADR-0059](0059-evaluation-job-limits-concurrency-and-retries-with-db-leases-on-queue-rows.md) · [ADR-0060](0060-article-deletion-announced-by-removed-article-ids-and-followed-by-referencing-modules.md)).
- 평가 작업이 운영 평가기보다 먼저 들어왔을 때, 작업은 평가기가 없으면 경고하고 건너뛰어 기사를 대기에 쌓았다. 그 뒤 운영 평가기가 들어오며 평가기 없는 작업 역할의 기동을 막았다([ADR-0061](0061-worker-role-does-not-run-without-an-evaluator.md)).

## 결정

- **리스너는 `event` 의 대기 테이블에 기사 ID 만 넣는다.** 기사마다 한 행이고, 이미 있으면 그대로다.
- **작업 역할의 반복 작업이 대기를 묶음으로 점유해 표현을 만든다.** 점유를 커밋한 뒤 트랜잭션 밖에서 기사를 읽고 임베더를 한 번 부르며, 기사마다 짧은 트랜잭션에서 점유 토큰이 맞아 대기 행을 지웠을 때만 표현을 쓴다. 임베더가 실패하거나 결과가 어긋나면 점유를 풀고 회차를 끝낸다.
- **임베더는 부를 때 찾는다. 없으면 회차를 건너뛰고 기사는 대기에 남는다.** 작업 역할이 뜰 때 경고를 한 번 남긴다. 리스너와 회차는 임베더를 생성자로 받지 않는다 — 임베더 없는 화면 역할도 뜬다.
- **지운 기사는 리스너가 한 트랜잭션에서 대기 행을 먼저, 표현 행을 나중에 지운다.** 진행 중인 쓰기가 대기 행을 잡고 있으면 그 커밋을 기다린 뒤 표현을 지운다.

## 결과

- 운영 임베더가 없는 동안에도 이벤트 발행이 실패로 쌓이지 않는다. 대신 기사가 대기에 쌓인다 — 그 처분과 운영 임베더가 들어온 뒤의 기동 검사 · 실패 처리는 운영 구현을 만드는 작업이 정한다.
- 대기 테이블 · 점유 · 반복 작업이 `event` 에 생긴다. 사건 배정이 같은 대기를 이어 쓸 수 있다.
- 임베더를 묶음으로 부른다.
- 임베더가 같은 묶음에서 계속 실패하면 회차가 넣은 시각 순으로 점유하므로 그 뒤 기사도 처리되지 않는다. 실패 갈래 · 재시도 상한 · 건너뜀은 운영 구현을 만드는 작업이 정한다.
- 이 모듈이 생기기 전에 저장된 기사는 대기에 들어가지 않아 표현이 없다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `ArticleStored` 리스너가 그 자리에서 표현을 계산해 저장한다 | 운영 임베더가 없는 구성에서 기사마다 발행이 실패해 시도 상한 뒤 운영자 처리 화면에 쌓인다. 모델 추론은 짧은 처리가 아니고, 기사 하나씩이라 묶음 추론을 못 쓴다. 배정은 어차피 작업으로 돌아 표현과 배정의 계기가 갈린다 |
| 리스너가 기사마다 JobRunr 작업을 등록한다 | 임베더가 없으면 작업이 기사마다 실패해 JobRunr 실패 작업으로 쌓인다. 묶음 추론을 못 쓰고, 지운 기사와 늦은 쓰기의 엇갈림을 막을 장치가 따로 필요하다 |
| 임베더가 없으면 작업 역할이 뜨지 않게 한다 | 운영 임베더가 들어오기 전까지 배포 구성이 뜨지 않아 배포 스모크가 깨진다([ADR-0061](0061-worker-role-does-not-run-without-an-evaluator.md)의 같은 대안과 같다) |
