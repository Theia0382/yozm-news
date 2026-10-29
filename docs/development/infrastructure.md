# 개발 인프라

개발용 Compose · 검사 환경 · 이미지 · CI 의 구성과, 그 구성이 지키는 불변식이다. 실행 명령은 [CONTRIBUTING.md §1](../../CONTRIBUTING.md#1-개발-환경)에 있다. 배포 구성을 띄우고 운영하는 일은 [operations/](../operations/README.md)에 있다.

## 구성 파일

| 파일 | 역할 | 정본인 것 |
|---|---|---|
| [compose.yaml](../../compose.yaml) | 개발용 의존 서비스(Compose 프로필 `dev`) | PostgreSQL 이미지 — 테스트 · 검사 이미지 · 배포 구성이 읽는다 |
| [.env.example](../../.env.example) | 개발용 Compose 가 읽는 값의 목록 | 바꿀 수 있는 호스트 포트 |
| [compose.check.yaml](../../compose.check.yaml) | 검사 정의 — CI 와 로컬이 함께 쓴다 | 검사 서비스 목록 |
| [backend/Dockerfile](../../backend/Dockerfile) · [frontend/Dockerfile](../../frontend/Dockerfile) | 측별 이미지 — 검사 target 과 배포 target | 빌드 환경(기반 이미지 · 버전 대조 · 의존성 설치) |
| [tools/docs-check/Dockerfile](../../tools/docs-check/Dockerfile) | 문서 검사 이미지 | 검사 실행 환경(기반 이미지 · 검사 명령) |
| 각 Dockerfile 옆의 `Dockerfile.dockerignore` | 이미지에 들어가는 파일의 허용 목록 | 검사 · 빌드 입력 |
| [.github/workflows/ci.yml](../../.github/workflows/ci.yml) | CI | job · 계기 · 캐시 · 보고서 |
| [frontend/proxy/](../../frontend/proxy/) | 배포 proxy(Caddy) 설정 | 앱으로 넘기는 경로(`backend-paths.caddy`) |
| [deploy/](../../deploy/) | 배포 구성과 스모크 — 띄우고 운영하는 방법은 [배포와 운영](../operations/deployment.md) | 배포 서비스 정의 · 운영 값 목록 |

## 개발용 Compose

- 루트 `compose.yaml` 의 서비스는 모두 프로필 `dev` 에 있다 — PostgreSQL 과 로컬 OIDC 공급자(`mock-oauth2-server`)
- **(미구현)** 메일을 도입하면 발송 메일을 확인할 `mailpit` 이 더해진다([ADR-0012](../adr/0012-swappable-external-dependencies.md))
- 백엔드를 Spring profile `local` 로 실행하면 앱이 이 Compose 를 띄우고, 컨테이너에서 PostgreSQL 접속 정보를 읽는다. 이 연동이 켜져 있는 동안 DB 접속 환경 변수는 쓰이지 않는다([ADR-0018](../adr/0018-local-profile-compose-connection.md)). `local` 의 나머지 값(명세 엔드포인트 열기 · 수집 연락처)은 `backend/src/main/resources/application-local.yaml` 에 있다
- Compose 프로젝트 이름을 정하지 않으므로 checkout(작업 디렉터리) 이름이 프로젝트 이름이 된다. 그래서 checkout 마다 컨테이너 · 네트워크 · 개발 DB 볼륨이 따로 생긴다([ADR-0019](../adr/0019-dev-compose-per-checkout-and-test-image-source.md)). 호스트 포트는 루프백에만 연다
- mock OIDC 서버의 issuer 가 호스트 포트로 만들어지므로 그 포트는 고정이다. PostgreSQL 의 호스트 포트는 비워 두면 Docker 가 고르고, 앱은 공개된 포트를 읽어 접속한다

### PostgreSQL 이미지 정본

PostgreSQL 이미지는 `compose.yaml` 의 `postgres` 서비스 `image` 한 줄이 정한다([ADR-0019](../adr/0019-dev-compose-per-checkout-and-test-image-source.md)). 같은 값을 세 곳이 읽는다.

- **테스트** — Gradle 이 그 파일의 경로를 시스템 속성으로 넘기고 테스트 입력으로 등록한다(`backend/build.gradle.kts`). 테스트 설정이 파일에서 이미지를 읽어 Testcontainers 에 넘긴다. 파일만 바뀌어도 테스트가 다시 돈다
- **백엔드 검사 이미지** — 검사 target 이 `compose.yaml` 을 이미지에 복사한다([아래 결선](#테스트가-저장소-파일을-읽는-결선))
- **배포 구성** — `deploy/compose.yaml` 의 `postgres` 가 `extends` 로 이 서비스를 물려받는다([ADR-0021](../adr/0021-separate-deploy-compose-with-single-app-service.md))

## 검사 환경

`compose.check.yaml` 이 검사 정의 하나이고, CI 와 로컬이 같은 정의를 부른다([ADR-0016](../adr/0016-containerized-checks.md) · [ADR-0035](../adr/0035-public-markdown-links-and-anchors-checked-by-separate-check-service.md)).

| 서비스 | 하는 일 |
|---|---|
| `backend` | 백엔드 검사 이미지(target `check`)에서 Gradle `build` — 컴파일 · 전체 테스트 · 검사 |
| `frontend` | 프론트엔드 검사 이미지(target `check`)에서 `build` · `lint` · `format:check` · `test` 를 모두 돌리고 하나라도 실패하면 실패 |
| `docs` | 문서 검사 이미지에서 검사기 단위 테스트와 저장소의 링크 검사를 모두 돌린다 |
| `docker` | 백엔드 테스트의 Testcontainers 가 쓰는 Docker 엔진(DinD) |

- **소스는 이미지에 복사한다.** 작업 트리를 bind mount 하지 않는다. 빌드 컨텍스트는 저장소 루트이고, 각 Dockerfile 의 `Dockerfile.dockerignore` 가 이미지에 넣을 파일의 허용 목록이다. Dockerfile 하나가 검사 target 과 배포 target 을 함께 가지므로 허용 목록은 두 용도의 합집합이다([ADR-0022](../adr/0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md))
- 허용 목록 안에서 저장소가 추적하지 않는 파일(`.gitignore` 의 대상)을 다시 빼는 줄은 `.gitignore` 를 손으로 옮긴 사본이다. CI 의 체크아웃과 같은 입력으로 검사하려고 두는 것이라, `.gitignore` 를 바꾸면 이 줄들도 맞춘다
- **DinD** — Testcontainers 는 호스트 Docker 가 아니라 검사 정의 안의 엔진을 쓴다. 그래서 호스트 종류(rootful · rootless · Docker Desktop)와 무관하게 구성이 같다. 엔진은 privileged 로 돌고, 공개 포트 없이 검사 정의의 내부 네트워크에만 열린다
- 세 검사 이미지는 시간대 · 로캘을 같은 값으로 고정한다(각 Dockerfile 의 `ENV`)
- 도구 버전은 공식 이미지의 태그가 갖는다. JDK 태그는 Gradle toolchain 의, Node 태그는 `frontend/.nvmrc` 의 사본이고, 어긋나면 이미지 빌드가 실패한다. pnpm 은 corepack 이 `frontend/package.json` 의 `packageManager` 를 읽어 받는다

### 테스트가 저장소 파일을 읽는 결선

백엔드 테스트는 저장소 파일 둘을 읽는다 — PostgreSQL 이미지를 정하는 `compose.yaml` 과, 현재 코드의 명세와 비교하는 화면용 API 명세 `api/openapi.json`([ADR-0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md)). 파일마다 네 곳이 맞물린다.

1. `backend/build.gradle.kts` 가 파일 경로를 테스트에 시스템 속성으로 넘긴다
2. 같은 파일이 Gradle 테스트 태스크의 입력으로 등록되어, 파일이 바뀌면 테스트가 다시 돈다
3. 백엔드 검사 target 이 파일을 이미지에 복사한다(`backend/Dockerfile`)
4. 백엔드 허용 목록(`backend/Dockerfile.dockerignore`)이 파일을 빌드 컨텍스트에 넣는다

IDE 에서 테스트를 Gradle 에 맡기는 것은 1 때문이다([CONTRIBUTING.md §1](../../CONTRIBUTING.md#1-개발-환경)). 4 가 빠지면 복사할 파일이 빌드 컨텍스트에 없어 검사 이미지 빌드가 실패하고, 3 이 빠지면 이미지는 만들어지지만 그 안의 백엔드 검사가 파일이 없어 실패한다.

### 문서 링크 검사

`tools/docs-check` 는 공개 마크다운의 상대 링크와 앵커를 확인한다([ADR-0035](../adr/0035-public-markdown-links-and-anchors-checked-by-separate-check-service.md)).

- 검사기(`check_links.py`)는 표준 라이브러리만 쓰는 Python 이라 이미지에 설치 단계가 없다. 컨테이너는 검사기의 단위 테스트(`test_check_links.py`)와 저장소 검사를 모두 돌린다
- 검사한 마크다운이 없거나 `docs/README.md` 가 대상에 없으면 실패한다 — 허용 목록이 문서를 빠뜨린 이미지가 링크 0개로 통과하지 않게 하는 하한이다
- 이미지에는 검사기 · 대상 문서 · 문서가 가리키는 문서 밖 경로만 들어간다(`tools/docs-check/Dockerfile.dockerignore`). 문서가 허용 목록에 없는 경로를 가리키면 "경로 없음"으로 실패한다. 경로를 더하는 절차는 [CONTRIBUTING.md §1](../../CONTRIBUTING.md#1-개발-환경), 검사가 읽는 링크 형식은 [docs/README.md §4](../README.md#4-작성-규칙)

## 이미지

- **측별 Dockerfile 하나** — `backend/Dockerfile` 은 target `check` · `app`, `frontend/Dockerfile` 은 target `check` · `proxy` 를 갖는다. 의존성 단계를 검사 target 과 배포 빌드가 함께 쓴다. 문서 검사 이미지는 검사 전용 `tools/docs-check/Dockerfile` 하나다([ADR-0022](../adr/0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md))
- **두 아키텍처** — 배포 이미지는 `linux/amd64` · `linux/arm64` 로 빌드한다([ADR-0006](../adr/0006-deployment-compose-cloudflare.md)). 빌드 단계는 빌더 플랫폼에서 돌고(jar · 정적 파일은 아키텍처와 무관하다), 실행 단계는 대상 플랫폼 이미지에 복사와 메타데이터만 둔다. 실행 단계에 명령(`RUN`)이 있으면 에뮬레이션이 없는 빌더에서 빌더와 다른 아키텍처의 빌드가 깨진다
- 두 아키텍처 빌드는 buildx bake 로 `deploy/` 에서 한다 — bake 는 `extends` 대상과 빌드 컨텍스트를 작업 디렉터리 기준으로 읽는다([ADR-0021](../adr/0021-separate-deploy-compose-with-single-app-service.md)). 결과가 어디에 남는지는 빌더 드라이버에 따라 다르다 — `docker` 드라이버에 containerd 이미지 저장소면 두 아키텍처 이미지가 로컬 이미지로 남고, `docker-container` 드라이버면 빌드 캐시에만 남는다
- **실행 계정** — 우리가 만드는 이미지(`app` · `proxy`)는 root 가 아닌 숫자 uid 로 돈다. 계정을 만드는 명령 없이 `USER` 로 정한다(값은 각 Dockerfile). proxy 는 이 계정으로 돌 수 있게 관리 API 를 열지 않고 설정을 디스크에 저장하지 않는다(`frontend/proxy/Caddyfile`)

### 이미지 버전

- 모든 이미지는 메이저 태그로 받는다 — 검사 · 실행 기반 이미지(`FROM`)와 개발용 서비스 · DinD(`image:`). 변형이나 OS 계열을 붙인 태그(`-slim` · `-dind` · `-jdk`, `pg{메이저}-{OS 계열}` 형식)도 메이저 태그다([ADR-0038](../adr/0038-container-images-use-major-tags-and-revisit-pinning-at-publication.md))
- 다시 빌드하거나 받으면 그 메이저의 최신 패치가 들어온다. 그래서 CI 에서 검사한 이미지와 나중에 빌드한 이미지의 패치가 다를 수 있다
- JDK · Node 의 메이저는 버전 정본(Gradle toolchain · `frontend/.nvmrc`)과 함께 바꾼다. PostgreSQL 의 메이저를 바꾸면 배포 DB 를 먼저 옮겨야 한다([업데이트](../operations/deployment.md#업데이트))
- 메이저 태그가 게시되지 않는 이미지만 정확한 버전으로 두고, 그 줄에 사유를 적는다. 지금은 그런 이미지가 없다

## proxy 가 넘기는 경로

브라우저에는 단일 오리진으로 서비스한다 — proxy 가 SPA 정적 파일을 내려주고 백엔드 경로만 앱으로 넘긴다([ADR-0005](../adr/0005-frontend-react-spa.md)).

- 넘기는 경로의 정본은 `frontend/proxy/backend-paths.caddy` 한 줄이다. 배포 proxy(Caddy)는 이 파일을 `import` 하고, Vite 개발 서버는 `frontend/proxy/backendPaths.ts` 가 같은 파일을 읽어 proxy 항목을 만든다
- 두 쪽의 차이가 하나 있다 — Caddy 의 경로 일치는 대소문자를 구분하지 않고, Vite 쪽 정규식은 구분한다
- 넘기지 않는 경로는 SPA 가 답한다. 관리 · 명세 엔드포인트(`/actuator/**` · `/v3/api-docs`)도 넘기지 않는다

## CI

`.github/workflows/ci.yml` 이 `main` 대상 PR 과 `main` push 에서 돈다. 같은 PR 에 새 push 가 오면 이전 실행을 취소하고, `main` push 끼리는 취소하지 않는다.

| job | 하는 일 |
|---|---|
| `backend` | 검사 정의의 `backend` · `docker` 로 백엔드 검사 |
| `frontend` | 검사 정의의 `frontend` |
| `docs` | 검사 정의의 `docs` |
| `deploy` | 배포 이미지를 두 아키텍처로 빌드하고, 배포 구성을 띄워 healthcheck 를 기다린 뒤 `deploy/smoke.sh` |

- **필수 체크** — 네 job 모두 `main` 의 필수 체크다. 필수 여부는 저장소 설정(branch protection)이 정한다
- **캐시** — 각 job 은 검사를 돌리기 전에 같은 정의로 이미지를 buildx bake 로 먼저 빌드해, GitHub Actions 캐시를 계층 캐시로 쓴다. 캐시 범위는 job 마다 따로다(`ci.yml`)
- **실패 보고서** — `backend` 검사 단계가 실패했을 때만 검사 컨테이너에서 테스트 보고서(HTML · JUnit XML)를 꺼내 artifact 로 올린다. 이미지 빌드 실패처럼 보고서가 없는 실패에서는 꺼내지 않는다. `deploy` 가 실패하면 배포 구성의 로그를 출력한다
- **빌더 확인** — `deploy` 는 빌드 전에 빌더에 arm64 에뮬레이션이 없음을 확인한다. 에뮬레이션이 있으면 실행 단계의 `RUN` 이 두 아키텍처 빌드에서 드러나지 않기 때문이다
- 시간 제한 · 캐시 범위 이름 · 보고서 보관 기간 같은 값은 `ci.yml` 에 있다

## 불변식과 지키는 장치

지금 구성이 지키려는 불변식과 그것을 지키는 장치다. 장치 열의 "구조"는 사본을 두지 않아 어긋날 수 없게 만든 것이다. 새 구성을 더하는 변경은 그 불변식과 장치를 이 표에 더한다([docs/README.md §3](../README.md#3-갱신-트리거) "개발 인프라 변경").

| 불변식 | 지키는 장치 | 정의 위치 |
|---|---|---|
| 이미지의 JDK 메이저 = Gradle toolchain | 빌드 실패 — 의존성 단계가 JDK 를 내려받지 않아 다르면 실패 | `backend/Dockerfile` · `backend/build.gradle.kts` |
| 이미지의 Node 메이저 = `frontend/.nvmrc` | 빌드 실패 — 의존성 단계의 버전 대조 | `frontend/Dockerfile` |
| 프론트엔드 의존성은 lockfile 그대로 설치한다 | 빌드 실패 — `--frozen-lockfile` | `frontend/Dockerfile` |
| 배포 target 의 실행 단계에 `RUN` 이 없다 | CI 단계 — `deploy` 의 두 아키텍처 빌드와 빌더 에뮬레이션 확인 | `backend/Dockerfile` · `frontend/Dockerfile` · `ci.yml` |
| PostgreSQL 이미지는 `compose.yaml` 한 줄이 정하고 테스트가 그 값을 쓴다 | 구조 + 테스트 `DevComposeFileTests`(파일 · 서비스 · 이미지가 없거나 보간 · 시스템 속성 없음은 실패) | `compose.yaml` · `backend/build.gradle.kts` |
| 배포 구성의 PostgreSQL 이 같은 이미지를 쓴다 | 구조(`extends`) + 스모크 K9 | `deploy/compose.yaml` · `deploy/smoke.sh` |
| 테스트가 읽는 저장소 파일이 검사 이미지에 있다 | 빌드 실패(허용 목록에 없으면 복사가 실패) · 검사 실패(복사하지 않으면 이미지 안에 파일이 없다) | [결선](#테스트가-저장소-파일을-읽는-결선) |
| 커밋된 화면용 API 명세 = 코드의 명세 | 테스트 `OpenApiSpecTests` | `api/openapi.json` · `backend/build.gradle.kts` |
| 넘기는 경로의 정본 하나를 Caddy · Vite 가 함께 읽는다 | 테스트 `backendPaths.test.ts` + 스모크 K5 · K6 · K10 | `frontend/proxy/` |
| proxy 가 관리 · 명세 엔드포인트를 넘기지 않는다 | 스모크 K7 · K8 | `frontend/proxy/backend-paths.caddy` |
| 배포 구성은 proxy 만 호스트에 공개하고 기본은 루프백이다 | 스모크 K2 | [공개](../operations/configuration.md#공개) |
| 배포 구성의 세 서비스가 순서대로 healthy 가 된다 | 구조(`depends_on` 의 healthy 조건) + `up --wait` + 스모크 K1 | [배포 구성 실행](../operations/deployment.md#배포-구성-실행) |
| 배포 필수 값이 없으면 멈춘다 | 구조(Compose 필수 보간) + 테스트 `RssArticleSourceConfigurationTests`(수집 연락처) | [운영 설정](../operations/configuration.md) |
| HTTP 로는 `health` 만 노출하고 상세 · 구성요소를 담지 않는다 | 테스트 `HealthEndpointTests` | [헬스 체크와 관측](../operations/deployment.md#헬스-체크와-관측) |
| 명세 엔드포인트는 기본으로 닫혀 있다 | 테스트 `ApiDocsExposureTests` | [platform Spec §7](../specs/platform.md#7-설정) |
| JobRunr 대시보드는 꺼져 있고, 사용 통계를 보내지 않고, 라이브러리가 스키마를 만들지 않는다 | 테스트 `JobRunrConfigurationTests` | `application.yaml` |
| 작업 서버의 동시 실행 수는 호스트 코어 수가 아니라 설정값이다 | 테스트 `JobRunrConfigurationTests` | `application.yaml` |
| 문서 검사가 대상 문서를 빠뜨린 채 통과하지 않는다 | 부분 — 스캔 하한(`ScanFloorTest`)은 `docs/README.md` 하나만 본다. 허용 목록이 다른 대상 문서(루트 `*.md` · `.github/`)를 빠뜨리면 그 문서를 검사하지 않고 통과한다 | `tools/docs-check/` |
| CI 와 로컬이 같은 정의 · 같은 명령으로 검사한다 | 부분 — 정의는 구조(같은 `compose.check.yaml`). 명령 문자열은 `ci.yml` 과 CONTRIBUTING 에 따로 있고, CI 는 이미지를 먼저 빌드해 `--build` 를 빼고 부른다 | `ci.yml` · [CONTRIBUTING.md §1](../../CONTRIBUTING.md#1-개발-환경) |
| `app` · `proxy` 는 root 가 아닌 uid 로 돈다 | 없음 | `backend/Dockerfile` · `frontend/Dockerfile` |
| proxy 는 관리 API 를 열지 않고 설정을 디스크에 저장하지 않는다 | 없음 | `frontend/proxy/Caddyfile` |
| 검사 이미지에 저장소가 추적하지 않는 파일이 들어가지 않는다 | 없음 — 허용 목록의 무시 줄은 `.gitignore` 의 손 사본이다 | 각 `Dockerfile.dockerignore` |
| 작업 서버가 멈추면 `health` 가 `DOWN` 이 된다 | 없음 — 테스트는 집계에 DB 가 들어 있는지만 본다 | `application.yaml` |
| DinD 엔진은 공개 포트 없이 검사 정의 안에만 열린다 | 없음 | `compose.check.yaml` |
| 검사 결과가 호스트의 시간대 · 로캘과 무관하다 | 없음 — 세 검사 이미지가 같은 `ENV` 를 각자 둔다 | 세 Dockerfile |
| 개발용 Compose 의 포트는 호스트 루프백에만 열린다 | 없음 | `compose.yaml` |
| PostgreSQL 메이저를 바꾸기 전에 배포 DB 를 옮긴다 | 없음 — 절차 | [업데이트](../operations/deployment.md#업데이트) |
| 작업 서버 동시 실행 수 변수를 빈 값 줄로 두지 않는다 | 없음 | [백그라운드 작업](../operations/configuration.md#백그라운드-작업) |
| 배포 구성의 Compose 프로젝트 이름이 고정이다 | 없음 — 정의 한 줄 | [Compose](../operations/configuration.md#compose) |
