# 모듈 인터페이스와 백그라운드 작업

모듈 Spec 이 생기기 전까지 핵심 포트 초안과 백그라운드 작업 목록을 담는다. 시스템 구성 · 모듈 지도 · 기술 스택은 [overview.md](overview.md)에 있다. 각 항목은 해당 모듈의 Spec(`specs/`)이 작성되면 그쪽으로 옮긴다.

## 핵심 인터페이스

```java
// source: 뉴스 공급원. v1 구현체는 RssArticleSource 하나.
public interface ArticleSource {
    FetchResult fetch(FeedDescriptor feed, FetchState previous); // feed 는 수집 프로필 포함, previous 는 ETag/Last-Modified
}

// article: 본문 추출. v1은 NoopContentExtractor. 소스별로 켤 수 있다.
public interface ContentExtractor {
    Optional<ExtractedContent> extract(ArticleRef article);
}

// evaluation: 공유 평가. 사용자 정보를 받지 않는다.
public interface ArticleEvaluator {
    EvaluatorInfo info();                                        // 이름, 모델 버전
    List<Evaluation> evaluate(List<EvaluationInput> inputs, Taxonomy taxonomy);
}

// digest: 사용자별 종합 다이제스트 생성.
public interface DigestGenerator {
    GeneratedDigest generate(DigestRequest request, LlmHandle llm); // 후보 + 사용자 관심사
}

// llm: 사용자 키로 호출 가능한 모델 핸들을 만든다. 키 평문은 이 모듈 밖으로 나가지 않는다.
public interface LlmClientFactory {
    LlmHandle forUser(UserId userId);                // 키 복호화는 내부에서, 호출 직전에만
    KeyValidationResult validate(Provider p, SecretValue key);
}
```

- `ArticleEvaluator`의 v1 구현체는 `JevArticleEvaluator`. Jev 호출 경로(직접 API 또는 AI Gateway)는 설정으로 바꾼다.
- `DigestGenerator`의 v1 구현체는 Spring AI `ChatClient` 기반 `LlmDigestGenerator`. 공급자별 차이는 Spring AI가 흡수한다.
- Spring AI의 `ChatModel`은 보통 애플리케이션 전역 빈으로 쓰지만, BYOK에서는 **요청마다 사용자 키로 인스턴스를 만든다**. `LlmClientFactory`가 이 차이를 감춘다.

## 주요 백그라운드 작업

| 작업 | 주기/계기 | 내용 |
|---|---|---|
| `PollDueFeeds` | 1분마다 | `next_poll_at`이 지난 피드를 골라 수집 작업 등록 |
| `PollFeed(feedId)` | 위에서 등록 | 조건부 GET, 파싱, 정규화, 기사 저장 |
| `EvaluatePendingArticles` | 1분마다 + `ArticleStored` | 평가 대기 기사를 묶어서 Jev 호출 |
| `ScheduleDueDigests` | 1분마다 | 생성 시각이 다가온 사용자의 다이제스트 생성 작업 등록 |
| `GenerateDigest(userId, date)` | 위에서 등록 / 사용자 수동 요청 | 후보 선별 → LLM 호출 → 검증 → 저장 |
| `FeedHealthCheck` | 1시간마다 | 연속 실패·장기 무소식 피드 표시, 운영자 알림 |
| `Retention` | 하루 1회 | 보존 기간이 지난 데이터 정리 ([data-model.md](data-model.md)) |

## 외부 API와 MCP (v1 이후)

- 경로를 분리한다: 화면용 `/api/**`(세션 쿠키), 외부용 `/public-api/v1/**`(Personal Access Token), MCP `/mcp`(OAuth 2.1).
- 화면용 API와 외부 API는 계약을 따로 관리한다. 외부 API는 하위 호환을 지켜야 하는 공개 계약이고, 화면용 API는 프론트엔드와 함께 자유롭게 바뀐다. 화면용 API 계약의 정본은 커밋된 OpenAPI 명세(`api/openapi.json`)이고, 프론트엔드 타입은 그 명세에서 생성한다 ([ADR-0020](adr/0020-screen-api-contract-from-committed-openapi-spec.md)).
- MCP 서버는 Spring AI의 MCP 서버 기능(Streamable HTTP)으로 구현하고, 내부적으로는 외부 API와 같은 서비스 계층을 호출한다. ChatGPT 등 원격 MCP 클라이언트 연결에는 OAuth 2.1 인가 서버(Spring Authorization Server)가 필요하다.
- 외부에는 **다이제스트 요약문, 기사 메타데이터, 원문 링크**만 제공한다. RSS 요약문 원문은 외부 API로 내보내지 않는다 ([security.md](security.md)).
