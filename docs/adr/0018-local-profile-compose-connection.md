# ADR-0018: 로컬 개발의 DB 접속 정보는 개발용 Compose 컨테이너에서 읽는다

- 상태: 승인
- 날짜: 2026-10-26
- 근거: [이슈 #2](https://github.com/Theia0382/yozm-news/issues/2) — 기점 `bff9dec` 의 저장소 사본에 임시 Compose 파일을 두고 `bootRun` 으로 실측
- 관련: [ADR-0012](0012-swappable-external-dependencies.md)(개발 환경 · 로컬 대체물), [ADR-0017](0017-standard-datasource-env-vars.md)(운영의 DB 접속 정보)

## 맥락

- 로컬 개발은 Compose `dev` 프로필로 PostgreSQL 을 띄우고 백엔드를 Spring profile `local` 로 실행한다([ADR-0012](0012-swappable-external-dependencies.md)). `local` 에는 개발용 DB 에 붙을 접속 정보가 필요하다.
- 접속값(계정 · 비밀번호 · DB 이름 · 포트)을 `application-local.yaml` 에 적으면 같은 값이 Compose 파일과 두 곳에 생긴다. 계정 · 비밀번호만 환경 변수로 받으면 개발자마다 IDE 실행 구성에 값을 넣어야 한다.
- Spring Boot 의 Docker Compose 지원(`spring-boot-docker-compose`)은 Compose 파일의 PostgreSQL 컨테이너에서 환경 변수(`POSTGRES_USER` · `POSTGRES_PASSWORD` · `POSTGRES_DB`)와 공개 포트를 읽어 접속 정보를 만든다. 테스트가 Testcontainers 의 `@ServiceConnection` 으로 접속 정보를 받는 것과 같은 기제다.

## 결정

- `spring-boot-docker-compose` 를 Gradle `developmentOnly` 의존성으로 둔다 — `bootRun` 의 실행 클래스패스에는 들어가고, 실행 jar 와 테스트 클래스패스에는 들어가지 않는다.
- 기본 설정 파일에서 이 기능을 끄고(`spring.docker.compose.enabled: false`), `application-local.yaml` 에서만 켜 저장소 루트의 `compose.yaml` 과 Compose 프로필 `dev` 를 가리킨다. 경로는 실행 작업 디렉터리(`backend/`) 기준의 상대 경로다.
- 수명주기는 기본값을 쓴다 — Compose 서비스가 떠 있지 않으면 앱이 띄우고 앱이 멈출 때 멈춘다. 이미 떠 있으면 건드리지 않는다.
- `application-local.yaml` 에는 접속값을 두지 않는다. 개발용 계정 · 포트의 정본은 Compose 파일 하나다.

## 결과

- 개발용 접속값이 Compose 파일 한 곳에만 있다. `docker compose --profile dev up` 을 먼저 하지 않아도 `local` 실행이 Compose 를 띄운다.
- 앱 프로세스가 `docker` CLI 를 부를 수 있어야 한다. Compose 파일이 없거나 작업 디렉터리가 달라 파일을 못 찾으면 기동이 멈춘다.
- 이 기능이 켜져 있는 동안 `SPRING_DATASOURCE_*` 환경 변수는 오류 없이 무시된다. 자기 PostgreSQL 을 쓰려면 기능을 끄고(`SPRING_DOCKER_COMPOSE_ENABLED=false`) 표준 환경 변수를 준다.
- Compose 의 PostgreSQL 서비스는 공식 `postgres` 이미지(또는 서비스 연결 라벨)이고 `POSTGRES_PASSWORD` 를 가져야 접속 정보가 만들어진다. 개발용 비밀번호와 포트 공개 범위는 Compose 파일이 정한다.
- IDE 실행 구성이 이 의존성을 포함한 클래스패스와 작업 디렉터리 `backend/` 로 실행하는지는 재지 않았다 — Gradle `bootRun` 에서만 확인했다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `application-local.yaml` 에 개발용 URL · 계정 · 비밀번호를 리터럴로 둔다 | 같은 값이 Compose 파일과 두 곳에 생긴다. 포트를 고정해야 한다 |
| URL 만 기본값으로 두고 계정 · 비밀번호는 환경 변수로 받는다 | 개발자마다 IDE 실행 구성에 환경 변수를 넣는 절차가 생기고, URL 은 여전히 두 곳에 있다 |
| 개발용 Compose 파일을 `backend/compose.yaml` 에 두고 기본 탐색에 맡긴다 | 개발용 Compose 를 저장소 루트에서 `docker compose --profile dev up` 으로 띄우는 구성과 어긋난다 |
| 수명주기 `start-only`(앱이 멈춰도 Compose 를 멈추지 않음) | 기본값이 앱이 띄운 것만 멈추고 개발자가 먼저 띄운 것은 두므로, 두 사용 방식이 모두 자연스럽다 |
