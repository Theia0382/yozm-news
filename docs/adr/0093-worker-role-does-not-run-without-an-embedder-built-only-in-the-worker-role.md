# ADR-0093: 작업 역할은 임베더 없이 운영하지 않는다 — 운영 임베더는 작업 역할에서만 만들고, 반복 작업이 생성자로 받는다

- 상태: 승인. 부분 대체 — 반복 작업이 임베더를 생성자로 받는 장치([ADR-0115](0115-embedding-api-in-both-roles-looks-up-the-embedder-on-call-and-a-worker-startup-check-requires-one.md)) · 회차가 임베더를 생성자로 받지 않음([ADR-0115](0115-embedding-api-in-both-roles-looks-up-the-embedder-on-call-and-a-worker-startup-check-requires-one.md))
- 날짜: 2026-11-07
- 근거: [이슈 #95](https://github.com/Theia0382/yozm-news/issues/95) — 운영 임베더를 들이며, 임베더가 없는 동안의 건너뜀 · 대기 적체와 그 처분을 운영 구현에 미뤄 둔 결정을 리드와 정함
- 관련: [ADR-0074](0074-article-features-built-by-event-intake-queue-job-and-skipped-without-an-embedder.md)(결정 절 셋째 항목의 첫 세 문장을 부분 대체한다 — 넷째 문장 "리스너와 회차는 임베더를 생성자로 받지 않는다 — 임베더 없는 화면 역할도 뜬다" 는 그대로, 결과 절 첫 항목의 미룬 사항을 정한다 — 후속 기록), [ADR-0084](0084-event-merge-applies-assignment-scoring-between-events-and-keeps-merged-rows-pointing-to-survivors.md)(결과 절 "임베더가 없는 프로세스에서는 다른 회차와 함께 건너뛴다"를 부분 대체한다), [ADR-0061](0061-worker-role-does-not-run-without-an-evaluator.md)(평가기의 같은 원칙), [ADR-0090](0090-article-embedding-defaults-to-an-in-app-local-model-with-an-openai-compatible-api-by-setting.md)(운영 임베더)

## 맥락

- 운영 임베더가 없을 때 사건 묶기 반복 작업은 임베더를 부를 때 찾아 없으면 건너뛰고, 기사는 대기에 쌓였다. 운영 임베더가 들어온 뒤의 기동 검사와 쌓인 기사의 처분은 운영 구현을 만드는 작업이 정한다고 미뤘다([ADR-0074](0074-article-features-built-by-event-intake-queue-job-and-skipped-without-an-embedder.md)).
- 사건 묶기는 다이제스트의 큰 사건 슬롯 · 사건 단위 중복 제거의 입력이다. 평가기에서는 핵심 입력 없이 작업 역할을 운영하지 않기로 했다([ADR-0061](0061-worker-role-does-not-run-without-an-evaluator.md)).
- 기본 운영 임베더는 설정 없이 만들어지는 앱 안 로컬 모델이다([ADR-0090](0090-article-embedding-defaults-to-an-in-app-local-model-with-an-openai-compatible-api-by-setting.md)). 그래서 운영 구성에서 임베더가 없을 수 있는 경우는 설정 오류 · 모델을 열지 못함뿐이다.
- 로컬 모델은 프로세스 메모리를 많이 쓴다. 화면 역할만 띄운 프로세스에는 필요 없다.

## 결정

> 부분 대체(둘째 문장) — [ADR-0115](0115-embedding-api-in-both-roles-looks-up-the-embedder-on-call-and-a-worker-startup-check-requires-one.md). 지금 상태: [evaluation Spec §5](../specs/evaluation.md#기사-임베딩) evaluation-R46 · [event Spec §5](../specs/event.md#사건-묶기-대기와-회차) event-R7
- **작업 역할은 임베더 없이 운영하지 않는다.** 사건 묶기 반복 작업이 임베더를 생성자로 받는다. 임베더가 없거나 만들지 못하면 작업 역할이 뜨지 않는다 — 설정 · 모델 오류는 기동 때 문구와 함께 드러난다.
> 부분 대체(셋째 문장) — [ADR-0115](0115-embedding-api-in-both-roles-looks-up-the-embedder-on-call-and-a-worker-startup-check-requires-one.md). 지금 상태: [event Spec §5](../specs/event.md#사건-묶기-대기와-회차) event-R7
- **운영 임베더(로컬 · 외부)는 작업 역할에서만 만든다.** 화면 역할만 띄운 프로세스는 임베더를 요구하지 않고 모델도 올리지 않는다. 리스너 · 회차는 여전히 임베더를 생성자로 받지 않는다.
- 로컬 대체물은 `local` 에서, 임베딩 방식이 기본(`onnx`)일 때만 등록된다 — 외부 방식을 고르면 `local` 에서도 외부 구현 하나만 쓴다.

## 결과

- 임베더 없이 대기만 쌓이는 운영이 없어진다. 경고 한 줄로 넘기던 설정 · 모델 오류가 작업 역할의 기동 실패가 된다 — 두 역할을 한 프로세스에서 하면 화면도 함께 뜨지 않는다.
- 임베더가 없던 동안 쌓인 기사는 따로 처분하지 않는다 — 임베더가 생기면 회차가 넣은 순서로 처리한다. 배정 회차는 발행 시각 기준이라 늦게 처리돼도 판정 규칙은 같다.
- `local` 이 아닌 시험 컨텍스트도 작업 역할이라 시험 설정이 임베더를 정해 준다(닿지 않는 주소의 외부 방식).

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 지금 그대로 — 임베더가 없으면 건너뛰고 경고, 기사는 대기에 쌓인다 | 핵심 입력 없이 조용히 쌓이는 운영이 남는다. 기본 임베더가 설정 없이 있으므로 없을 일이 설정 · 모델 오류뿐인데, 그 오류를 경고 한 줄로 넘기게 된다 |
| 운영 임베더를 두 역할 모두에서 만든다 | 화면 역할만 띄운 프로세스가 쓰지 않는 모델을 메모리에 올린다 |
