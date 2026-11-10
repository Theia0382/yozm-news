# ADR-0104: arm64 실행은 arm64 러너의 배포 검사로 확인하고, 배포 검사는 아키텍처마다 대칭 job 으로 둔다

- 상태: 승인
- 날짜: 2026-11-10
- 근거: [이슈 #102](https://github.com/Theia0382/yozm-news/issues/102) — 배포 검사를 아키텍처별 job 둘로 나누고 스모크에 로컬 모델 추론 확인을 더한 구현(`29e834c`), 덧씌우기 · 토큰 · 요구 표지의 대조와 변이를 amd64 에서 실측
- 관련: [ADR-0100](0100-repository-tool-unit-tests-run-in-one-tools-check-service-and-docs-check-checks-links-only.md)(CI 체크 목록 — 부분 대체), [ADR-0022](0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md)(CI 의 빌더 확인 — arm64 에뮬레이션에서 상대 아키텍처 에뮬레이션으로, 부분 대체), [ADR-0006](0006-deployment-compose-cloudflare.md)(두 아키텍처 빌드 — 실행 확인을 더함, 보충), [ADR-0091](0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md)(arm64 실기 실행 미확인 — 보충)

## 맥락

- 배포 이미지는 `linux/amd64` · `linux/arm64` 로 빌드한다([ADR-0006](0006-deployment-compose-cloudflare.md)). CI 의 배포 검사 `deploy` 는 amd64 러너 하나에서 두 아키텍처를 빌드하고 amd64 로만 띄웠다. arm64 는 빌드까지만 확인됐다.
- 빌드의 가드는 에뮬레이션이 없는 빌더의 두 아키텍처 빌드다 — 실행 단계에 `RUN` 이 있으면 빌더와 다른 아키텍처의 빌드가 깨진다([ADR-0022](0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md)). 빌더에 에뮬레이션이 있으면 가드가 무력해진다.
- 기본 로컬 임베딩(ONNX Runtime · DJL 토크나이저)은 linux-aarch64 네이티브를 담지만, arm64 는 QEMU 에뮬레이션에서만 돌려 봤다([ADR-0091](0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md)). 네이티브가 실기에서 열리고 추론하는지는 확인되지 않았다.
- 셀프 호스팅 소프트웨어라 운영자의 호스트에 arm64(ARM VPS · 단일 보드 컴퓨터)가 흔하다([ADR-0047](0047-self-hosted-software-run-by-instance-operators.md)).
- GitHub Actions 는 공개 저장소에 arm64 러너(`ubuntu-24.04-arm`)를 둔다.
- 스모크(`deploy/smoke.sh`)에는 수집할 피드가 없어, 기사가 로컬 모델 표현까지 가는 경로를 지나지 않았다.

## 결정

- **배포 검사는 아키텍처마다 job 하나이고, 두 job 은 대칭이다.** `deploy-amd64` 는 amd64 러너, `deploy-arm64` 는 arm64 러너에서 돈다. 두 job 은 러너와 상대 아키텍처만 다르다. 한쪽의 실패가 다른 아키텍처의 검사를 취소하지 않는다.
- **각 job 은 두 아키텍처를 빌드하고, 빌드 전에 빌더에 상대 아키텍처의 에뮬레이션이 없음을 확인한다.** 실행 단계 `RUN` 의 가드가 두 방향(amd64 빌더의 arm64 빌드 · arm64 빌더의 amd64 빌드)으로 돈다.
- **각 job 은 자기 아키텍처 이미지로 배포 구성을 띄워 스모크를 돈다.** 실행 확인의 범위는 기동과 로컬 모델 추론 한 건이다 — 스모크가 시험 피드 서버를 얹은 구성에서 시험 소스를 등록 · 켜고, 시험 기사 하나가 수집 · 표현까지 가며 표현의 모델 버전이 저장소의 정본과 같은지 본다.
- **추론 확인은 시험 피드 서버와 운영자 토큰이 있을 때만 돈다.** 없으면 건너뛴다 — 운영 인스턴스에서 스모크를 돌려도 시험 소스를 등록하지 않는다. CI 는 요구 표지를 줘서 조건이 맞지 않으면 건너뛰지 않고 실패한다.
- **체크 이름은 검사하는 디렉터리 이름에 아키텍처를 붙인다.** CI 체크는 `backend` · `frontend` · `docs` · `tools` · `deploy-amd64` · `deploy-arm64` 다.

## 결과

- arm64 호스트에서 기본 배포 구성이 뜨고 로컬 모델이 추론하는지가 PR 마다 확인된다.
- 러너의 CPU 가 확인 범위다. 러너에 없는 명령어 조합의 오래된 arm64 코어에서 로컬 모델의 커널 갈래가 도는지는 확인하지 않는다.
- 두 job 이 각자 두 아키텍처를 빌드해 CI 시간과 빌드 캐시가 job 수만큼 든다. 캐시 범위는 job 마다 따로다.
- 필수 체크 목록을 `deploy` 에서 두 체크로 바꾸는 일은 저장소 설정이다.
- arm64 실행 확인은 공개 저장소의 arm64 러너에 기댄다. 러너를 쓸 수 없게 되면 이 결정을 다시 본다.
- 추론 확인은 피드 수집 · 사건 묶기 대기의 반복 작업을 기다리는 만큼 스모크를 늘인다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| amd64 러너 하나에서 QEMU 로 arm64 이미지도 띄운다 | 빌더에 에뮬레이션이 생겨 실행 단계 `RUN` 의 가드가 무력해지고([ADR-0022](0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md)), 에뮬레이션 실행은 이미 확인한 범위라 실기 확인을 대신하지 못한다 |
| arm64 지원을 철회하고 amd64 로만 빌드한다 | arm64 호스트를 쓰는 운영자를 뺀다. 두 아키텍처 빌드는 이미 돌고, 남은 빈칸은 러너로 메울 수 있다 |
| arm64 이미지는 빌드하되 "실행은 확인하지 않는다"고 밝힌다 | 실행의 불확실성을 운영자에게 넘긴다 |
| arm64 러너에만 실행 job 을 더하고 기존 `deploy` 는 그대로 둔다 | 두 job 의 정의가 달라지고, arm64 빌더 쪽의 `RUN` 가드(amd64 빌드)가 돌지 않는다 |
| 추론 확인을 언제나 돈다 | 운영 인스턴스에서 스모크를 돌리면 시험 소스가 등록된다 |
