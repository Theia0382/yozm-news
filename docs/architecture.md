# 모듈 인터페이스와 백그라운드 작업

아직 모듈이 없는 포트 · 백그라운드 작업과 외부 API · MCP 의 초안을 담는다. 시스템 구성 · 모듈 지도 · 기술 스택은 [overview.md](overview.md)에 있다. 모듈을 만드는 이슈가 그 모듈의 항목을 그 모듈 Spec([specs/](specs/README.md))으로 옮긴다.

## 핵심 인터페이스

`evaluation` 의 모델 평가 포트 `ArticleEvaluator` 와 기사 임베딩 포트 `ArticleEmbedder` 는 evaluation Spec 의 [`ArticleEvaluator`](specs/evaluation.md#articleevaluator) · [`ArticleEmbedder`](specs/evaluation.md#articleembedder)에, `tag` 의 평가 작업은 [tag Spec](specs/tag.md#평가-대기와-평가-작업)에, `event` 의 사건 묶기 작업은 [event Spec](specs/event.md#들어오는-쪽)에 있다.

```java
// digest: 사용자별 종합 다이제스트 생성.
public interface DigestGenerator {
    GeneratedDigest generate(DigestRequest request, LlmHandle llm); // 후보 + 사용자 프로필(자유 텍스트 관심사 포함)
}

// report: 사용자별 변화 리포트 생성.
public interface ReportGenerator {
    GeneratedReport generate(ReportRequest request, LlmHandle llm); // 기간 통계 + 구독한 스토리라인의 변화 + 직전 리포트 요약
}

// llm: 사용자 키로 호출 가능한 모델 핸들을 만든다. 키 평문은 이 모듈 밖으로 나가지 않는다.
public interface LlmClientFactory {
    LlmHandle forUser(UserId userId);                // 키 복호화는 내부에서, 호출 직전에만
    KeyValidationResult validate(Provider p, SecretValue key);
}
```

- `DigestGenerator`의 v1 구현체는 Spring AI `ChatClient` 기반 `LlmDigestGenerator`. 공급자별 차이는 Spring AI가 흡수한다.
- `ReportGenerator`의 v1 구현체는 `DigestGenerator` 와 같은 방식의 LLM 구현이고, Spring profile `local` 은 고정 응답 로컬 대체물을 쓴다 ([ADR-0025](adr/0025-long-term-personal-news-history-in-v1.md)).
- Spring AI의 `ChatModel`은 보통 애플리케이션 전역 빈으로 쓰지만, BYOK에서는 **요청마다 사용자 키로 인스턴스를 만든다**. `LlmClientFactory`가 이 차이를 감춘다.

## 주요 백그라운드 작업

| 작업 | 주기/계기 | 내용 |
|---|---|---|
| `ScheduleDueDigests` | 1분마다 | 생성 시각이 다가온 사용자의 다이제스트 생성 작업 등록 |
| `GenerateDigest(userId, date)` | 위에서 등록 / 사용자 수동 요청 | 후보 선별 → LLM 호출 → 검증 → 저장 |
| `ScheduleDueReports` | 1시간마다 | 리포트 시점이 된 사용자의 변화 리포트 생성 작업 등록 |
| `GenerateReport(userId, period)` | 위에서 등록 | 통계 집계 → 스냅숏 → LLM 호출 → 검증 → 저장 |
| `Retention` | 하루 1회 | 보존 기간이 지난 데이터 정리 ([data-model.md](data-model.md)) — 요약문(운영 설정을 켰을 때) 포함. 사건 배정 입력(기사 표현)은 `event` 의 사건 정리 작업이 지운다([event Spec](specs/event.md#사건-정리)) |

## 외부 API와 MCP (v1 이후)

- 경로를 분리한다: 화면용 `/api/**`(세션 쿠키), 외부용 `/public-api/v1/**`(Personal Access Token), MCP `/mcp`(OAuth 2.1).
- 화면용 API와 외부 API는 계약을 따로 관리한다. 외부 API는 하위 호환을 지켜야 하는 공개 계약이고, 화면용 API는 프론트엔드와 함께 자유롭게 바뀐다. 화면용 API 계약의 정본은 커밋된 OpenAPI 명세(`api/openapi.json`)이고, 프론트엔드 타입은 그 명세에서 생성한다 ([ADR-0020](adr/0020-screen-api-contract-from-committed-openapi-spec.md)).
- MCP 서버는 Spring AI의 MCP 서버 기능(Streamable HTTP)으로 구현하고, 내부적으로는 외부 API와 같은 서비스 계층을 호출한다. ChatGPT 등 원격 MCP 클라이언트 연결에는 OAuth 2.1 인가 서버(Spring Authorization Server)가 필요하다.
- 조회 대상: 다이제스트(주요 뉴스 · 추가 헤드라인), 기사 메타데이터 · 검색, 사건, 스토리라인과 타임라인, 관심사 히스토리, 변화 리포트. 사용자 데이터(다이제스트 · 스토리라인 구독 · 관심사 히스토리 · 리포트)는 토큰 주인의 범위로 한정한다.
- 외부에는 **다이제스트 · 리포트의 LLM 종합문, 기사 메타데이터, 원문 링크**만 제공한다. RSS 요약문 원문은 외부 API로 내보내지 않는다 ([security.md](security.md)).

## 모듈 이벤트

코드가 있는 모듈의 이벤트는 그 모듈 Spec 의 공개 인터페이스 절(§3)에, 모듈 사이의 이벤트 흐름은 [overview.md §5](overview.md#5-모듈-지도)에 있다.
