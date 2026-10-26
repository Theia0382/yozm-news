# ADR-0019: 개발용 Compose 는 checkout 마다 따로 뜨고, 테스트의 PostgreSQL 이미지는 그 파일에서 읽는다

- 상태: 승인
- 날짜: 2026-10-26
- 근거: [이슈 #3](https://github.com/Theia0382/yozm-news/issues/3) — 기점 `3361638` 의 worktree 에 임시 Compose 파일을 두고 `bootRun` · `docker compose` 로 실측
- 관련: [ADR-0012](0012-swappable-external-dependencies.md)(개발 환경 · 로컬 대체물), [ADR-0018](0018-local-profile-compose-connection.md)(로컬 DB 접속 정보), [ADR-0004](0004-tech-stack.md)(테스트 DB 는 Testcontainers), [ADR-0016](0016-containerized-checks.md)(컨테이너 검사)

## 맥락

- 로컬 개발은 저장소 루트 `compose.yaml` 의 Compose 프로필 `dev` 로 PostgreSQL 과 mock OIDC 서버를 띄운다([ADR-0012](0012-swappable-external-dependencies.md) · [ADR-0018](0018-local-profile-compose-connection.md)). 테스트는 Testcontainers 로 PostgreSQL 컨테이너를 띄운다. 두 곳은 같은 PostgreSQL 버전을 써야 하는데, 이미지를 양쪽에 적으면 같은 값이 두 곳에 생긴다.
- Compose 는 Java · Gradle 의 값을 읽지 못한다. 테스트 쪽은 파일을 읽을 수 있다.
- 이슈마다 git worktree 를 두고 병렬로 일하고, 팀원은 저장소를 다른 디렉터리 이름으로 clone 할 수 있다. Compose 프로젝트 이름을 파일에 고정하면 모든 checkout 이 컨테이너 · 볼륨을 함께 쓴다.
- 같은 `compose.yaml` 에 나중에 운영 구성이 더해진다. 최상위 `name:` 과 `.env` 는 파일 전체에 걸린다.

## 결정

- PostgreSQL 이미지의 정본은 `compose.yaml` 의 `postgres` 서비스 `image` 하나다. 테스트 설정이 그 값을 읽어 Testcontainers 에 넘긴다. 파일 경로는 Gradle 이 테스트에 시스템 속성으로 넘기고, 같은 파일을 테스트 태스크 입력으로 등록한다. 백엔드 검사 이미지에 `compose.yaml` 을 넣는다.
- 최상위 `name:` 을 두지 않는다. 프로젝트 이름은 Compose 파일이 있는 디렉터리 이름이고, checkout 마다 컨테이너 · 네트워크 · 볼륨이 따로 생긴다.
- 개발용 값의 기본값은 `compose.yaml` 에 둔다. `.env` 는 없어도 되고, 바꿀 값만 적는다(`.env.example`).

## 결과

- PostgreSQL 버전을 바꾸는 곳은 `compose.yaml` 한 줄이다. 그 파일만 바뀌어도 테스트가 다시 돈다.
- 테스트는 Gradle 로 실행한다. IDE 의 JUnit 실행은 Gradle 에 위임해야 하고, 그렇지 않으면 PostgreSQL 컨테이너를 띄우는 테스트가 시스템 속성이 없다는 메시지로 실패한다.
- `postgres` 서비스의 `image` 는 보간식 없는 리터럴이어야 한다. 서비스 이름을 바꾸거나 `image` 를 변수로 바꾸면 테스트가 실패한다.
- 브랜치마다 개발 DB 가 따로라 마이그레이션 이력이 섞이지 않는다. 두 checkout 을 함께 띄우면 고정 호스트 포트(mock OIDC)가 겹쳐 나중 것이 실패하므로 `.env` 로 포트를 바꾼다. checkout 을 지워도 그 프로젝트의 볼륨이 남으므로 `docker compose --profile dev down -v` 로 정리한다.
- clone 직후 `.env` 없이 `local` 실행이 된다. 운영 구성이 같은 파일과 `.env` 를 쓰게 되면, 기본값 없이 받아야 하는 운영 값과 선택인 개발 값을 함께 다루는 방식은 그때 정한다. 운영 배포의 프로젝트 이름도 배포 디렉터리 이름을 따른다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 테스트와 Compose 에 이미지를 따로 적고 일치를 검사한다 | 두 번째 사본이 남는다. 일치 검사도 Compose 파일을 읽어야 해 더 단순하지 않다 |
| 공통 Dockerfile 을 Compose `build` 와 Testcontainers 가 함께 빌드한다 | 테스트마다 이미지를 빌드하고, 공식 이미지 이름이 아니어서 Boot 의 서비스 인식에 라벨이 따로 필요하다 |
| 테스트도 Docker Compose 지원으로 DB 를 띄운다 | 테스트 클래스패스에서 뺀 Docker Compose 지원([ADR-0018](0018-local-profile-compose-connection.md))과 Testcontainers · DinD 검사 구조([ADR-0016](0016-containerized-checks.md))를 다시 연다 |
| `.env` 변수로 버전을 주고 양쪽이 읽는다 | `.env` 는 저장소에 없다. 기본값을 Compose 에 두면 결국 Compose 파일이 정본이다 |
| 테스트 코드가 `../compose.yaml` 상대 경로를 직접 연다 | 실행 작업 디렉터리에 따라 경로가 어긋나고, 경로가 테스트 코드와 Gradle 입력 선언 두 곳에 생긴다 |
| 고정 프로젝트 이름(머신당 개발 환경 하나) | 먼저 띄운 앱이 끝나면 다른 checkout 앱의 DB 가 멈추고, 브랜치 간 마이그레이션 이력이 한 DB 에 섞인다. 이름이 운영 구성에도 걸린다 |
| `.env` 필수(`${VAR:?}`) | 첫 실행 전에 복사 절차가 생겨, 외부 계정이나 비밀 값 없이 바로 띄우는 개발 환경([ADR-0012](0012-swappable-external-dependencies.md))에 한 단계가 더해진다 |
