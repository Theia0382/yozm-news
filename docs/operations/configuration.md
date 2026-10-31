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
- 내부 대역은 같은 망에 둔 RSS 생성기 같은 피드를 받을 때만 연다. 대역은 쉼표로 이어 적고, 빈 항목은 건너뛴다. 판정은 실제로 연결하는 주소로 하므로, 피드 URL 의 `localhost` 는 IPv6 루프백(`::1`)으로 풀려 `127.0.0.1/32` 밖이 될 수 있다 — 주소로 적거나 두 대역을 함께 연다
- 수집은 시스템 프록시 설정을 읽지 않는다(source-R36). 바깥 연결이 프록시를 거쳐야만 하는 망에서는 피드를 받지 못한다

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

## 공개

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `PROXY_HOST_ADDRESS` | 아니오 | `proxy` 의 호스트 공개 주소 | `0.0.0.0` | proxy 를 공개할 호스트 주소 |
| `PROXY_HOST_PORT` | 아니오 | `proxy` 의 호스트 공개 포트 | `8080` | proxy 를 공개할 호스트 포트 |

- 기본은 이 호스트의 루프백에만 공개한다(기본 주소 · 포트는 `deploy/compose.yaml`). Tunnel 없이 직접 공개하는 셀프 호스팅에서만 바꾼다
- `app` · `postgres` 는 어떤 설정으로도 호스트에 공개되지 않는다

## Compose

| 변수 | 필수 | 쓰는 곳 | 값 예시 | 바꾸는 것 |
|---|---|---|---|---|
| `COMPOSE_PROJECT_NAME` | 아니오 | Compose | `yozmnews-second` | Compose 프로젝트 이름 — 컨테이너 · DB 볼륨 이름이 여기서 정해진다 |

- 기본 이름은 `deploy/compose.yaml` 에 고정돼 있다. 한 호스트에서 두 벌을 띄울 때만 바꾼다. 바꾸면 다른 DB 볼륨을 쓰므로 기존 데이터가 보이지 않는다

## 비밀 값

- 지금 배포 구성의 비밀 값은 `POSTGRES_PASSWORD` 와 [운영자 토큰](#운영자)(`YOZMNEWS_ADMIN_TOKEN`) 둘이다
- **(미구현)** OIDC 공급자의 client secret, Jev API 키, 봇 차단(Turnstile) secret, Tunnel 토큰, BYOK 키 암호화용 KEK
- 비밀 값은 호스트의 환경 파일(`deploy/.env`)에 두고 저장소에 커밋하지 않는다
- **(미구현)** 비밀 값을 Docker secrets 로도 받는다
- **(미구현)** KEK 는 다른 비밀 값과 **별도 파일**로 두고 DB 백업에 넣지 않는다. 버전을 붙여 교체할 수 있게 하고, DB 백업과 다른 위치에 따로 백업한다. KEK 를 잃으면 저장된 사용자 키를 복호화할 수 없다 — 사용자가 키를 다시 등록하면 되므로 치명적이지는 않다. 키 암호화 구조와 KEK 교체 절차는 [security.md](../security.md)

## 데이터 보존

- 보존 기간의 정본은 [data-model.md 보존 정책](../data-model.md#보존-정책-초안)이다
- **(미구현)** RSS 요약문은 기본으로 지우지 않는다. 운영자는 보존 일수를 정해 자동 삭제를 켤 수 있다(기본 꺼짐). 설정 이름은 보존 정리 기능을 만들 때 정한다([ADR-0029](../adr/0029-indefinite-summary-retention-with-optional-deletion.md))
