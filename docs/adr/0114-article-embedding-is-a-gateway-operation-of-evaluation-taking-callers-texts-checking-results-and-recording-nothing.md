# ADR-0114: 기사 임베딩은 `evaluation` 관문의 연산으로 두고, 텍스트는 부르는 쪽이 넘기며, 결과는 관문이 확인하고 기록하지 않는다

- 상태: 승인
- 날짜: 2026-11-11
- 근거: [이슈 #131](https://github.com/Theia0382/yozm-news/issues/131) — 임베더를 `event` 에서 `evaluation` 으로 옮기며, 입력 텍스트를 누가 만들고 결과를 누가 확인하고 기록하는지를 리드와 정함
- 관련: [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)(결정 절 "임베딩은 `event` 의 `ArticleEmbedder` 포트 뒤에 둔다"의 위치를 부분 대체한다), [ADR-0112](0112-tags-split-into-a-tag-module-and-evaluation-is-the-model-gateway-that-assembles-calls-and-records-each-judgement.md)(관문의 연산을 판정 원본이 없는 임베딩으로 넓힌다 — 보충), [ADR-0090](0090-article-embedding-defaults-to-an-in-app-local-model-with-an-openai-compatible-api-by-setting.md) · [ADR-0091](0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md)(운영 구현과 설정 · 모델 · 배포는 그대로 두고 모듈 위치만 옮긴다 — 보충), [ADR-0092](0092-embedder-failures-in-three-kinds-with-rejected-articles-isolated-and-dropped-after-a-limit.md)(실패 세 갈래와 갈래별 회차 동작은 그대로), [ADR-0075](0075-article-vectors-in-a-dimensionless-vector-column-one-row-per-model-version.md) · [ADR-0079](0079-event-centroids-in-a-dimensionless-halfvec-column-with-a-model-version-per-event.md)(벡터와 모델 버전의 저장은 `event` 에 남는다), [ADR-0115](0115-embedding-api-in-both-roles-looks-up-the-embedder-on-call-and-a-worker-startup-check-requires-one.md)(임베더 유무를 보장하는 장치)

## 맥락

- 기사 임베딩(포트 `ArticleEmbedder` — 로컬 모델 · OpenAI 호환 외부 API · 로컬 대체물)은 `event` 안에 있었다. 임베딩은 Jev 판정처럼 기사를 운영자 비용의 모델(앱 안 로컬 모델 또는 외부 API)로 다루는 일이고, 교체 가능성 · 로컬 대체물 · 모델이 없을 때의 동작을 판정과 같이 다룬다.
- `evaluation` 은 판정마다 공개 API 하나로 모델 요청을 조립해 부르고, 결과를 대조하고, 판정 원본을 기록하는 관문이다(ADR-0112). 판정의 입력은 관문이 기사 ID 로 읽고, 질문 문장은 그 지식을 가진 쪽이 낸다.
- `event` 는 기사의 제목과 요약문 앞부분으로 표현 텍스트 하나를 만들어 임베딩과 핵심 말(Nori)에 함께 쓴다. 표현 텍스트 규칙의 변경은 모델 버전에 넣지 않는다 — 텍스트 규칙은 묶기의 지식이고 모델 버전은 임베더의 것이다.
- 임베딩 결과(벡터)는 `event` 가 기사 표현 행과 사건 중심에 모델 버전과 함께 저장하고, 모델 버전이 같은 것끼리만 비교한다. 같은 텍스트 · 같은 모델이면 벡터를 다시 만들 수 있다.
- 결과의 수 · 차원은 부르는 쪽(`event` 의 표현 회차)이 확인했다. 관문의 판정은 결과와 질문의 어긋남을 관문이 대조해 예외로 낸다.

## 결정

- **임베더는 `evaluation` 에 둔다.** 포트 `ArticleEmbedder` 와 운영 구현(로컬 모델 · OpenAI 호환) · 로컬 대체물, provider 설정과 결선은 `evaluation` 이 갖는다. 다른 모듈은 공개 API 하나(임베더 정보 · 임베딩)로만 임베딩을 얻는다 — 판정 공개 API 와 포트를 나눈 꼴과 같다.
- **입력은 부르는 쪽이 만든 텍스트 목록이다.** 관문은 기사를 읽지 않는다. 표현 텍스트 · 핵심 말이 같은 텍스트를 쓰는 지식은 `event` 에 남는다.
- **결과를 기록하지 않는다.** 벡터와 그 모델 버전은 부르는 쪽의 데이터다. 판정 원본은 다시 읽기 · 운영자 조회 · 판정의 쓰임 추적에 쓰이지만, 임베딩에는 그런 소비자가 없다.
- **결과의 수 · 차원은 관문이 확인한다.** 공개 API 의 성공은 넘긴 텍스트 수만큼, 임베더가 밝힌 차원의 유한한 벡터다. 어긋나면 관문의 예외다. 예상한 실패(일시 · 입력 거부 · 사용 불가)는 그대로 돌려주고, 갈래마다 무엇을 할지는 부르는 쪽이 정한다.
- **임베딩은 DB 트랜잭션 밖에서 부른다.** 트랜잭션 안에서 부르면 예외다.
- **벡터 값 타입 · 실패 갈래 · 차원 상한은 관문의 공개 계약이다.** 부르는 쪽은 같은 벡터 값 타입을 저장 데이터에 그대로 쓴다. 차원 상한(4,000)은 부르는 쪽의 장기 검색 인덱스가 받는 차원에서 왔고, 관문이 내는 벡터의 상한으로 둔다.
- 경계의 정본은 [overview.md §5](../overview.md#5-모듈-지도) · [evaluation Spec](../specs/evaluation.md) · [event Spec](../specs/event.md)이다.

## 결과

- 임베딩을 쓰는 다음 소비자(스토리라인 잇기 등)도 같은 공개 API · 같은 계약(트랜잭션 밖, 확인된 결과)으로 쓴다. 확인을 소비자마다 되풀이하지 않는다.
- 결과 어긋남은 표현 회차에 예외로 온다. 회차는 예상 못한 예외와 같은 갈래로 점유를 풀고 멈추며, 오류 로그의 사유 칸은 어긋남 문구가 아니라 예외 클래스 이름이다 — 어긋남 문구는 그 뒤 스택의 예외 문구에 있다.
- 관문의 공개 API 가 판정 원본을 남기는 판정과 남기지 않는 임베딩으로 나뉜다. 판정 원본이 필요해지는 임베딩 소비자가 생기면 그때 기록을 더한다.
- 설정 키 · 환경 변수 · 모델 파일 경로 · 빌드 · 이미지 · 테이블은 바뀌지 않는다. 저장된 기사 표현 · 사건 중심 벡터를 그대로 읽는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 판정처럼 부르는 쪽이 기사 ID 를 넘기고 관문이 기사를 읽어 텍스트를 만든다 | 표현 텍스트 규칙이 관문과 `event`(핵심 말)에 두 벌 생긴다. 텍스트 규칙은 모델 버전에 넣지 않는 묶기의 지식이다 |
| 판정 원본처럼 임베딩 결과를 관문이 기록한다 | 벡터는 같은 텍스트 · 같은 모델로 다시 만들 수 있고, 다시 읽기 · 운영자 조회 · 쓰임 추적 같은 소비자가 없다. 저장이 두 모듈에 겹친다 |
| 결과의 수 · 차원을 지금처럼 부르는 쪽이 확인한다 | 공개 API 의 결과가 확인되지 않은 값이 되고, 소비자마다 같은 확인을 되풀이한다. 판정의 관문 대조 꼴과 갈린다 |
| 결과 어긋남을 사용 불가 실패로 돌려준다 | 회차 로그가 오류에서 경고로 바뀌고, 설정 오류(차원)가 임베더를 쓸 수 없는 경우와 같은 갈래에 섞인다 |
| 포트를 그대로 공개 API 로 낸다 | 포트와 공개 API 가 한 타입이라 관문이 결과 확인 · 트랜잭션 확인을 끼울 자리가 없고, 구현이 공개 타입을 직접 구현한다 |
| `event` 에 벡터 값 타입을 따로 두고 관문 결과를 옮겨 담는다 | 같은 모양의 값 타입 두 벌과 옮겨 담는 코드가 생긴다 |
