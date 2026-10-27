# 아키텍처 결정 기록 (ADR)

결정 하나에 파일 하나. 결정을 바꿀 때는 기존 파일을 고치지 않고, 새 ADR을 만들어 이전 것을 `대체됨`으로 표시한다.

| 번호 | 제목 | 상태 |
|---|---|---|
| [0001](0001-news-source-rss.md) | 뉴스 소스: 네이버 검색 API 대신 운영자 큐레이션 RSS | 승인 |
| [0002](0002-shared-evaluation-with-jev.md) | Jev는 사용자 무관 공유 평가에만 사용 | 승인 (다중 카테고리 부분은 0010이 대체) |
| [0003](0003-byok-personalized-digest.md) | 사용자 BYOK LLM으로 개인화 종합 다이제스트 생성 | 승인 |
| [0004](0004-tech-stack.md) | 백엔드 기술 스택 | 승인 (데이터 접근은 0014가 대체) |
| [0005](0005-frontend-react-spa.md) | 프론트엔드: React SPA, 단일 오리진 제공 | 승인 |
| [0006](0006-deployment-compose-cloudflare.md) | 배포: Docker Compose + Cloudflare Tunnel | 승인 (서비스 목록의 `web` · `worker` 는 0021이 대체) |
| [0007](0007-defer-event-clustering.md) | 같은 사건 묶기는 v1에서 LLM 종합에 맡김 | 제안 |
| [0008](0008-v1-llm-providers.md) | v1 지원 LLM 공급자는 OpenAI(ChatGPT)와 Anthropic(Claude) | 승인 |
| [0009](0009-authentication-google-and-local.md) | v1 인증은 Google 로그인과 자체(이메일·비밀번호) 로그인 | 승인 (메일 관련 부분은 0011이 대체) |
| [0010](0010-tag-taxonomy.md) | 태그 체계: 축 분리 + 통제 어휘 다중 태그 | 승인 |
| [0011](0011-no-email-in-v1.md) | v1에서는 메일 발송을 하지 않는다 | 승인 |
| [0012](0012-swappable-external-dependencies.md) | 외부 의존성은 교체 가능하게, 개발 환경에는 로컬 대체물을 둔다 | 승인 (Compose 프로필 `prod` 부분은 0021이 대체) |
| [0013](0013-architecture-patterns.md) | 아키텍처 패턴: 모듈러 모놀리스 + 실용적 헥사고날 | 승인 (이벤트 재처리 시점은 0023이 대체) |
| [0014](0014-spring-data-jdbc.md) | 데이터 접근은 Spring Data JDBC + JdbcClient | 승인 |
| [0015](0015-source-presets-and-collection-profiles.md) | 뉴스 소스는 수집 프로필로 등록하고, 주요 언론사는 프리셋으로 제공한다 | 승인 |
| [0016](0016-containerized-checks.md) | 검사는 컨테이너에서 돌리고, CI 와 로컬이 같은 Compose 정의를 쓴다 | 승인 |
| [0017](0017-standard-datasource-env-vars.md) | DB 접속 정보는 Spring 표준 환경 변수로 받는다 | 승인 |
| [0018](0018-local-profile-compose-connection.md) | 로컬 개발의 DB 접속 정보는 개발용 Compose 컨테이너에서 읽는다 | 승인 |
| [0019](0019-dev-compose-per-checkout-and-test-image-source.md) | 개발용 Compose 는 checkout 마다 따로 뜨고, 테스트의 PostgreSQL 이미지는 그 파일에서 읽는다 | 승인 (운영 배포의 프로젝트 이름 부분은 0021이 대체) |
| [0020](0020-screen-api-contract-from-committed-openapi-spec.md) | 화면용 API 계약은 커밋된 OpenAPI 명세가 정본이고, 프론트엔드 타입은 그 명세에서 생성한다 | 승인 |
| [0021](0021-separate-deploy-compose-with-single-app-service.md) | 배포 구성은 개발용과 따로 둔 Compose 파일이고, 앱은 서비스 `app` 하나로 띄운다 | 승인 |
| [0022](0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md) | 컨테이너 이미지는 측별 Dockerfile 하나에 검사 · 배포 target 으로 두고, 실행 단계는 명령 없이 조립한다 | 승인 |
| [0023](0023-jobrunr-oss-framework-schema-and-event-resubmission.md) | 백그라운드 작업은 JobRunr OSS 로 돌리고, 프레임워크 스키마는 라이브러리 원문을 Flyway 에 담으며, 실패한 이벤트는 반복 작업이 상한까지 다시 처리한다 | 승인 |

양식:

```markdown
# ADR-NNNN: 제목
- 상태: 제안 | 승인 | 대체됨(ADR-XXXX)
- 날짜: YYYY-MM-DD

## 맥락
## 결정
## 결과
## 검토한 대안
```
