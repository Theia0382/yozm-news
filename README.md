# 요즘뉴스 (Yozm News)

AI로 뉴스 기사를 수집·분류하고, 사용자마다 관심사에 맞춘 일일 다이제스트를 만들어 웹으로 보여주는 서비스입니다.

- 언론사 RSS에서 기사를 모으고, 결정 모델(Jev)로 주제·지역·형식 태그와 중요도를 매깁니다.
- 사용자가 등록한 LLM API 키(OpenAI, Anthropic)로 관심사에 맞춰 여러 기사를 종합한 다이제스트를 만듭니다.
- 셀프 호스팅을 전제로 하며, 외부 서비스는 설정으로 교체하거나 끌 수 있게 설계합니다.

> 현재 설계와 프로젝트 초기화 단계입니다. 아직 동작하는 기능은 없습니다.

## 저장소 구조

| 경로 | 내용 |
|---|---|
| [`docs/`](docs/) | 설계 문서, 아키텍처 결정 기록(ADR), 조사 자료 |
| [`backend/`](backend/) | Spring Boot 애플리케이션 (Java 25, Gradle) |

## 개발

### 필요한 것

- JDK 25
- Docker (테스트에서 Testcontainers로 PostgreSQL을 띄웁니다)

### 백엔드 테스트

```sh
cd backend
./gradlew test
```

## 문서

설계 문서는 [`docs/README.md`](docs/README.md)부터 읽으면 됩니다.
