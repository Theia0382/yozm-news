# ADR-0022: 컨테이너 이미지는 측별 Dockerfile 하나에 검사 · 배포 target 으로 두고, 실행 단계는 명령 없이 조립한다

- 상태: 승인
- 날짜: 2026-10-26
- 근거: [이슈 #5](https://github.com/Theia0382/yozm-news/issues/5) — 기점 `c119b1f` 의 저장소에서 시제품 Dockerfile 로 에뮬레이션 없는 빌더의 교차 빌드 · 계층 캐시 · 비root 실행을 실측
- 관련: [ADR-0016](0016-containerized-checks.md)(검사 이미지 · 도구 버전 태그), [ADR-0006](0006-deployment-compose-cloudflare.md)(amd64 · arm64 이미지), [ADR-0021](0021-separate-deploy-compose-with-single-app-service.md)(배포 Compose)

## 맥락

- 배포 이미지는 빌드 단계가 필요하다 — 백엔드는 JDK + Gradle, 프론트엔드는 Node + pnpm. 검사 이미지([ADR-0016](0016-containerized-checks.md))가 같은 기반 이미지 태그 · 버전 대조 · 의존성 설치를 이미 갖고 있다. 배포용을 따로 정의하면 빌드 환경의 정의가 두 곳이 된다([ADR-0016](0016-containerized-checks.md) 결과).
- 이미지는 amd64 · arm64 로 빌드한다([ADR-0006](0006-deployment-compose-cloudflare.md)). 실측한 빌더에는 arm64 에뮬레이션이 없었다. 대상 플랫폼 단계에 명령(`RUN`)이 하나라도 있으면 arm64 빌드가 `exec format error` 로 실패했고, 빌드를 빌더 플랫폼에서 돌리고 대상 플랫폼 단계는 복사만 하면 두 아키텍처가 모두 빌드됐다.
- 실행 기반 이미지(`eclipse-temurin` JRE · `caddy`)는 기본 사용자가 root 다. 계정을 만들려면 대상 플랫폼 단계에 명령이 필요하다.

## 결정

- **측별 Dockerfile 하나다** — `backend/Dockerfile`(target `check` · `app`), `frontend/Dockerfile`(target `check` · `proxy`). 의존성 단계를 검사 target 과 배포 빌드가 공유한다. 기반 이미지 태그 · 버전 대조 · 의존성 설치는 이 파일에만 있다. `compose.check.yaml` 은 `target: check` 를 고른다.
- **빌드 단계는 `FROM --platform=$BUILDPLATFORM` 으로 빌더 플랫폼에서 돈다.** jar · 정적 파일은 아키텍처와 무관하다.
- **대상 플랫폼 단계(실행 단계)에는 `RUN` 을 두지 않는다.** `COPY` 와 메타데이터(`USER` · `EXPOSE` · `ENTRYPOINT` 등)만 둔다. 도구가 필요한 일(healthcheck 등)은 기반 이미지에 있는 도구로 한다.
- **우리가 만드는 이미지(`app` · `proxy`)는 숫자 uid `65534` 로 실행한다.** 계정을 만들지 않는다.

## 결과

- 검사를 통과한 빌드 환경과 배포 이미지를 만드는 빌드 환경이 같은 정의다. 배포 target 은 검사 이미지의 의존성 계층을 그대로 재사용한다.
- 에뮬레이션이 없는 빌더(기여자 머신 · CI)에서도 두 아키텍처가 빌드된다. 대신 실행 이미지에 패키지를 설치할 수 없다 — 필요해지면 그 도구가 든 기반 이미지를 고르거나 빌드 단계에서 만든 결과를 복사한다.
- 이 규칙의 가드는 에뮬레이션이 없는 빌더의 두 아키텍처 빌드다. 빌더에 에뮬레이션이 있으면 규칙 위반이 드러나지 않으므로, CI 는 빌더에 arm64 에뮬레이션이 없음을 먼저 확인한다.
- `<Dockerfile>.dockerignore` 는 Dockerfile 마다 하나라, 허용 목록이 검사 · 배포 두 용도의 합집합이다. 배포 단계가 복사하지 않는 파일은 배포 계층 캐시에 영향을 주지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 배포용 Dockerfile 을 따로 둔다 | 기반 이미지 태그 · 버전 대조 · 의존성 설치가 두 벌이 된다. 검사 통과와 배포 빌드 통과가 서로 다른 정의에 기댄다 |
| 배포 Dockerfile 을 따로 두고 빌드 단계를 검사 이미지 위에 올린다(bake `contexts` · Compose `additional_contexts`) | 배포 빌드가 테스트 소스 · 개발용 Compose 파일을 담은 검사 이미지에 묶여 캐시 무효화가 넓어지고, 두 Compose 정의를 잇는 참조가 생긴다 |
| 실행 단계에서 도구 설치 · 계정 생성(`RUN`) | 에뮬레이션 없는 빌더에서 arm64 빌드가 실패한다. 기여자 · CI 의 QEMU 구성에 기대게 된다 |
| 기반 이미지의 이름 있는 계정(`ubuntu` 등)으로 실행 | 기반 이미지의 OS 계정에 기대어, 이름이나 uid 가 바뀌면 깨진다 |
