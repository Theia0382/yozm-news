# 운영 설정

배포 구성이 받는 설정 값이다. 값은 `deploy/.env` 에 적는다 — 받을 수 있는 변수 전부는 [deploy/.env.example](../../deploy/.env.example)에 있고, 각 변수가 바꾸는 것은 이 문서가 정한다. 이 문서에 없는 앱 설정은 배포 구성이 앱에 넘기지 않으므로 `deploy/.env` 에 적어도 효과가 없다.

변수는 그 값이 바꾸는 것의 자리별로 묶는다. 값이 바꾸는 규칙이 모듈에 속하면 그 규칙의 정의는 모듈 Spec 에 있다.

## DB 접속

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `POSTGRES_PASSWORD` | 예 | `postgres` 의 DB 계정 비밀번호이자 `app` 의 접속 비밀번호 | 길고 무작위인 문자열 | DB 비밀번호 — [비밀 값](#비밀-값) |

- 앱은 DB 접속 정보를 Spring 표준 환경 변수 `SPRING_DATASOURCE_URL` · `SPRING_DATASOURCE_USERNAME` · `SPRING_DATASOURCE_PASSWORD` 로 받는다. `SPRING_DATASOURCE_URL` 이 없으면 앱이 뜨지 않는다([ADR-0017](../adr/0017-standard-datasource-env-vars.md))
- 배포 구성은 이 셋을 `postgres` 서비스에 맞춰 Compose 정의 안에서 만들어 넘긴다. DB 이름 · 계정도 정의에 고정돼 있다. 그래서 `deploy/.env` 로는 바꿀 수 없다 — 외부 DB 는 [서비스](deployment.md#서비스)

## 피드 수집

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `YOZMNEWS_COLLECTION_CONTACT` | 예 | `app` → `yozmnews.collection.contact` | `ops@example.org` · `https://example.org/contact` | 수집 요청의 `User-Agent` 에 밝히는 운영자 연락처 — [source Spec §7](../specs/source.md#7-설정) · source-R29 |
| `YOZMNEWS_COLLECTION_ALLOWED_NETWORKS` | 아니오 | `app` → `yozmnews.collection.allowed-networks` | `192.168.10.0/24,10.0.5.7/32` | 막힌 내부 대역 가운데 수집이 연결해도 되는 대역 — [source Spec §7](../specs/source.md#7-설정) · source-R34 |

- 연락처는 모든 피드 서버에 나가는 값이라 비밀 값을 넣지 않는다. 피드 운영자(언론사)가 요청 주체에 닿을 수 있는 주소를 적는다
- 내부 대역은 같은 망에 둔 RSS 생성기 같은 피드를 받을 때만 연다. 대역은 쉼표로 이어 적고, 빈 항목은 건너뛴다. 판정은 실제로 연결하는 주소로 하므로, 피드 URL 의 `localhost` 는 IPv6 루프백(`::1`)으로 풀려 `127.0.0.1/32` 밖이 될 수 있다 — 주소로 적거나 두 대역을 함께 연다. 로컬 NAT64 접두(`64:ff9b:1::/48`)로 DNS64 를 돌리는 IPv6 전용 망은 IPv4 피드가 그 접두 안의 주소로 풀려 막힌다 — 그 접두를 열면 받지만, 연 대역에서는 사설 IPv4 로 가는 요청도 함께 열린다
- 수집은 시스템 프록시 설정을 읽지 않는다(source-R36). 바깥 연결이 프록시를 거쳐야만 하는 망에서는 피드를 받지 못한다

## 공유 평가

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `YOZMNEWS_JEV_API_KEY` | 예 | `app` → `yozmnews.jev.api-key` | TypeSafe 콘솔에서 만든 키 | 평가 모델 Jev 의 API 키 — [비밀 값](#비밀-값) · [evaluation Spec §7](../specs/evaluation.md#7-설정) |
| `YOZMNEWS_JEV_MODEL` | 아니오 | `app` → `yozmnews.jev.model` | `jev-1.14.0` | 평가 모델 — 기본 `jev-latest` |
| `YOZMNEWS_JEV_BASE_URL` | 아니오 | `app` → `yozmnews.jev.base-url` | `https://openrouter.ai/api` | Jev 를 부르는 기본 주소 — 기본 `https://api.typesafe.ai` |

- 앱은 수집한 기사마다 Jev 에 요청 하나를 보내 주제 · 지역 · 형식 · 중요도를 묻는다. 비용은 이 키의 계정에 청구되고 사용자 수와 무관하다 — 기본 태그 체계에서 기사당 입력 평균 약 3,500토큰이다([evaluation Spec](../specs/evaluation.md#articleevaluator))
- 사건 묶기는 가까운 사건과의 점수가 애매한 구간에 든 기사만 Jev 에 사건 소속 판정 요청 하나를 더 보낸다 — 실측 표본에서 요청당 입력 약 540 ~ 700토큰이다([evaluation Spec](../specs/evaluation.md#articleevaluator)). 기사 가운데 몇 건이 애매한 구간에 드는지는 아직 재지 않았다. 가까운 사건끼리 합칠 때도 점수가 애매한 구간에 든 사건 쌍마다 한 번 묻고, 같은 쌍은 다시 묻지 않는다. 기준을 바꿔 지난 배정을 다시 판정할 때는 Jev 를 부르지 않는다([사건 묶기](#사건-묶기))
- 키가 없으면 앱이 뜨지 않는다 — 배포 구성은 줄이 없거나 값이 비면 Compose 가 변수 이름을 말하며 멈춘다. 공백 · 제어 문자 · ASCII 밖의 문자가 있거나 512자를 넘는 키도 앱이 뜨지 않는다. 키가 맞는지는 평가 때 드러난다 — 틀린 키면 평가가 멈추고 앱 로그에 경고 `평가기를 쓸 수 없어 회차를 멈췄다` 가 남는다. 기사는 평가 대기에 남아, 키를 고쳐 앱을 다시 띄우면 그대로 평가된다. 사건 묶기도 애매한 구간의 기사에서 멈추고 경고 `평가기를 쓸 수 없어 배정 회차를 멈췄다` 를 남긴다 — 기사는 배정 대기에 남는다. 사건 병합도 같은 까닭으로 멈추면 경고 `평가기를 쓸 수 없어 병합 회차를 멈췄다` 를 남기고, 키를 고친 뒤 다시 본다
- 모델 기본값 `jev-latest` 는 TypeSafe 가 새 버전을 올리면 따라간다. 평가마다 실제로 답한 버전이 저장된다. 태그 기준 · 저장 기준을 맞춘 뒤에는 버전 ID 로 고정한다. 고정한 버전이 내려가면 평가가 멈추고 같은 경고가 남는다 — 새 버전으로 바꾼다
- OpenRouter 로 부르려면 기본 주소를 `https://openrouter.ai/api`, 모델을 `typesafe/` 이름(예: `typesafe/jev-1.13`, 최신 별칭은 `~typesafe/jev-latest`), 키를 OpenRouter 키로 바꾼다 — 같은 요청 · 응답 스키마의 호환 경로다. 실제로 확인한 경로는 TypeSafe 직접이다
- 요청 제한 · 일시적인 서버 오류는 기사마다 1 · 2 · 4 · 8분 뒤 다시 시도하고, 다섯 번째에 실패로 남긴다. 실패로 남은 기사는 저절로 다시 평가하지 않는다 — 태그 체계를 활성화할 때 정한 [재평가 기간](tag-sets.md#재평가-기간)에 든 기사만 다시 평가한다. 잔액이 떨어졌을 때 TypeSafe 가 주는 응답은 문서에 없다 — 요청 제한으로 온다면 그동안 들어온 기사가 이렇게 실패로 남을 수 있다
- 모델 · 기본 주소의 값을 비운 줄(`YOZMNEWS_JEV_MODEL=`)은 빈 값이 그대로 넘어가 앱이 뜨지 않는다. 쓰지 않을 때는 줄을 지우거나 주석으로 둔다

## 사건 묶기

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `YOZMNEWS_EVENT_ASSIGNMENT_WINDOW` | 아니오 | `app` → `yozmnews.event-assignment.window` | `48h` | 시간 창 — 기사의 발행 시각과 이만큼 떨어진 사건까지 후보로 본다. 기본 72시간 |
| `YOZMNEWS_EVENT_ASSIGNMENT_UPPER` | 아니오 | `app` → `yozmnews.event-assignment.upper` | `0.85` | 위 기준 — 점수가 이 이상이면 Jev 없이 붙인다. 기본 0.80 |
| `YOZMNEWS_EVENT_ASSIGNMENT_LOWER` | 아니오 | `app` → `yozmnews.event-assignment.lower` | `0.50` | 아래 기준 — 이 이상 · 위 기준 미만이면 Jev 에 묻는다. 기본 0.55 |
| `YOZMNEWS_EVENT_ASSIGNMENT_STORE` | 아니오 | `app` → `yozmnews.event-assignment.store` | `0.35` | 저장 기준 — 이 이상인 후보를 점수 · 판정 확률과 함께 남겨 기준을 바꿀 때의 재판정에 쓴다. 기본 0.40 |
| `YOZMNEWS_EVENT_ASSIGNMENT_PROBABILITY` | 아니오 | `app` → `yozmnews.event-assignment.probability` | `0.60` | 확률 기준 — Jev 가 답한 확률이 이 이상이어야 붙인다. 기본 0.50 |
| `YOZMNEWS_EVENT_ASSIGNMENT_CLOSE_AFTER` | 아니오 | `app` → `yozmnews.event-assignment.close-after` | `10d` | 닫는 기간 — 사건에 마지막으로 기사가 붙은 뒤 이만큼 새 기사가 없으면 닫는다. 기본 7일 |
| `YOZMNEWS_EVENT_ASSIGNMENT_MAX_OPEN` | 아니오 | `app` → `yozmnews.event-assignment.max-open` | `21d` | 최대 기간 — 사건을 연 뒤 이만큼 지나면 새 기사가 있어도 닫는다. 기본 14일 |

- 기본값은 측정 전 목표다. 임베딩 모델이 바뀌면 점수 분포가 달라지므로 기준도 함께 본다. 값을 정할 근거(사건별 점수 · 판정 방식, 판정 기록의 확률)는 운영자 화면에서 본다 — [사건 화면 읽기](events.md#사건-배정-기준). 값의 뜻과 규칙은 [event Spec §5](../specs/event.md#사건-배정)에 있고, 닫는 기간 · 최대 기간은 [사건 정리](../specs/event.md#사건-정리)에 있다
- 0 ≤ 저장 기준 ≤ 아래 기준 ≤ 위 기준 ≤ 1, 0 < 확률 기준 ≤ 1, 시간 창은 초 단위여야 한다. 닫는 기간 · 최대 기간도 초 단위이고 시간 창 ≤ 닫는 기간 ≤ 최대 기간이어야 한다. 어기면 앱이 뜨지 않는다. 값을 비운 줄은 앱의 기본값이 되지 않으므로 쓰지 않을 때는 주석으로 둔다
- 값을 바꿔 앱을 다시 띄우면, 앱이 지난 배정을 저장해 둔 점수 · 확률로 1분마다 200건씩 다시 판정한다 — Jev 를 부르지 않는다. 닫힌 사건의 배정은 다시 판정하지 않고 기준만 새것으로 적는다. 저장 기준보다 낮은 점수의 후보는 남아 있지 않아, 아래 기준을 판정 때의 저장 기준 아래로 내린 효과는 지난 배정에 보이지 않는다
- 사건은 하루 1회 정리 작업이 닫는다. 닫는 기간 · 최대 기간을 바꾸면 그 뒤의 닫기에만 쓰고, 이미 닫힌 사건은 다시 열지 않는다. 같은 작업이 닫힌 사건 기사의 기사 표현과, 만든 지 시간 창에 여유를 더한 기간이 지났는데 열린 사건에 배정되지 않은 기사 표현을 지운다
- 정리 작업은 닫힌 사건을 찾는 장기 검색 인덱스를 모델 버전마다 하나 만든다. 만들려면 앱의 DB 계정이 `events` 테이블의 소유자여야 한다 — 배포 구성의 DB 계정은 마이그레이션으로 테이블을 만든 계정이라 따로 할 일이 없다. 외부 DB 에서 앱 계정이 소유자가 아니면 인덱스를 만들지 못하고 앱 로그에 경고 `장기 검색 인덱스를 만들지 못했다` 를 남긴다 — 사건 묶기의 결과는 같고 장기 검색만 느리다. 앱은 다음 날 다시 만들어 본다
- 사건 묶기를 켜기 전에 저장된 기사는 사건이 없다. 운영자 API `POST /api/admin/event-intake-fills`(본문 `{"days": 일수}`, 1 ~ 30일)로 그 기간에 처음 본 기사 가운데 사건이 없는 기사를 사건 묶기에 넣는다 — 화면은 아직 없다

### 임베딩

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `YOZMNEWS_EMBEDDING_PROVIDER` | 아니오 | `app` → `yozmnews.embedding.provider` | `openai-compatible` | 기사 임베딩 방식 — `onnx`(기본, 이미지 안의 로컬 모델) · `openai-compatible`(외부 API) |
| `YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_BASE_URL` | 외부 방식에서 예 | `app` → `yozmnews.embedding.openai-compatible.base-url` | `https://api.upstage.ai/v1` · `https://openrouter.ai/api/v1` | 외부 API 주소 — 앱이 뒤에 `/embeddings` 를 붙인다 |
| `YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_MODEL` | 외부 방식에서 예 | `app` → `yozmnews.embedding.openai-compatible.model` | `solar-embedding-2-query` · `qwen/qwen3-embedding-4b` | 외부 모델 이름 |
| `YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_API_KEY` | 아니오 | `app` → `yozmnews.embedding.openai-compatible.api-key` | 제공자 콘솔에서 만든 키 | 외부 API 키 — [비밀 값](#비밀-값). 비면 인증 헤더를 보내지 않는다(키 없이 받는 직접 띄운 서버) |
| `YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_DIMENSIONS` | 외부 방식에서 예 | `app` → `yozmnews.embedding.openai-compatible.dimensions` | `1024` | 모델이 내는 벡터 차원 — 1 ~ 4,000 |
| `YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_SEND_DIMENSIONS` | 아니오 | `app` → `yozmnews.embedding.openai-compatible.send-dimensions` | `true` | 요청에 차원(`dimensions`)을 담는다 — 기본 `false`. 차원을 줄여 받는 모델에서만 켠다 |
| `YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_INPUT_PREFIX` | 아니오 | `app` → `yozmnews.embedding.openai-compatible.input-prefix` | `query: ` | 텍스트마다 앞에 붙일 문자열 — 기본 없음 |
| `YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_BATCH_SIZE` | 아니오 | `app` → `yozmnews.embedding.openai-compatible.batch-size` | `16` | 한 요청에 담는 기사 수 — 1 ~ 256, 기본 32 |

- 기본(`onnx`)은 이미지에 든 `multilingual-e5-small` int8 모델로 앱 안에서 임베딩한다 — 실행 중 외부로 나가는 요청이 없고 추가 비용이 없다. 앱 메모리가 약 0.3 ~ 0.4 GiB 는다([앱 메모리와 이미지 크기](deployment.md#앱-메모리와-이미지-크기))([ADR-0090](../adr/0090-article-embedding-defaults-to-an-in-app-local-model-with-an-openai-compatible-api-by-setting.md) · [ADR-0091](../adr/0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md))
- **외부 방식은 기사 제목과 요약문 앞부분을 그 API 제공자에게 보낸다.** 비용은 그 키의 계정에 청구된다. 제공자의 데이터 이용 조건(입력을 학습에 쓰는지 등)은 운영자가 확인한다. 공유 평가도 같은 기사 제목 · 요약문을 Jev 에 보낸다
- 외부 방식은 OpenAI 의 `/embeddings` 와 같은 요청 · 응답을 받는 API 면 쓸 수 있다 — 상용 API · OpenRouter 같은 중계 · 직접 띄운 임베딩 서버. 모델에 따라 품질이 크게 다르다 — 측정한 모델별 품질 · 비용은 [임베딩 모델 조사](../research/2026-11-embedding-models.md)에 있다. 응답 벡터는 앱이 정규화하므로 정규화하지 않는 API 도 된다
- 차원은 모델의 문서에서 확인해 적는다. 응답 벡터의 차원이 이 값과 다르면 앱 로그에 `임베더가 실패해 회차를 멈췄다` 가 남고 처리하지 않는다. 차원 전송은 차원을 줄여 받을 수 있는 모델(OpenAI text-embedding-3 등)에서만 켠다 — 받지 않는 API 는 요청을 거부한다
- 입력 앞 문자열은 모델이 요구할 때만 적는다(예: e5 계열은 `query: `). 끝 공백도 값에 든다 — `deploy/.env` 에서 따옴표로 감싸 적는다
- 설정이 틀리면 앱이 뜨지 않는다 — 방식 이름이 다르거나, 외부 방식에서 주소 · 모델 · 차원이 없거나 범위를 벗어나거나, 키에 공백 · 제어 문자 · ASCII 밖의 문자가 있거나 512자를 넘으면 기동 로그에 설정 이름과 환경 변수 이름이 나온다. 키가 맞는지는 임베딩 때 드러난다
- 실행 중 실패는 갈래마다 다르게 처리한다([ADR-0092](../adr/0092-embedder-failures-in-three-kinds-with-rejected-articles-isolated-and-dropped-after-a-limit.md))
  - 연결 실패 · 시간 초과 · 요청 제한 · 서버 오류 — 경고 `임베더가 일시 실패해 회차를 멈췄다`. 기사는 대기에 남아 1분 뒤 다시 시도한다
  - 틀린 키 · 권한 · 없는 모델 · 읽을 수 없는 응답 — 경고 `임베더를 쓸 수 없어 회차를 멈췄다`. 설정을 고쳐 앱을 다시 띄우면 대기의 기사를 그대로 처리한다
  - 입력을 거부한 기사(API 의 400 · 413 · 422 응답) — 그 기사만 가려 거부 수를 세고, 세 번째 거부에 경고 `임베더가 기사를 거부해 사건 묶기에서 뺐다` 를 남기고 사건 묶기에서 뺀다. 뺀 기사는 [채우기](#사건-묶기)로 다시 넣을 수 있다
  - API 가 설정 때문에 요청을 거부해도(예: 받지 않는 차원 전송) 같은 응답이라 기사마다 거부로 센다 — 정보 로그 `임베더가 기사를 거부했다` 가 여러 기사에 이어지면 설정을 고쳐 앱을 다시 띄우고, 그새 뺀 기사는 채우기로 다시 넣는다
- 외부 방식은 시스템 프록시 설정을 읽지 않고 리다이렉트를 따르지 않는다. 바깥 연결이 프록시를 거쳐야만 하는 망에서는 쓸 수 없다
- **방식 · 모델을 바꾸면 새 기사는 새 사건을 연다.** 사건은 모델 버전마다 따로라, 바꾼 뒤의 기사는 바꾸기 전 사건에 붙지 않는다. 모델마다 코사인 분포가 크게 달라 [사건 기준](#사건-묶기)도 새 모델에 맞춰 다시 본다 — 지금 기본값은 어느 모델에도 맞춰지지 않은 측정 전 목표다
- 로컬 모델은 ONNX Runtime 의 사용 통계 전송을 끄는 환경 변수(`ORT_DISABLE_TELEMETRY=1`)가 없으면 열리지 않는다 — 배포 이미지가 이 값을 갖고 있어 따로 할 일이 없다

## 스토리라인 잇기

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `YOZMNEWS_STORYLINE_LINKING_WINDOW` | 아니오 | `app` → `yozmnews.storyline-linking.window` | `21d` | 시간 창 — 판정할 사건의 첫 · 마지막 기사 시각에서 이만큼 떨어진 스토리라인 · 사건까지 견준다. 기본 14일 |
| `YOZMNEWS_STORYLINE_LINKING_THRESHOLD` | 아니오 | `app` → `yozmnews.storyline-linking.threshold` | `0.70` | 기준 점수 — 이 이상이어야 스토리라인에 붙이거나 짝으로 새 스토리라인을 연다. 기본 0.65 |

- 기본값은 측정 전 목표다. 지금 잇기 규칙은 잇는 방법을 평가 세트로 정하기 전의 임시 규칙이다. 값의 뜻과 규칙은 [storyline Spec §5](../specs/storyline.md#임시-잇기-규칙)에 있다
- 시간 창은 0 보다 큰 초 단위, 기준 점수는 0 보다 크고 1 이하여야 한다. 어기면 앱이 뜨지 않는다. 값을 비운 줄은 앱의 기본값이 되지 않으므로 쓰지 않을 때는 주석으로 둔다
- 값을 바꿔 앱을 다시 띄우면 그 뒤의 판정에만 쓴다 — 지난 소속을 다시 판정하지 않는다. 새 값으로 지난 사건을 다시 보려면 아래 채우기를 쓴다. 채우기는 소속을 더할 수는 있지만 빼지는 않는다
- 스토리라인 잇기를 처음 켜기 전의 사건은 스토리라인에 들지 않는다 — 켠 뒤 바뀌는 사건만 알림으로 들어온다. 운영자 API `POST /api/admin/storyline-linking-fills`(본문 `{"days": 일수}`, 1 ~ 180일)로 그 기간에 마지막 기사가 있는 사건(닫힌 사건 포함)을 잇기 대기에 넣는다 — 화면은 아직 없다. 지금에서 거슬러 세므로, 이을 기간의 처음이 지금에서 며칠 전인지를 일수로 준다
- 판정이 세 번 거듭 실패한 사건은 대기에서 빠지고 앱 로그에 오류 `스토리라인 잇기 판정이 거듭 실패해 대기에서 뺐다` 를 남긴다. 원인을 고친 뒤 채우기로 다시 넣는다
- 같은 사건들로 된 스토리라인 둘은 하나로 합쳐지고, 사건 하나만 남은 스토리라인은 그 사건을 가진 다른 스토리라인에 합쳐지거나 풀린다 — 판정할 때 저절로 하므로 따로 할 일이 없다. 이 기능 전에 남은 것은 채우기로 그 사건들을 다시 판정하면 정리된다([storyline Spec §5](../specs/storyline.md#정돈))

## 스토리라인 상태

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `YOZMNEWS_STORYLINE_STATE_QUIET_AFTER` | 아니오 | `app` → `yozmnews.storyline-state.quiet-after` | `10d` | 잠잠 기간 — 스토리라인의 마지막 기사 뒤 이만큼 새 기사가 없으면 잠잠이다. 기본 7일 |
| `YOZMNEWS_STORYLINE_STATE_ENDED_AFTER` | 아니오 | `app` → `yozmnews.storyline-state.ended-after` | `60d` | 끝남 기간 — 마지막 기사 뒤 이만큼 새 기사가 없으면 끝남이다. 기본 30일 |

- 기본값은 측정 전 목표다. 값의 뜻과 규칙은 [storyline Spec §5](../specs/storyline.md#상태)에 있다
- 두 기간은 0 보다 큰 초 단위이고 잠잠 기간이 끝남 기간보다 짧아야 한다. 어기면 앱이 뜨지 않는다. 값을 비운 줄은 앱의 기본값이 되지 않으므로 쓰지 않을 때는 주석으로 둔다
- 상태는 저장하지 않고 볼 때마다 정하므로, 값을 바꿔 앱을 다시 띄우면 모든 스토리라인에 바로 적용된다. 끝난 스토리라인도 최근 기사가 있는 사건이 붙으면 다시 진행이 된다
- 상태를 보이는 화면 · 조회는 아직 없다

## 백그라운드 작업

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `YOZMNEWS_JOB_WORKERS` | 아니오 | `app` → `jobrunr.background-job-server.worker-count` | `8` | 작업 서버가 동시에 실행하는 백그라운드 작업 수 |

- 작업 서버는 작업 역할(`worker`)을 켠 프로세스마다 하나다 — 배포 구성에서는 `app` 하나다. 피드 수집 · 이벤트 재처리 등 모든 백그라운드 작업이 이 수를 나눠 쓰므로, 이 값이 동시에 도는 피드 수집 수의 상한이기도 하다
- 앱의 역할은 배포 정의(`deploy/compose.yaml`)가 `SPRING_PROFILES_ACTIVE: web,worker` 로 고정한다. `deploy/.env` 로 바꾸는 값이 아니다([platform Spec](../specs/platform.md#역할))
- 줄이 없으면 앱의 기본값(`backend/src/main/resources/application.yaml`)을 쓴다. 작업 서버 자체의 기본값은 호스트 코어 수에 따라 달라지므로 앱이 값을 고정해 둔다
- 값을 비운 줄(`YOZMNEWS_JOB_WORKERS=`)은 빈 값이 그대로 넘어가 앱의 기본값이 아니라 작업 서버의 기본값이 된다. 쓰지 않을 때는 줄을 지우거나 주석으로 둔다

## 운영자

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `YOZMNEWS_ADMIN_TOKEN` | 아니오 | `app` → `yozmnews.admin.token` | `openssl rand -hex 32` 의 출력(64자) | 운영자 화면(`/admin`)과 운영자 API 를 여는 운영자 토큰 — [비밀 값](#비밀-값) · [platform Spec §7](../specs/platform.md#7-설정) |

- 줄이 없거나 값이 비면 운영자 기능이 닫힌 채 앱이 뜬다. 운영자 화면은 닫혔다고 안내한다
- 값은 32 ~ 512자이고 영문 대소문자 · 숫자 · `-` `.` `_` `~` `+` `/` 와 끝의 `=` 만 쓴다. 어기면 앱이 뜨지 않는다. 사람이 지은 문자열이 아니라 위 명령 같은 무작위 값을 쓴다 — 틀린 토큰의 요청을 따로 제한하지 않는다
- 운영자 화면에서 토큰을 한 번 넣으면 그 브라우저 탭에 남고, 탭을 닫거나 "잠금"을 누르면 지워진다. 토큰을 아는 사람은 모두 운영자다 — 바꾸려면 값을 바꿔 앱을 다시 띄운다
- 꺼진 피드를 켜면 앱이 피드를 한 번 받아 확인하므로 응답이 수십 초 걸릴 수 있다 — [공개 경로와 외부 서비스](deployment.md#공개-경로와-외부-서비스)
- 운영자 토큰은 계정 인증이 생기기 전까지 운영자 기능을 여는 수단이다. 계정 인증 뒤의 첫 운영자 계정은 [계정과 고지](#계정과-고지)에 있다
- 운영자 화면으로 소스를 등록하는 방법은 [소스 등록](sources.md)에 있다

## 공개

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `PROXY_HOST_ADDRESS` | 아니오 | `proxy` 의 호스트 공개 주소 | `0.0.0.0` | proxy 를 공개할 호스트 주소 |
| `PROXY_HOST_PORT` | 아니오 | `proxy` 의 호스트 공개 포트 | `8080` | proxy 를 공개할 호스트 포트 |

- 기본은 이 호스트의 루프백에만 공개한다(기본 주소 · 포트는 `deploy/compose.yaml`). Tunnel 없이 proxy 포트를 직접 공개할 때만 바꾼다
- `app` · `postgres` 는 어떤 설정으로도 호스트에 공개되지 않는다

## 계정과 고지

- **(미구현)** 개인정보처리방침 · 이용약관 주소 — 운영자가 문서를 둘 때만 적는다. 적으면 가입 · 키 등록 화면과 바닥글에 링크가 나오고, 비우면 링크가 없다. 설정 이름은 고지 링크를 만들 때 정한다([security.md](../security.md#개인정보))
- **(미구현)** 공개 가입 — 기본은 닫혀 있다. 켜면 누구나 가입할 수 있고 봇 차단 · 요청 제한이 가입에 걸린다([security.md](../security.md#가입))
- **(미구현)** 첫 운영자 계정 — 이메일 · 초기 비밀번호를 주면 운영자가 없을 때 그 값으로 운영자 계정을 만든다. 주지 않으면 기동 로그에 운영자 초대 링크가 나온다. 초기 비밀번호는 [비밀 값](#비밀-값)이고, 계정이 생긴 뒤에는 지워도 된다(남겨 두면 앱이 경고한다). 규칙은 [security.md](../security.md#가입)
- 공개 가입 · 첫 운영자 계정의 설정 이름은 가입 기능을 만들 때 정한다

## Compose

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `COMPOSE_PROJECT_NAME` | 아니오 | Compose | `yozmnews-second` | Compose 프로젝트 이름 — 컨테이너 · DB 볼륨 이름이 여기서 정해진다 |

- 기본 이름은 `deploy/compose.yaml` 에 고정돼 있다. 한 호스트에서 두 벌을 띄울 때만 바꾼다. 바꾸면 다른 DB 볼륨을 쓰므로 기존 데이터가 보이지 않는다

## 비밀 값

- 지금 배포 구성의 비밀 값은 `POSTGRES_PASSWORD`, [운영자 토큰](#운영자)(`YOZMNEWS_ADMIN_TOKEN`), [Jev API 키](#공유-평가)(`YOZMNEWS_JEV_API_KEY`), 외부 임베딩 방식을 쓸 때의 [임베딩 API 키](#임베딩)(`YOZMNEWS_EMBEDDING_OPENAI_COMPATIBLE_API_KEY`)다
- **(미구현)** OIDC 공급자의 client secret, 봇 차단(Turnstile) secret, Tunnel 토큰, BYOK 키 암호화용 KEK, 첫 운영자 초기 비밀번호
- 비밀 값은 호스트의 환경 파일(`deploy/.env`)에 두고 저장소에 커밋하지 않는다
- **(미구현)** 비밀 값을 Docker secrets 로도 받는다
- **(미구현)** KEK 는 다른 비밀 값과 **별도 파일**로 두고 DB 백업에 넣지 않는다. 버전을 붙여 교체할 수 있게 하고, DB 백업과 다른 위치에 따로 백업한다. KEK 를 잃으면 저장된 사용자 키를 복호화할 수 없다 — 사용자가 키를 다시 등록하면 되므로 치명적이지는 않다. 키 암호화 구조와 KEK 교체 절차는 [security.md](../security.md)

## 데이터 보존

- 보존 기간의 정본은 [data-model.md 보존 정책](../data-model.md#보존-정책-초안)이다
- **(미구현)** RSS 요약문은 기본으로 지우지 않는다. 운영자는 보존 일수를 정해 자동 삭제를 켤 수 있다(기본 꺼짐). 설정 이름은 보존 정리 기능을 만들 때 정한다([ADR-0029](../adr/0029-indefinite-summary-retention-with-optional-deletion.md))
