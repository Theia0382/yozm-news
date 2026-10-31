# 배포와 운영

배포 구성(`deploy/compose.yaml`)을 띄우고 업데이트하고 지켜보는 방법이다. 설정 값은 [운영 설정](configuration.md)에 있다. 이미지 · 검사 구성은 [개발 인프라](../development/infrastructure.md)에 있다.

## 구성

- 배포 단위는 Docker Compose 하나다. VPS · 관리형 클라우드 · 셀프 호스팅 어디서든 같은 정의로 띄운다([ADR-0006](../adr/0006-deployment-compose-cloudflare.md))
- 서버 포트는 외부에 열지 않고 앞단(Cloudflare Tunnel 또는 운영자의 리버스 프록시)으로 공개한다. proxy 는 HTTP 만 받고 TLS 는 앞단이 맡는다
- 상태 저장소는 PostgreSQL 하나다([overview.md §3](../overview.md#3-품질-목표와-제약))

## 호스트 요건

- Docker 와 Compose 플러그인
- `linux/amd64` 또는 `linux/arm64` — 이미지는 두 아키텍처로 빌드된다
- 저장소 전체 — 이미지를 레지스트리에서 받지 않고 소스에서 빌드한다. 배포 구성의 `postgres` 가 루트 `compose.yaml` 을 물려받으므로 `deploy/` 만으로는 뜨지 않는다([ADR-0021](../adr/0021-separate-deploy-compose-with-single-app-service.md))
- 인바운드 포트는 필요 없다(Tunnel). Tunnel 없이 직접 공개하면 proxy 의 포트를 연다([공개](configuration.md#공개))

## 서비스

| 서비스 | 이미지 | 하는 일 |
|---|---|---|
| `proxy` | `frontend/Dockerfile` target `proxy` — Caddy 에 SPA 빌드 결과를 담은 이미지 | SPA 정적 파일을 내려주고 백엔드 경로만 `app` 으로 넘긴다. 넘기지 않는 경로(관리 엔드포인트 포함)는 SPA 가 답한다([넘기는 경로](../development/infrastructure.md#proxy-가-넘기는-경로)) |
| `app` | `backend/Dockerfile` target `app` | 화면용 API 와 백그라운드 작업(작업 서버)을 한 프로세스에서 맡는다 — 정의가 두 역할 profile 을 켠다(`SPRING_PROFILES_ACTIVE: web,worker`, [platform Spec](../specs/platform.md#역할)). **(미구현)** 인증(로그인)도 이 프로세스가 맡는다 |
| `postgres` | 루트 `compose.yaml` 의 PostgreSQL 이미지 | 데이터. 호스트에 공개하지 않는다 |
| **(미구현)** `cloudflared` | Cloudflare Tunnel | `proxy` 로만 연결한다([공개 경로와 외부 서비스](#공개-경로와-외부-서비스)) |
| **(미구현)** `backup` | `pg_dump` + S3 호환 업로드 | [백업과 복구](#백업과-복구) |

- `app` 과 `postgres` 는 호스트에 공개하지 않는다. 공개하는 것은 `proxy` 하나다
- **(미구현)** 역할을 나눠 띄우는 구성 — 같은 이미지를 `web` · `worker` 두 서비스로 띄우고 각 서비스가 `SPRING_PROFILES_ACTIVE` 로 자기 역할 하나를 켠다([overview.md §4](../overview.md#4-시스템-구성)). proxy 가 넘길 곳(`app` 고정)과 스모크가 아직 이 구성을 모른다
- **외부 DB** — 배포 구성은 DB 접속 정보를 Compose 정의 안에서 `postgres` 서비스에 맞춰 고정해 넘긴다([DB 접속](configuration.md#db-접속)). 관리형 DB 를 쓰려면 `postgres` 서비스와 `app` 의 `depends_on` 을 빼고 접속 정보를 바꾼 정의를 써야 한다 — 지금 배포 구성에는 `deploy/.env` 만으로 바꾸는 경로가 없다
- **(미구현)** 사건 묶기가 생기면 DB 에 pgvector 확장이 필요하다. 확장 설치에 superuser 권한이 필요하므로, 앱 계정에 그 권한이 없는 외부 DB 에서는 DBA 가 먼저 설치한다([ADR-0027](../adr/0027-pgvector-for-vector-storage.md))

## 배포 구성 실행

저장소 루트에서 실행한다.

1. 값을 준비한다 — `deploy/.env.example` 을 `deploy/.env` 로 복사하고 필수 값을 적는다([운영 설정](configuration.md)). 필수 값이 없거나 비어 있으면 Compose 가 변수 이름을 말하며 멈춘다
2. 띄우고 확인한다

   ```sh
   cp deploy/.env.example deploy/.env                            # 처음 한 번 — 필수 값을 적는다
   docker compose -f deploy/compose.yaml up -d --build --wait   # 호스트 플랫폼 이미지를 빌드하고 postgres → app → proxy 가 healthy 가 될 때까지 기다린다
   bash deploy/smoke.sh                                          # 기동 상태 · 공개 범위 · 라우팅 · 앱 역할 확인 — 종료 코드 0 이면 통과 (bash · docker · curl 필요)
   ```

   `deploy/` 에서는 `-f` 없이 `docker compose up -d --build --wait` 로 같다. 스모크는 공개 주소를 기본값(루프백)으로 둔 구성을 확인한다 — [공개](configuration.md#공개) 주소를 바꾼 구성에서는 공개 범위 항목이 실패한다.
3. 브라우저로 proxy 의 공개 주소를 연다 — 기본은 이 호스트의 루프백이다([공개](configuration.md#공개))

정지:

```sh
docker compose -f deploy/compose.yaml down      # 정지 — DB 볼륨은 남는다
docker compose -f deploy/compose.yaml down -v   # 정지하고 DB 볼륨까지 지운다 — 데이터가 사라진다
```

- **기동 순서와 healthcheck** — `postgres` 가 healthy 가 된 뒤 `app` 이, `app` 이 healthy 가 된 뒤 `proxy` 가 뜬다. `postgres` 는 `pg_isready` 로, `app` 은 컨테이너 안에서 `/actuator/health` 가 200 인지로(DB 장애 때 unhealthy), `proxy` 는 자기 정적 응답으로 healthy 를 판정한다(주기 · 재시도는 `deploy/compose.yaml`)
- **프로젝트 이름** — 고정이라 컨테이너 · DB 볼륨 이름이 배포 디렉터리 이름과 무관하고, checkout 이름을 따르는 개발용 프로젝트와 겹치지 않는다. 한 호스트에서 두 벌을 띄울 때만 바꾼다([Compose](configuration.md#compose))
- `restart: unless-stopped` 라 호스트가 다시 켜지면 Docker 가 서비스를 다시 띄운다

## 업데이트

- 저장소를 새 버전으로 받고 [배포 구성 실행](#배포-구성-실행)의 `up` 을 다시 돌린다 — 바뀐 이미지가 다시 빌드되고 바뀐 서비스만 다시 뜬다
- DB 마이그레이션은 `app` 이 기동할 때 Flyway 가 실행한다
- **PostgreSQL 메이저가 바뀐 버전으로 올릴 때** — 새 메이저는 데이터를 다른 경로에 두어, 그대로 띄우면 기존 데이터를 두고 빈 클러스터로 뜬다. 올리기 전에 `pg_dump` 로 덤프하고, 새 버전으로 띄운 뒤 복원한다([ADR-0021](../adr/0021-separate-deploy-compose-with-single-app-service.md))
- **(미구현)** 이미지를 레지스트리(GHCR)에 두 아키텍처로 게시하고, 호스트는 `pull` 로 받아 띄운다. 처음에는 수동으로 하고, 이후 SSH 배포 작업으로 자동화한다

## 헬스 체크와 관측

- **헬스 체크** — 앱은 HTTP 로 `health` 엔드포인트만 노출한다(탐색 페이지 `/actuator` 와 probe 그룹 `/actuator/health/liveness` · `/actuator/health/readiness` 포함). 응답에는 집계 상태와 probe 그룹 이름만 담고 구성요소와 상세는 넣지 않는다. 집계에 DB 가 들어 있어 DB 장애 때 상태가 `DOWN` 이 되고 HTTP 503 으로 응답한다
- proxy 는 `/actuator/**` 를 앱으로 넘기지 않는다. 헬스 체크는 컨테이너 안에서 묻는다(위 healthcheck)
- 작업 서버 대시보드(JobRunr)는 꺼져 있다 — 앱 보안 설정과 proxy 밖의 별도 포트로 뜨므로, 운영자 접근 통제가 생긴 뒤에 연다([ADR-0023](../adr/0023-jobrunr-oss-framework-schema-and-event-resubmission.md))
- 로그는 각 서비스의 컨테이너 로그(표준 출력 · 표준 오류)로 남는다 — `docker compose -f deploy/compose.yaml logs`
- **(미구현)** 로그를 JSON 구조화 로그로 내고, 요청 ID 와 사용자 ID 를 담고, 비밀 값을 마스킹한다
- **(미구현)** 지표는 Spring Boot Actuator 와 Micrometer 로 낸다. 핵심 지표는 피드별 수집 성공률, 평가 대기열 길이, Jev 지연 · 토큰, 사건 배정 수 · 애매한 구간 판정 비율 · 열린 사건 수, 다이제스트 · 변화 리포트 생성 성공률 · 소요 시간, 공급자별 오류율이다
- **(미구현)** 대시보드는 운영자 화면으로 시작하고, 필요해지면 지표를 외부(Grafana Cloud 무료 티어 등)로 내보낸다
- **(미구현)** 오류 추적 서비스(Sentry 등)는 선택이다. 붙인다면 요청 본문과 헤더 전송을 끈다

## 공개 경로와 외부 서비스

Cloudflare 는 권장 구성이지 필수가 아니다. 셀프 호스팅에서는 Tunnel 대신 일반 리버스 프록시를, R2 대신 다른 S3 호환 저장소를, Turnstile 대신 다른 봇 차단 수단을 쓰거나 끌 수 있다([ADR-0012](../adr/0012-swappable-external-dependencies.md)).

| 기능 | 용도 |
|---|---|
| **(미구현)** Tunnel | 인바운드 포트 없이 공개한다. `proxy` 로만 연결한다 |
| **(미구현)** DNS · 프록시 · CDN | 도메인, TLS, 정적 파일 캐싱 |
| **(미구현)** WAF · 요청 제한 규칙 | 로그인 · 키 등록 · 수동 재생성 같은 민감한 엔드포인트의 1차 방어 |
| **(미구현)** R2 | DB 백업 저장. 외부로 나가는 전송량 요금이 없다 |
| **(미구현)** AI Gateway (선택) | Jev 호출 경유 — 호출 로그, 비용 집계, 재시도, 한도. Jev 지원 여부는 확인 전이다([미결 #15](../open-questions.md)) |

- **(미구현)** 사용자 BYOK LLM 호출은 AI Gateway 를 거치지 않고 공급자에 직접 보낸다 — 사용자 키와 프롬프트가 제3자 로그에 남지 않게 하기 위해서다
- **(미구현)** Turnstile 은 자체 가입 · 로그인 · 복구 코드 사용 화면에 붙인다([ADR-0009](../adr/0009-authentication-google-and-local.md) · [ADR-0011](../adr/0011-no-email-in-v1.md))
- 운영자 화면의 피드 켜기는 앱이 그 피드를 받아 확인한 뒤 답한다 — 같은 언론사의 앞선 요청을 기다린 시간(요청 하나당 최대 10초)과 받는 시간(최대 10초)이 더해진다. 배포 `proxy`(Caddy)는 응답 시간을 제한하지 않는다. 앞단(Tunnel · 리버스 프록시)에 응답 시간 제한이 있으면 그보다 길어진 켜기는 화면에 실패로 보이고, 서버의 판정 · 저장은 그대로 끝난다 — 목록을 다시 읽으면 결과가 보인다
- 이 프로젝트의 공식 인스턴스 도메인은 `yozmnews.net` 이다. Cloudflare Registrar 로 등록해 DNS 도 Cloudflare 에서 관리한다

## 백업과 복구

- **(미구현)** 매일 `pg_dump` 를 S3 호환 저장소(기본 R2)에 올리고 30일 보관한다
- **(미구현)** 분기마다 복구 리허설을 한다 — 새 환경에 배포 구성을 띄우고 덤프를 복원해 동작을 확인한다
- **(미구현)** KEK 는 DB 백업과 다른 위치에 따로 백업한다([비밀 값](configuration.md#비밀-값))
