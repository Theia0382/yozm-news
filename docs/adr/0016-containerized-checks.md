# ADR-0016: 검사는 컨테이너에서 돌리고, CI 와 로컬이 같은 Compose 정의를 쓴다

- 상태: 승인. 검사 서비스 목록은 [ADR-0035](0035-public-markdown-links-and-anchors-checked-by-separate-check-service.md)가 대체
- 날짜: 2026-10-25
- 근거: [이슈 #1](https://github.com/Theia0382/yozm-news/issues/1) — 기점 `6cd2477` 의 저장소 사본에서 시제품으로 실측
- 관련: [ADR-0004](0004-tech-stack.md)(빌드 · 테스트 도구), [ADR-0006](0006-deployment-compose-cloudflare.md)(배포 Compose)

## 맥락

- 기여자마다 OS · JDK 공급사 · Node 패치 · 시간대가 다르다(Linux · Windows · macOS). 호스트에서 돌린 검사는 "로컬 통과 ≠ CI 통과"가 될 수 있다.
- 백엔드 테스트는 Testcontainers 로 PostgreSQL 을 띄운다. 검사를 컨테이너 안에서 돌리면서 호스트 Docker 소켓을 공유하면, 엔진에 닿는 방법이 호스트 종류마다 다르다 — rootless Linux 는 소켓 경로 지정과 host 네트워크가 필요하고, Docker Desktop 은 `host.docker.internal` 지정이나 계정 로그인 + host 네트워크 설정이 필요하다. rootless Linux 에서는 기본 설정으로 실패했다.
- GitHub Actions 의 컨테이너 job 은 host 네트워크를 쓸 수 없고, 이미지를 레지스트리에서 받아야 한다.

## 결정

- **검사 정의는 저장소 루트의 `compose.check.yaml` 하나다.** 서비스는 `backend` · `frontend` · `docker`(DinD). CI(GitHub Actions)와 로컬이 같은 정의를 `docker compose` 명령으로 부른다. 검사에 필요한 호스트 도구는 Docker 뿐이다. CI 는 같은 정의로 이미지를 먼저 빌드해 계층 캐시를 붙인다.
- **소스는 이미지에 복사한다.** 작업 트리를 bind mount 하지 않는다. 빌드 컨텍스트는 저장소 루트이고, 검사 이미지마다 허용 목록 `.dockerignore` 로 필요한 파일만 넣는다 — 비공개 설정 · 작업 산출물 · 비밀 값 파일은 들어가지 않는다.
- **Testcontainers 는 정의 안의 DinD 엔진을 쓴다.** 호스트 Docker 소켓과 host 네트워크를 쓰지 않는다. DinD 는 privileged 로 돌고, 공개 포트 없이 정의의 내부 네트워크에만 열린다.
- **도구 버전은 공식 이미지 태그가 갖는다.** `eclipse-temurin:{메이저}-jdk` · `node:{메이저}` 태그는 Gradle toolchain · `frontend/.nvmrc` 의 사본이고, 어긋나면 이미지 빌드가 실패한다. pnpm 은 corepack 이 `packageManager` 를 읽어 받는다(사본 없음). 베이스 이미지는 메이저 태그로 고정한다.
- 개발 서버 · IDE 는 컨테이너로 옮기지 않는다 — 호스트에서 돈다.

## 결과

- 검사 결과가 호스트의 OS · 도구 버전 · 시간대 · 로캘 · Docker 구성(rootful · rootless · Docker Desktop)에 따라 달라지지 않는다. 버전 정본과 이미지 태그가 어긋나면 크게 실패한다.
- privileged 컨테이너를 허용하지 않는 Docker 환경에서는 백엔드 검사를 돌릴 수 없다.
- 호스트에서 직접 돌릴 때보다 느리다 — 이미지 빌드와, DinD 안의 PostgreSQL 이미지 내려받기가 더해진다. 로컬은 DinD 데이터 볼륨으로, CI 는 GitHub Actions 캐시로 줄인다.
- 검사 결과 보고서는 작업 트리에 생기지 않는다. 멈춘 컨테이너에서 꺼낸다(`docker compose cp`).
- 이미지에 넣을 파일을 `.dockerignore` 가 정하므로, 저장소의 무시 규칙(`.gitignore`)이 바뀌면 함께 맞춘다.
- 배포용 백엔드 이미지가 빌드 단계(JDK + Gradle)를 두면, 백엔드를 빌드하는 JDK 환경의 정의가 검사 이미지와 두 곳이 될 수 있다 — 그 이미지를 설계할 때 함께 맞춘다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 러너에 도구를 설치하고 호스트에서 검사(setup action) | 로컬과 CI 의 환경이 다르다 — 이 결정이 없애려는 차이가 그대로 남는다 |
| GitHub Actions 컨테이너 job | 이미지 · 옵션이 워크플로와 로컬에 따로 생긴다. host 네트워크를 쓸 수 없고, 검사 전용 이미지는 먼저 레지스트리에 게시해야 한다 |
| 작업 트리 bind mount | rootful Linux 에서 산출물이 root 소유로 남고, Windows 에서는 느리며, 플랫폼별 `node_modules` 를 따로 떼야 한다 — 차이가 호스트마다 다르게 나타난다 |
| 호스트 Docker 소켓 공유(형제 컨테이너) | 호스트 종류마다 다른 설정을 기여자에게 넘긴다. 호스트 Docker 의 전체 권한을 넘긴다 |
| 이미지가 버전 정본 파일을 읽어 도구를 설치 | 사본은 없어지지만 설치 스크립트 · Gradle toolchain 내려받기 설정이 더해지고, 호스트 Gradle 에도 내려받기 설정이 적용된다 |
| 버전 정본을 이미지 정의로 옮김 | 호스트 도구(IDE · nvm)가 읽는 정본을 바꿔야 한다 |
