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

- 앱은 수집한 기사마다 Jev 에 요청 하나를 보내 주제 · 지역 · 형식 · 중요도를 묻는다. 비용은 이 키의 계정에 청구되고 사용자 수와 무관하다 — 기본 태그 체계에서 기사당 입력 약 3,000 ~ 4,100토큰이다([evaluation Spec](../specs/evaluation.md#articleevaluator))
- 사건 묶기는 가까운 사건과의 점수가 애매한 구간에 든 기사만 Jev 에 사건 소속 판정 요청 하나를 더 보낸다 — 실측 표본에서 요청당 입력 약 540 ~ 700토큰이다([evaluation Spec](../specs/evaluation.md#articleevaluator)). 기사 가운데 몇 건이 애매한 구간에 드는지는 아직 재지 않았다. 기준을 바꿔 지난 배정을 다시 판정할 때는 Jev 를 부르지 않는다([사건 묶기](#사건-묶기))
- 키가 없으면 앱이 뜨지 않는다 — 배포 구성은 줄이 없거나 값이 비면 Compose 가 변수 이름을 말하며 멈춘다. 공백 · 제어 문자 · ASCII 밖의 문자가 있거나 512자를 넘는 키도 앱이 뜨지 않는다. 키가 맞는지는 평가 때 드러난다 — 틀린 키면 평가가 멈추고 앱 로그에 경고 `평가기를 쓸 수 없어 회차를 멈췄다` 가 남는다. 기사는 평가 대기에 남아, 키를 고쳐 앱을 다시 띄우면 그대로 평가된다. 사건 묶기도 애매한 구간의 기사에서 멈추고 경고 `평가기를 쓸 수 없어 배정 회차를 멈췄다` 를 남긴다 — 기사는 배정 대기에 남는다
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

- 기본값은 측정 전 목표다. 임베딩 모델이 바뀌면 점수 분포가 달라지므로 기준도 함께 본다. 값의 뜻과 규칙은 [event Spec §5](../specs/event.md#사건-배정)에 있다
- 0 ≤ 저장 기준 ≤ 아래 기준 ≤ 위 기준 ≤ 1, 0 < 확률 기준 ≤ 1, 시간 창은 초 단위여야 한다. 어기면 앱이 뜨지 않는다. 값을 비운 줄은 앱의 기본값이 되지 않으므로 쓰지 않을 때는 주석으로 둔다
- 값을 바꿔 앱을 다시 띄우면, 앱이 지난 배정을 저장해 둔 점수 · 확률로 1분마다 200건씩 다시 판정한다 — Jev 를 부르지 않는다. 저장 기준보다 낮은 점수의 후보는 남아 있지 않아, 아래 기준을 판정 때의 저장 기준 아래로 내린 효과는 지난 배정에 보이지 않는다
- 사건 묶기를 켜기 전에 저장된 기사는 사건이 없다. 운영자 API `POST /api/admin/event-intake-fills`(본문 `{"days": 일수}`, 1 ~ 30일)로 그 기간에 처음 본 기사 가운데 사건이 없는 기사를 사건 묶기에 넣는다 — 화면은 아직 없다. 임베더가 없는 배포에서는 넣어도 처리되지 않고 쌓인다

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

- 지금 배포 구성의 비밀 값은 `POSTGRES_PASSWORD`, [운영자 토큰](#운영자)(`YOZMNEWS_ADMIN_TOKEN`), [Jev API 키](#공유-평가)(`YOZMNEWS_JEV_API_KEY`) 셋이다
- **(미구현)** OIDC 공급자의 client secret, 봇 차단(Turnstile) secret, Tunnel 토큰, BYOK 키 암호화용 KEK, 첫 운영자 초기 비밀번호
- 비밀 값은 호스트의 환경 파일(`deploy/.env`)에 두고 저장소에 커밋하지 않는다
- **(미구현)** 비밀 값을 Docker secrets 로도 받는다
- **(미구현)** KEK 는 다른 비밀 값과 **별도 파일**로 두고 DB 백업에 넣지 않는다. 버전을 붙여 교체할 수 있게 하고, DB 백업과 다른 위치에 따로 백업한다. KEK 를 잃으면 저장된 사용자 키를 복호화할 수 없다 — 사용자가 키를 다시 등록하면 되므로 치명적이지는 않다. 키 암호화 구조와 KEK 교체 절차는 [security.md](../security.md)

## 데이터 보존

- 보존 기간의 정본은 [data-model.md 보존 정책](../data-model.md#보존-정책-초안)이다
- **(미구현)** RSS 요약문은 기본으로 지우지 않는다. 운영자는 보존 일수를 정해 자동 삭제를 켤 수 있다(기본 꺼짐). 설정 이름은 보존 정리 기능을 만들 때 정한다([ADR-0029](../adr/0029-indefinite-summary-retention-with-optional-deletion.md))
