# ADR-0090: 기사 임베딩의 기본은 앱 안 로컬 모델이고, OpenAI 호환 외부 API 를 설정으로 고르며, 외부 방식의 차원은 운영자가 정한다

- 상태: 승인
- 날짜: 2026-11-07
- 근거: [이슈 #95](https://github.com/Theia0382/yozm-news/issues/95) — 로컬 모델 · OpenAI 호환 외부 API · 직접 띄우는 임베딩 서버(TEI)를 같은 기사 표본(사건 38개 · 기사 139건)으로 실측하고, 외부 상용 모델 17개를 OpenRouter · Upstage 로 잰 뒤 리드와 정함
- 관련: [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)(결정 절 "기본 구현은 로컬 모델(ONNX Runtime)이고, 설정으로 외부 임베딩 API 로 바꿀 수 있다"의 구체 — 보충, 후속 기록), [ADR-0091](0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md)(기본 로컬 모델), [ADR-0089](0089-long-term-hnsw-indexes-on-closed-events-created-per-model-version-at-runtime-by-the-worker-role.md)(차원 상한의 근거), [ADR-0075](0075-article-vectors-in-a-dimensionless-vector-column-one-row-per-model-version.md) · [ADR-0079](0079-event-centroids-in-a-dimensionless-halfvec-column-with-a-model-version-per-event.md)(방식 · 모델을 바꾸면 새 모델 버전)

## 맥락

- 사건 묶기의 임베딩은 포트 `ArticleEmbedder` 뒤에 있고, 기본은 로컬 모델 · 설정으로 외부 API 라고 정했다([ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)). 운영 구현은 없었다.
- 같은 기사 표본(같은 사건 쌍 425 · 다른 사건 쌍 9,166, 같은 주제의 다른 사건 쌍 536)으로 잰 간이 품질:
  - 로컬 후보(multilingual-e5-small · bge-m3 · KURE-v1)의 차이는 표본 오차 안이었고, 셋 모두 로컬 대체물보다 확실히 나았다
  - 외부 상위 모델(Cohere embed-5-pro · Qwen3-Embedding-4B · Gemini embedding-001)은 같은 주제의 다른 사건 대비 AUC 로는 로컬과 거의 같았고, 쌍 판정의 최대 F1 로는 0.957 ~ 0.972 대 0.925 로 표본 오차를 넘었다. 최대 F1 은 표본 안에서 문턱을 고른 값이라 낙관적이다
  - OpenAI text-embedding-3-small 처럼 로컬보다 낮은 외부 모델도 있었다
  - 표본은 언론사 3곳 · 이틀 · 라벨러 하나다. 핵심 말 · 시각 감쇠를 더한 배정 산식 전체의 정확도는 재지 않았다
- 외부 비용은 하루 약 1,200건에서 어느 모델이든 월 약 $1 이하다. 외부 방식은 기사 제목 · 요약문 앞부분을 제공자에게 보낸다.
- OpenAI 호환 `/v1/embeddings` 클라이언트 하나로 상용 · 국내 · 직접 띄우는 서버(Ollama · TEI) 대부분에 닿는다. 다만 제공자마다 다르다
  - `dimensions` 를 받지 않거나 다른 이름으로 받는다
  - 응답 벡터가 정규화되지 않을 수 있다
  - 한 요청의 입력 수 한도가 다르다
  - `encoding_format` 의 허용 값이 다르다
- 직접 띄우는 임베딩 서버(TEI, 같은 모델)는 앱 안 ONNX 보다 가볍지 않았다(메모리 1.46 GiB · 이미지 938 MB). amd64 · arm64 가 별도 태그이고 모델을 실행 중에 내려받는다.
- 장기 검색 HNSW 인덱스([ADR-0089](0089-long-term-hnsw-indexes-on-closed-events-created-per-model-version-at-runtime-by-the-worker-role.md))는 `halfvec` 의 차원이 4,000 을 넘으면 만들어지지 않는다(pgvector 0.8.7 실측 — `halfvec(4000)` 은 만들어지고 `halfvec(4096)` 은 거부됐다).

## 결정

- **기본 임베더는 앱 안에서 도는 로컬 모델이다.** 설정(provider)으로 OpenAI 호환 외부 API 를 고를 수 있다. 두 구현을 함께 둔다.
- **외부 방식은 OpenAI 호환 `POST {주소}/embeddings` 하나다.**
  - 설정은 주소 · 모델 · 키(선택) · 차원 · 차원을 요청에 담을지 · 입력 앞에 붙일 문자열 · 한 요청의 입력 수다
  - `encoding_format` 은 보내지 않는다
  - 응답은 `index` 로 정렬해 수를 확인하고, 클라이언트가 L2 정규화한다
- **외부 방식의 차원은 운영자가 정하는 필수 설정이다.** 요청에 `dimensions` 를 담을지는 따로 켜고 끈다(기본 끔). 결과의 차원이 다르면 회차가 저장하지 않는다.
- **외부 방식의 모델 버전은 모델 · 차원과, 주소 · 입력 앞 문자열 · 차원 요청 여부의 해시로 만든다.** 같은 모델 이름이라도 이 가운데 하나가 다르면 다른 버전이다.
- **임베더의 차원은 4,000 이하다.** 넘으면 임베더 정보가 거부해 설정 오류로 기동하지 않는다.

## 결과

- 기본 구성은 외부 계정 · 비용 · 전송 없이 사건 묶기를 돌린다.
- 외부 방식을 고른 운영자는 측정상 품질이 조금 오를 수 있지만, 이점은 고르는 모델에 달렸다. 기사 제목 · 요약문이 제공자에게 전송된다.
- 두 구현이 같은 포트 · 같은 결과 확인을 거친다. 구현 · 시험이 둘이다.
- 방식이나 모델을 바꾸면 모델 버전이 바뀌어, 새 기사는 새 사건을 연다([ADR-0079](0079-event-centroids-in-a-dimensionless-halfvec-column-with-a-model-version-per-event.md)). 코사인 분포가 모델마다 달라 사건 기준도 다시 맞춰야 한다.
- 기본을 외부로 옮기는 일은 구현을 바꾸지 않는다 — 평가 세트로 외부 우위가 확인되면 기본값 · 배포 정의만 바꾼다.
- 차원이 4,000 을 넘는 외부 모델은 쓸 수 없다. 차원을 줄이는 모델은 차원 요청을 켜서 쓴다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 로컬만 둔다 | arm64 네이티브 문제나 메모리가 작은 호스트에서 대안이 없고, 다른 머신의 임베딩 서버를 쓸 수 없다 |
| 외부만 둔다 — 로컬 모델이 필요한 운영자는 임베딩 서버를 직접 띄운다 | 기본 구성의 사건 묶기가 외부 계정 · 비용 · 전송에 묶인다([ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)이 기각한 것과 같다) |
| 외부 구현 하나에 배포 구성 기본으로 임베딩 서버(TEI) 옆 컨테이너를 둔다 | 앱 안 모델보다 무겁고, 배포 정의가 아키텍처마다 이미지를 골라야 하며, 모델을 실행 중에 내려받는다 |
| 두 구현을 두고 기본은 외부 | 외부 우위의 근거가 간이 표본과 임베딩 단독 지표뿐이고 모델 선택에 달렸다. 기본 구성에 키와 제3자 전송이 는다. 구현이 같으므로 평가 뒤 기본값만 옮길 수 있다 |
| 외부 차원을 선택으로 두고, 없으면 기동 때 한 번 불러 응답 길이로 정한다 | 기동이 외부 API 에 기대 일시 장애 · 키 오류가 작업 역할 기동을 막는다. 제공자가 출력 차원을 바꾸면 다음 기동이 새 차원을 그대로 받아 조용히 새 사건을 연다 |
