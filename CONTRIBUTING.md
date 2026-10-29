# 기여 가이드

요즘뉴스 팀의 공용 규약이다. 브랜치 · 커밋 · PR · 이슈 · 개발 환경의 정본은 이 문서다. 설계 문서는 [docs/README.md](docs/README.md)부터 읽는다.

## 1. 개발 환경

### 필요한 것

- **Docker**(Compose 플러그인 포함) — 검사(아래)는 컨테이너에서 돌고, 검사에 필요한 호스트 도구는 Docker 뿐이다. privileged 컨테이너를 허용해야 한다(검사 정의의 구성은 [개발 인프라 — 검사 환경](docs/development/infrastructure.md#검사-환경))

호스트에서 직접 빌드하거나 개발 서버를 돌릴 때는 다음도 필요하다.

- **JDK 25**
- Gradle 은 따로 설치하지 않는다 — 저장소의 wrapper(`backend/gradlew`)를 쓴다. 호스트에서 돌리는 테스트도 Testcontainers 로 PostgreSQL 컨테이너를 띄운다
- **Node.js 24** (`frontend/.nvmrc`)
- **pnpm** — 설치된 버전과 관계없이 `frontend/package.json` 의 `packageManager` 에 고정한 버전으로 실행된다

### 검사 (CI 와 같음)

PR 을 올리기 전에 돌리는 검사다. CI 와 같은 정의(`compose.check.yaml`)와 같은 명령을 써서, 결과가 호스트의 OS · 도구 버전 · Docker 구성에 따라 달라지지 않게 한다([ADR-0016](docs/adr/0016-containerized-checks.md)). 저장소 루트에서 실행한다.

```sh
docker compose -f compose.check.yaml up --build --force-recreate --attach backend --abort-on-container-exit --exit-code-from backend backend docker   # 백엔드: backend/gradlew -p backend build
docker compose -f compose.check.yaml run --rm --build frontend   # 프론트엔드: 의존성 고정 설치 뒤 build · lint · format:check · test
docker compose -f compose.check.yaml run --rm --build docs       # 문서: 공개 마크다운의 상대 링크 · 앵커
```

- 종료 코드가 검사 결과다. 백엔드 로그에 `> Task :test` 가 `UP-TO-DATE` 없이 찍혀야 테스트가 실제로 돈 것이다
- 백엔드 결과 보고서는 작업 트리에 생기지 않는다. 검사가 끝난 컨테이너에서 꺼낸다 — `docker compose -f compose.check.yaml cp backend:/workspace/backend/build/reports/tests/test <받을 디렉터리>`
- 정리: `docker compose -f compose.check.yaml down -v` — 멈춘 컨테이너와 검사용 Docker 엔진의 데이터 볼륨을 지운다(다음 백엔드 검사가 PostgreSQL 이미지를 다시 받는다)
- 문서 검사 이미지에는 문서와 문서가 가리키는 경로만 들어간다. 문서가 새 경로(예: 새 최상위 디렉터리)를 가리켜 "경로 없음"으로 실패하면 그 경로를 허용 목록 `tools/docs-check/Dockerfile.dockerignore` 에 더한다

배포 구성 검사는 배포 구성을 [배포 구성 실행](docs/operations/deployment.md#배포-구성-실행) 대로 띄워 스모크(`deploy/smoke.sh`)를 돌린다 — 스모크의 종료 코드가 검사 결과다. 검사가 끝나면 DB 볼륨까지 지우는 정지로 정리한다. CI 는 같은 정의로 이미지를 두 아키텍처(`linux/amd64` · `linux/arm64`)로도 빌드한다 — 로컬에서는 `deploy/` 에서 실행한다([개발 인프라 — 이미지](docs/development/infrastructure.md#이미지)).

```sh
docker buildx bake --set '*.platform=linux/amd64,linux/arm64' app proxy   # deploy/ 에서 — 두 아키텍처 배포 이미지 빌드
```

- 배포 target 의 실행 단계에는 명령(`RUN`)을 두지 않는다 — 에뮬레이션 없는 빌더에서 다른 아키텍처의 빌드가 깨진다. 이미지 구성은 [개발 인프라 — 이미지](docs/development/infrastructure.md#이미지)

### 호스트에서 직접 실행 (빠른 반복)

일부 테스트만 돌리거나 개발 서버를 띄울 때 쓴다. 결과가 호스트 환경에 따라 다를 수 있으니 PR 전에는 위의 검사를 돌린다. 저장소 루트에서 실행한다.

**백엔드**

```sh
backend/gradlew -p backend test                                  # 전체 테스트
backend/gradlew -p backend test --tests 'net.yozmnews.digest.*'  # 일부만
backend/gradlew -p backend build                                 # 컴파일 + 테스트 + 검사
```

결과는 `backend/build/test-results/test/` (JUnit XML) 와 `backend/build/reports/tests/test/index.html` 에 남는다.

IDE 에서 테스트를 돌릴 때도 Gradle 로 실행을 위임한다. 위임하지 않으면 PostgreSQL 컨테이너를 띄우는 테스트가 시스템 속성이 없다는 메시지로 실패한다 — [테스트가 저장소 파일을 읽는 결선](docs/development/infrastructure.md#테스트가-저장소-파일을-읽는-결선).

**백엔드 로컬 실행**

개발용 의존 서비스(PostgreSQL · mock OIDC 서버)는 저장소 루트 `compose.yaml` 의 Compose 프로필 `dev` 에 있다. 백엔드를 Spring profile `local` 로 실행하면 앱이 이 Compose 를 띄우고 그 PostgreSQL 에 접속한다([ADR-0018](docs/adr/0018-local-profile-compose-connection.md)). Docker 가 필요하다.

```sh
backend/gradlew -p backend bootRun --args=--spring.profiles.active=local  # 백엔드 실행 — 헬스 체크 http://localhost:8080/actuator/health
docker compose --profile dev up -d                                       # (선택) 의존 서비스만 먼저 띄우기
docker compose --profile dev down -v                                     # 컨테이너와 개발 DB 볼륨 삭제 — DB 초기화
```

- IDE 에서는 이 Gradle 태스크(`bootRun`)로 실행을 위임한다
- Compose 를 먼저 띄우지 않았으면 앱이 띄우고, 앱이 끝날 때 멈춘다. 먼저 띄워 두었으면 앱이 끝나도 그대로 둔다
- 바꿀 수 있는 값(호스트 포트)은 `.env.example` 에 있다. 필요한 줄만 `.env` 로 옮긴다 — `.env` 는 없어도 된다
- 컨테이너 · 볼륨은 checkout(작업 디렉터리)마다 따로 생긴다. 두 checkout 의 환경을 함께 띄우면 mock OIDC 서버의 호스트 포트가 겹치니 한쪽의 `.env` 에서 바꾼다. checkout 을 지우기 전에 그 checkout 에서 `down -v` 로 정리한다
- 자기 PostgreSQL 을 쓰려면 Compose 연동을 끄고(`SPRING_DOCKER_COMPOSE_ENABLED=false`) DB 접속 환경 변수(`SPRING_DATASOURCE_*` — [운영 설정 — DB 접속](docs/operations/configuration.md#db-접속))를 준다
- PostgreSQL 이미지는 `compose.yaml` 이 정한다([PostgreSQL 이미지 정본](docs/development/infrastructure.md#postgresql-이미지-정본)). 메이저 버전을 올린 뒤에는 `down -v` 로 개발 DB 를 다시 만든다

**프론트엔드**

```sh
pnpm -C frontend install       # 의존성 설치 (처음 한 번, lockfile 이 바뀌었을 때)
pnpm -C frontend dev           # API 타입 생성 + 개발 서버 — /api/** · /oauth2/** · /login/** 은 localhost:8080 백엔드로 넘긴다(정본 frontend/proxy/backend-paths.caddy)
pnpm -C frontend build         # API 타입 생성 + 타입 검사 + 빌드
pnpm -C frontend lint          # ESLint
pnpm -C frontend format        # Prettier 로 정렬 (검사만: format:check)
pnpm -C frontend test          # Vitest
```

UI 컴포넌트는 shadcn 으로 추가한다 — `pnpm -C frontend dlx shadcn@latest add <컴포넌트>` (`src/shared/ui` 에 생긴다).

**화면용 API 명세와 타입**

화면용 API(`/api/**`) 명세의 정본은 저장소 루트 `api/openapi.json` 이다. 프론트엔드 API 타입은 이 명세에서 생성한다([ADR-0020](docs/adr/0020-screen-api-contract-from-committed-openapi-spec.md)).

```sh
backend/gradlew -p backend updateOpenApiSpec  # 현재 백엔드 코드의 명세로 api/openapi.json 다시 쓰기 — Docker 필요(Testcontainers)
pnpm -C frontend generate:api                 # api/openapi.json 에서 API 타입(frontend/src/shared/api/schema.d.ts) 생성
```

- 화면용 API 를 바꾸면 `updateOpenApiSpec` 으로 명세를 다시 써서 함께 커밋한다. 명세가 코드와 다르면 백엔드 테스트가 이 명령을 안내하며 실패한다
- 생성 타입은 커밋하지 않는다. `dev` · `build` 는 먼저 생성한다. clone 직후와 명세가 바뀐 브랜치를 받은 뒤에는 `generate:api` 를 한 번 돌려야 IDE 가 새 타입을 본다
- 명세가 프론트엔드 코드와 맞지 않으면 프론트엔드 `build` 의 타입 검사가 실패한다
- 실행 중인 백엔드의 명세 엔드포인트(`http://localhost:8080/v3/api-docs`)는 기본으로 닫혀 있고 Spring profile `local` 로 실행하면 열린다([platform Spec §7](docs/specs/platform.md#7-설정))

테스트로 무엇을 증명해야 하는지는 [테스트 전략](docs/qa/test-strategy.md)을 따른다.

## 2. 이슈

작업은 GitHub Issues 로 관리한다. 진행 상황은 이슈 상태(열림 · 닫힘), 담당자, 연결된 PR 로 보고, 로드맵 단계는 Milestone 으로 묶는다.

- **제목**은 한국어. 모듈 작업이면 앞에 모듈 이름 — `[digest] 후보 선별 쿼리 구현`
- **본문**에 목적, 수용 기준(AC), 관련 문서(`docs/` · ADR 링크)를 적는다
- **라벨은 하나** — 작업 유형([§4 유형](#4-커밋-메시지)에서 `revert` 를 뺀 값). 작업 브랜치 접두사와 같다
- 큰 묶음은 상위 이슈 + 하위 이슈(sub-issue), 로드맵 단계는 Milestone
- **담당자** — 작업을 시작할 때 자기를 담당자로 지정한다. 담당자가 있는 열린 이슈가 진행 중인 작업이고, 담당자가 없는 열린 이슈가 백로그다
- 작업을 시작하기 전에 이슈가 있어야 한다. 버그는 원인과 범위를 파악한 뒤에 정확히 만든다
- **새 모듈**은 그 모듈의 Spec([docs/specs/](docs/specs/README.md))을 만드는 설계 이슈를 먼저 진행하고, 구현 이슈는 그 Spec 을 토대로 한다

## 3. 브랜치

```
main ── 통합 브랜치 (작업 브랜치의 PR merge 대상)
 ├── feat/12-frontend-init
 └── fix/15-feed-date-parse
```

- 작업 브랜치는 `main` 에서 분기하고, PR 로 `main` 에 merge 한다
- `main` 에 직접 커밋 · push 하지 않는다
- 작업 브랜치의 이력은 선형으로 유지한다 — `main` 을 들이는 동기화 merge([§5](#5-pr) 충돌 · 동기화)만 예외다
- PR 이 merge 되면 원격 작업 브랜치는 GitHub 가 지운다(저장소 설정 Automatically delete head branches)
- **이름**: `{접두사}/{이슈 번호}-{설명}` — 접두사는 이슈 라벨(작업 유형)과 같고, 설명은 영문 소문자 kebab-case

## 4. 커밋 메시지

```
{유형}({영역}): {요지}

- 변경 항목 1
- 변경 항목 2

Issue: #12
```

- **유형**: [Conventional Commits](https://www.conventionalcommits.org/) 의 type 을 따른다

  | 유형 | 대상 |
  |---|---|
  | `feat` | 새 기능 |
  | `fix` | 버그 수정 |
  | `refactor` | 동작 변경 없는 구조 개선 |
  | `perf` | 성능 개선 |
  | `test` | 테스트 추가 · 개선 |
  | `docs` | 문서만 |
  | `build` | 빌드 시스템 · 의존성 (Gradle · npm · Dockerfile) |
  | `ci` | CI 설정 |
  | `chore` | 그 밖의 잡무 (설정 · 저장소 관리) |
  | `revert` | 이전 커밋 되돌리기 — 커밋에만 쓰고 이슈 라벨 · 브랜치 접두사로는 쓰지 않는다 |

- **영역**: 아래 표의 낱말 하나 — 주된 영역. 해당하는 영역이 없거나 셋 이상을 고르게 걸치면 `({영역})` 을 생략한다
- **요지**: 한 가지 변경의 요지, 한국어 50자 이내, 명사형 종결(`~ 추가` · `~ 정정` · `~ 통합`)
- **본문**: 제목만으로 무엇이 바뀌었는지 다 드러나지 않으면 변경 항목 bullet 을 쓴다. 여러 변경을 담은 커밋은 항상 쓴다
- **trailer**: 마지막 문단에 `Issue: #{이슈 번호}`
- 메시지에는 저장소에서 바뀐 것만 적는다 — 작업 절차의 용어나 개인 작업 기록의 번호를 넣지 않는다
- ADR 등재 커밋: `docs(adr): ADR-NNNN {결정 요지}`

| 영역 | 대상 |
|---|---|
| `source` · `article` · `evaluation` · `digest` · `llm` · `credential` · `user` · `feedback` · `admin` · `platform` · `event` · `storyline` · `report` | 백엔드 모듈 ([overview.md §5](docs/overview.md#5-모듈-지도)) |
| `web` | 프론트엔드 |
| `adr` · `docs` · `qa` | ADR · 설계 문서 · 테스트 전략 |
| `deploy` | Docker · Compose · 배포 설정 |

## 5. PR

- **대상**은 `main`. 하나의 이슈에 PR 하나
- **제목**은 커밋 메시지 제목과 같은 형식: `{유형}({영역}): {요지}`
- **본문**은 [PR 템플릿](.github/pull_request_template.md)을 따른다 — 변경 요약, `Closes #{이슈 번호}`, 확인 사항. 리뷰어가 읽고 PR 에 남는 변경 기록이므로 저장소의 변경을 정리해 쓴다
- **CI**: PR 과 `main` 에서 GitHub Actions(`.github/workflows/ci.yml`)가 §1 의 검사를 돌리고, 결과가 체크 `backend` · `frontend` · `deploy` · `docs` 로 표시된다. **네 체크가 통과해야 merge 한다**
- **merge** 는 리드가 한다. 방식은 **merge commit** 만 쓴다(squash · rebase merge 를 쓰지 않는다 — 작업 브랜치의 커밋을 그대로 남긴다). merge 커밋 메시지는 저장소 설정이 만든다 — 제목 `Merge pull request #{번호} from {소유자}/{작업 브랜치}`, 본문은 PR 제목. merge 커밋은 [§4](#4-커밋-메시지) 형식을 따르지 않는다
- **리뷰**: 리드가 GitHub 에서 검토하고, 필요하면 로컬로 받아 확인한다(`gh pr checkout {번호}`)
- **충돌 · 동기화**: 작업 브랜치에 `main` 을 merge 해서 해소하고 다시 push 한다. 이 merge 커밋의 메시지는 `Merge origin/main into {작업 브랜치}` 이고, 마지막 문단에 `Issue: #{이슈 번호}` 를 둔다. force push 하지 않는다

## 6. 문서

- 문서는 종류 · 독자별 갈래로 나뉜다 — [docs/README.md §2 문서 목록](docs/README.md#2-문서-목록)
- 모듈의 공개 인터페이스 · 이벤트 · 포트 · 내부 구조(Spec 에 있는 구성 요소) · 작업 · 규칙 · 불변식을 바꾸면 그 모듈의 Spec 을 함께 고친다. Spec 의 구조와 쓰는 규칙은 [docs/specs/README.md](docs/specs/README.md)
- 변경이 어떤 문서 갱신을 요구하는지는 [docs/README.md §3 갱신 트리거](docs/README.md#3-갱신-트리거)가 정한다. 해당하는 문서 갱신은 그 PR 에 포함한다
- 작성 규칙(현재 상태만 쓰기 · 절 번호 유지 · ADR 수정 범위 · 상대 링크)은 [docs/README.md §4](docs/README.md#4-작성-규칙)
- ADR 은 [docs/adr/README.md](docs/adr/README.md)의 양식으로 쓰고, 번호는 모든 브랜치에서 가장 큰 번호 + 1 로 정한 뒤 바로 커밋한다

## 7. 코드

- 들여쓰기 · 줄바꿈 · 문자 인코딩은 [.editorconfig](.editorconfig)를 따른다
- 모듈 구조와 패턴은 [ADR-0013](docs/adr/0013-architecture-patterns.md), 데이터 접근은 [ADR-0014](docs/adr/0014-spring-data-jdbc.md)를 따른다
- 모듈의 공개 API(모듈 최상위 패키지의 타입)는 Javadoc 을 쓴다. 주석과 Javadoc 은 현재 동작만 설명하고, 이슈 번호에 기대지 않고 그 자리에서 이해되게 쓴다
- 변경 안에서 발견한 작은 정리(낡은 주석 · dead code · 중복)는 같은 PR 에서 끝낸다
- JobRunr · Spring Modulith 의 스키마는 라이브러리 원문을 Flyway 마이그레이션에 담는다([ADR-0023](docs/adr/0023-jobrunr-oss-framework-schema-and-event-resubmission.md)). 구획 규칙은 `V1__jobrunr_and_event_publication.sql` 머리 주석에 있다
  - JobRunr 를 올려 새 스크립트가 생기면 `FrameworkSchemaMigrationTests` 가 실패한다. 그 원문을 Flyway 새 버전 파일에 `-- jobrunr-migration:` 구획으로 옮기고, `-- jobrunr-migration-record` 구획에 기록 행을 더한다
  - Spring Modulith 를 올릴 때는 이벤트 발행 레지스트리의 스키마 판이 바뀌었는지 릴리스 노트로 확인한다. 테스트는 지금 쓰는 판의 원문만 대조하므로, 그 원문이 바뀌면 실패하지만 판이 바뀐 것은 알리지 못한다. 원문이 `CREATE TABLE IF NOT EXISTS` 라 새 원문을 옮겨도 이미 있는 테이블은 바뀌지 않는다 — 바뀐 부분을 손으로 쓴 마이그레이션으로 더하고, 대조 테스트의 Modulith 대조를 새 원문에 맞게 고친다
- 되돌릴 수 없는 스키마 변경은 두 번의 배포로 나눈다(확장 → 축소)
