# ADR-0021: 배포 구성은 개발용과 따로 둔 Compose 파일이고, 앱은 서비스 `app` 하나로 띄운다

- 상태: 승인
- 날짜: 2026-10-26
- 근거: [이슈 #5](https://github.com/Theia0382/yozm-news/issues/5) — 기점 `c119b1f` 의 `compose.yaml` 사본으로 Compose 보간 · `extends` · `!reset` 과 buildx bake 의 해석을 실측
- 관련: [ADR-0006](0006-deployment-compose-cloudflare.md)(배포 Compose), [ADR-0012](0012-swappable-external-dependencies.md)(Compose 프로필 구성), [ADR-0017](0017-standard-datasource-env-vars.md)(DB 접속 환경 변수), [ADR-0018](0018-local-profile-compose-connection.md)(로컬 실행의 Compose 연동), [ADR-0019](0019-dev-compose-per-checkout-and-test-image-source.md)(PostgreSQL 이미지 정본)

## 맥락

- [ADR-0012](0012-swappable-external-dependencies.md)는 개발용(`dev`)과 운영용(`prod`) 구성을 한 Compose 파일의 프로필로 나누기로 했다.
- 운영 값(DB 비밀번호 등)은 기본값 없이 받아야 한다. Compose 는 필수 변수(`${VAR:?}`)를 활성 프로필과 관계없이 파일 전체에서 보간한다. 같은 파일에 두면 `.env` 없이 도는 개발 흐름([ADR-0019](0019-dev-compose-per-checkout-and-test-image-source.md))과, 같은 파일을 읽는 `local` 실행의 Compose 연동([ADR-0018](0018-local-profile-compose-connection.md))이 함께 멈춘다. 실측에서 `--profile dev config` 가 운영 서비스의 필수 변수 때문에 실패했다.
- PostgreSQL 이미지의 정본은 개발용 `compose.yaml` 의 `postgres` 서비스다([ADR-0019](0019-dev-compose-per-checkout-and-test-image-source.md)).
- 앱은 역할(`web` · `worker`)을 Spring profile 로 나누는 이미지 하나다. 기본 구성은 두 역할을 함께 켠 프로세스 하나다(이슈 #6).

## 결정

- **배포 구성은 `deploy/compose.yaml` 하나다.** 운영 값은 그 디렉터리의 `.env` 에서 받고, 필수 값은 `${VAR:?메시지}` 로 받는다. 저장소 루트의 개발용 `compose.yaml` 과 `.env` 는 바꾸지 않는다.
- **`postgres` 는 개발용 `postgres` 서비스를 `extends` 로 물려받는다.** `!reset` 으로 프로필과 호스트 포트를 지운다. 이미지 정본은 개발용 파일 그대로다.
- **서비스는 `proxy` · `app` · `postgres` 다**(공개 · 백업 서비스 `cloudflared` · `backup` 은 그 기능을 만들 때 더한다). 앱 서비스는 두 역할을 켠 `app` 하나다. 역할을 나눠 띄우는 구성은 `web` · `worker` 두 서비스로 둔다.
- **호스트 포트는 `proxy` 만 공개한다.** 기본 바인딩은 루프백이고, 주소와 포트는 `.env` 로 바꾼다.
- **프로젝트 이름은 `yozmnews-deploy` 로 고정한다**(최상위 `name:`). 컨테이너 · 네트워크 · DB 볼륨 이름이 배포 디렉터리 이름과 무관해지고, checkout 디렉터리 이름을 따르는 개발용 프로젝트([ADR-0019](0019-dev-compose-per-checkout-and-test-image-source.md))와 겹치지 않는다. 한 호스트에서 두 벌을 띄울 때만 `COMPOSE_PROJECT_NAME` 으로 바꾼다.
- 실행은 저장소 루트에서 `docker compose -f deploy/compose.yaml …`, 또는 `deploy/` 에서 `docker compose …` 다. 프로필은 쓰지 않는다.

## 결과

- 운영 값이 빠지면 Compose 가 변수 이름을 말하며 멈춘다. 개발 흐름 · `local` 실행 · 테스트는 영향을 받지 않는다.
- 배포 호스트에는 `deploy/` 와 함께 `extends` 가 가리키는 루트 `compose.yaml` 이 같은 상대 위치에 있어야 한다. [ADR-0006](0006-deployment-compose-cloudflare.md) 결과의 "Compose 파일과 환경 변수만 옮기면 된다"보다 파일이 하나 많다. 이미지를 레지스트리에서 받는 배포로 옮길 때 다시 본다.
- 개발용 `postgres` 의 변경이 배포로 물려간다. PostgreSQL 18 부터 이미지는 데이터를 메이저별 경로에 두므로, 메이저를 바꾸면 배포 구성은 기존 데이터를 두고 빈 클러스터로 뜬다. 메이저 변경에는 배포 DB 이전 절차가 따른다.
- buildx bake 는 `extends` 대상 · 빌드 컨텍스트 · `.env` 를 작업 디렉터리 기준으로 읽는다(buildx v0.38.1 실측). 배포 이미지를 bake 로 빌드할 때는 `deploy/` 에서 실행한다.
- 프록시 업스트림 · 문서가 서비스 이름 `app` 을 인용한다.
- 배포 디렉터리를 옮기거나 이름을 바꿔도 같은 DB 볼륨(`yozmnews-deploy_postgres-data`)을 쓴다. 이 저장소의 여러 checkout 에서 배포 구성을 함께 띄우면 같은 프로젝트가 되어 나중 것이 먼저 띄운 컨테이너를 바꿔 띄운다. [ADR-0019](0019-dev-compose-per-checkout-and-test-image-source.md) 결과의 "운영 배포의 프로젝트 이름도 배포 디렉터리 이름을 따른다"는 운영 구성이 개발용과 같은 파일을 쓴다는 전제였고, 이 결정이 대체한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 같은 `compose.yaml` 에 프로필 `prod` 로 운영 서비스를 둔다 | 운영 값을 필수로 받으면 개발 흐름과 `local` 실행이 멈춘다. 피하려면 운영 비밀 값에 기본값을 두거나 비워 두어야 하고, 값이 빠졌을 때 Compose 가 아니라 PostgreSQL · 앱이 각자의 메시지로 실패한다. 필수 `env_file` 은 파일의 존재만 본다 |
| 루트 `compose.yaml` 을 배포 기본으로 하고 개발용을 다른 파일로 옮긴다 | `local` 실행이 읽는 파일 경로([ADR-0018](0018-local-profile-compose-connection.md))와 테스트가 읽는 PostgreSQL 이미지 정본([ADR-0019](0019-dev-compose-per-checkout-and-test-image-source.md))을 다시 연다 |
| 앱 서비스 이름을 `web` 으로 한다 | 기본 구성의 프로세스가 `worker` 역할도 해 이름과 하는 일이 어긋난다 |
| 프로젝트 이름을 `yozmnews` 로 고정한다 | 디렉터리 이름이 `yozmnews` 로 정규화되는 checkout 에서는 개발용 프로젝트와 같은 이름이 된다. 실측에서 배포 `up` 이 개발 DB 컨테이너를 바꿔 띄우고 개발 볼륨을 그대로 썼고(배포 비밀번호로는 접속 실패), 배포 쪽 `down -v` 가 개발 볼륨을 지웠다 |
| 프로젝트 이름을 두지 않는다(배포 디렉터리 이름 `deploy`) | 흔한 디렉터리 이름이라 같은 호스트의 다른 프로젝트와 겹칠 수 있다. 배포 파일을 다른 이름의 디렉터리로 옮겨 쓰면 운영 DB 가 빈 상태로 뜨고, 앱이 새 스키마를 만들어 정상처럼 보인다 |
