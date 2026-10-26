# 요즘뉴스 (Yozm News)

AI로 뉴스 기사를 수집·분류하고, 사용자마다 관심사에 맞춘 일일 다이제스트를 만들어 웹으로 보여주는 서비스입니다.

- 언론사 RSS에서 기사를 모으고, 결정 모델(Jev)로 주제·지역·형식 태그와 중요도를 매깁니다.
- 사용자가 등록한 LLM API 키(OpenAI, Anthropic)로 관심사에 맞춰 여러 기사를 종합한 다이제스트를 만듭니다.
- 셀프 호스팅을 전제로 하며, 외부 서비스는 설정으로 교체하거나 끌 수 있게 설계합니다.

> 현재 설계와 프로젝트 초기화 단계입니다. 아직 동작하는 기능은 없습니다.

## 저장소 구조

| 경로 | 내용 |
|---|---|
| [`docs/`](docs/README.md) | 설계 문서, 아키텍처 결정 기록(ADR), 테스트 전략, 조사 자료 |
| [`backend/`](backend/) | Spring Boot 애플리케이션 (Java 25, Gradle) |
| [`frontend/`](frontend/) | React SPA (TypeScript, Vite, pnpm) |
| [`api/`](api/) | 화면용 API 명세(OpenAPI) — 백엔드 코드에서 만들어 커밋하고, 프론트엔드가 여기서 타입을 생성한다 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 개발 환경, 이슈 · 브랜치 · 커밋 · PR 규약 |

## 개발

JDK 25, Docker, Node.js 24, pnpm 이 필요합니다. 저장소 루트에서 실행합니다.

```sh
backend/gradlew -p backend test               # 백엔드 테스트
pnpm -C frontend install && pnpm -C frontend dev  # 프론트엔드 개발 서버
```

개발 환경 설정과 기여 규약은 [`CONTRIBUTING.md`](CONTRIBUTING.md)에 있습니다.

## 문서

설계 문서는 [`docs/README.md`](docs/README.md)부터 읽으면 됩니다. 시스템 전체 그림은 [`docs/overview.md`](docs/overview.md)에 있습니다.
