# 모듈 인터페이스와 백그라운드 작업

모듈 Spec 이 생기기 전까지 핵심 포트 초안과 백그라운드 작업 목록을 담는다. 시스템 구성 · 모듈 지도 · 기술 스택은 [overview.md](overview.md)에 있다. 각 항목은 해당 모듈의 Spec(`specs/`)이 작성되면 그쪽으로 옮긴다.

## 핵심 인터페이스

```java
// source: 뉴스 공급원. 모든 피드 수집(정기 수집 · 활성화 미리보기)이 거친다. v1 구현체는 RssArticleSource 하나.
public interface ArticleSource {
    FetchResult fetch(FeedDescriptor feed, FetchState previous); // feed = 피드 URL + 수집 프로필, previous = ETag/Last-Modified
}   // FetchResult: Fetched(정규화 결과 · 새 상태 · 받은 시각 · 영구 이동한 주소) | NotModified(상태 · 시각 · 영구 이동한 주소) | Failed(이유 · 일시/영구 · 실패한 시각)

// article: 본문 추출. v1은 NoopContentExtractor. 소스별로 켤 수 있다.
public interface ContentExtractor {
    Optional<ExtractedContent> extract(ArticleRef article);
}

// evaluation: 공유 평가. 사용자 정보를 받지 않는다. Jev 를 부르는 유일한 포트다.
public interface ArticleEvaluator {
    EvaluatorInfo info();                                        // 이름, 모델 버전
    List<Evaluation> evaluate(List<EvaluationInput> inputs, Taxonomy taxonomy);
    List<MembershipJudgement> judgeEventMembership(EvaluationInput article, List<EventDescriptor> candidates); // 애매한 구간의 사건 소속 판정
}

// event: 사건 묶기용 기사 임베딩. 사용자 정보를 받지 않는다.
public interface ArticleEmbedder {
    EmbedderInfo info();                                         // 이름, 모델 버전, 차원
    List<Embedding> embed(List<EmbeddingInput> inputs);          // 제목 + 요약문 앞부분(없으면 제목만)
}

// digest: 사용자별 종합 다이제스트 생성.
public interface DigestGenerator {
    GeneratedDigest generate(DigestRequest request, LlmHandle llm); // 후보 + 자유 텍스트 관심사 + 진행 중인 이슈
}

// report: 사용자별 변화 리포트 생성.
public interface ReportGenerator {
    GeneratedReport generate(ReportRequest request, LlmHandle llm); // 기간 통계 + 이슈 변화 + 직전 리포트 요약
}

// llm: 사용자 키로 호출 가능한 모델 핸들을 만든다. 키 평문은 이 모듈 밖으로 나가지 않는다.
public interface LlmClientFactory {
    LlmHandle forUser(UserId userId);                // 키 복호화는 내부에서, 호출 직전에만
    KeyValidationResult validate(Provider p, SecretValue key);
}
```

- `ArticleEvaluator`의 v1 구현체는 `JevArticleEvaluator`. Jev 호출 경로(직접 API 또는 AI Gateway)는 설정으로 바꾼다. 사건 소속 판정의 인자 · 반환 타입(`EventDescriptor` · `MembershipJudgement`)은 `evaluation` 이 소유한다 — `event` 의 타입을 받으면 모듈 순환이 된다. `event` 는 `evaluation` 의 공개 API 를 거쳐 판정을 요청한다 ([ADR-0026](adr/0026-event-clustering-with-local-embeddings-and-jev.md)).
- `ArticleEmbedder`의 v1 구현체는 로컬 모델을 ONNX Runtime 으로 돌리는 구현이고, 설정으로 외부 임베딩 API 구현으로 바꿀 수 있다. 개발 · 테스트는 고정 규칙 기반 로컬 대체물을 쓴다.
- `ArticleSource`의 v1 구현체는 Apache HttpClient 로 피드를 받아 정규화로 넘기는 `RssArticleSource`. 입출력 실패도 결과(`Failed`)로 돌려준다. 내부 주소 차단 · 요청 제한은 이 구현이 건다 ([ADR-0031](adr/0031-feed-request-blocks-internal-addresses-with-apache-httpclient-deadline.md)).
- `DigestGenerator`의 v1 구현체는 Spring AI `ChatClient` 기반 `LlmDigestGenerator`. 공급자별 차이는 Spring AI가 흡수한다.
- `ReportGenerator`의 v1 구현체는 `DigestGenerator` 와 같은 방식의 LLM 구현이고, Spring profile `local` 은 고정 응답 로컬 대체물을 쓴다 ([ADR-0025](adr/0025-long-term-personal-news-history-in-v1.md)).
- Spring AI의 `ChatModel`은 보통 애플리케이션 전역 빈으로 쓰지만, BYOK에서는 **요청마다 사용자 키로 인스턴스를 만든다**. `LlmClientFactory`가 이 차이를 감춘다.

## 주요 백그라운드 작업

| 작업 | 주기/계기 | 내용 |
|---|---|---|
| `PollDueFeeds` | 1분마다 | 소스 · 피드가 켜졌고 `next_poll_at` 이 지난 피드를 잠그고 점유를 기록한 뒤, 점유 토큰을 작업 id 로 피드마다 수집 작업을 예약 등록. 같은 소스의 피드는 예약 시각을 띄운다. 프로필에서 피드를 얻지 못하면 미룬다 ([pipeline.md](pipeline.md#수집-동작) · [ADR-0033](adr/0033-feed-polling-db-lease-per-feed-new-items-and-per-source-request-spacing.md)) |
| `PollFeed(feedId, 점유 토큰)` | 위에서 등록 | 점유 확인 → 같은 소스의 요청 시작 시각 예약 · 대기 → 트랜잭션 밖에서 조건부 GET · 파싱 · 정규화 → 한 트랜잭션에서 수집 상태 반영과 새 항목(그 피드의 직전 성공 수집에 없던 항목)마다 `ArticleFetched` 발행. 기사 저장은 `article`. 점유가 자기 것이 아니면 아무것도 하지 않고, 재시도하지 않는다 |
| `EvaluatePendingArticles` | 1분마다 + `ArticleStored` | 평가 대기 기사를 묶어서 Jev 호출 |
| `AssignArticleToEvent` | `ArticleStored` | 기사 표현(임베딩 · 고유명사) → 사건 후보 → 판정 → 배정 또는 새 사건 ([pipeline.md](pipeline.md) §8) |
| `MergeEvents` | 주기 | 가까운 열린 사건끼리 합친다 |
| `CloseEvents` | 하루 1회 | 새 기사가 일정 기간 없는 사건을 닫는다 |
| `ScheduleDueDigests` | 1분마다 | 생성 시각이 다가온 사용자의 다이제스트 생성 작업 등록 |
| `GenerateDigest(userId, date)` | 위에서 등록 / 사용자 수동 요청 | 후보 선별 → LLM 호출 → 검증 → 저장 → 이슈(`storyline`) 연결 적용 |
| `MarkDormantStorylines` | 하루 1회 | 새 연결이 기준 일수 동안 없는 이슈를 휴면으로 바꾼다. 마지막 연결 날짜는 `digest` 의 타임라인에서 오고, 전이는 `storyline` API 로 한다. 이 작업을 어느 모듈에 둘지는 구현에서 정한다 — 어느 쪽이든 `storyline` 이 `digest` 를 읽어서는 안 된다 |
| `ScheduleDueReports` | 1시간마다 | 리포트 시점이 된 사용자의 변화 리포트 생성 작업 등록 |
| `GenerateReport(userId, period)` | 위에서 등록 | 통계 집계 → 스냅숏 → LLM 호출 → 검증 → 저장 |
| `FeedHealthCheck` | 1시간마다 | 연속 실패·장기 무소식 피드 표시, 운영자 알림 |
| `Retention` | 하루 1회 | 보존 기간이 지난 데이터 정리 ([data-model.md](data-model.md)) — 사건 배정 입력, 요약문(운영 설정을 켰을 때) 포함 |
| `EventResubmissionJob` (반복 작업 ID `event-publication-resubmission`) | 5분마다 | 리스너가 실패한 이벤트 발행을 다시 처리한다. 한 발행의 총 처리 시도는 6회까지이고, 넘긴 발행은 실패로 남겨 실행마다 경고 로그로 알린다. 처리 도중 멈춘 발행은 Modulith 감시가 발행 30분 뒤 실패로 바꿔 이 작업이 고른다 ([ADR-0023](adr/0023-jobrunr-oss-framework-schema-and-event-resubmission.md)) |

## 외부 API와 MCP (v1 이후)

- 경로를 분리한다: 화면용 `/api/**`(세션 쿠키), 외부용 `/public-api/v1/**`(Personal Access Token), MCP `/mcp`(OAuth 2.1).
- 화면용 API와 외부 API는 계약을 따로 관리한다. 외부 API는 하위 호환을 지켜야 하는 공개 계약이고, 화면용 API는 프론트엔드와 함께 자유롭게 바뀐다. 화면용 API 계약의 정본은 커밋된 OpenAPI 명세(`api/openapi.json`)이고, 프론트엔드 타입은 그 명세에서 생성한다 ([ADR-0020](adr/0020-screen-api-contract-from-committed-openapi-spec.md)).
- MCP 서버는 Spring AI의 MCP 서버 기능(Streamable HTTP)으로 구현하고, 내부적으로는 외부 API와 같은 서비스 계층을 호출한다. ChatGPT 등 원격 MCP 클라이언트 연결에는 OAuth 2.1 인가 서버(Spring Authorization Server)가 필요하다.
- 조회 대상: 다이제스트(주요 뉴스 · 추가 헤드라인), 기사 메타데이터 · 검색, 사건, 이슈와 타임라인, 관심사 히스토리, 변화 리포트. 사용자 데이터(다이제스트 · 이슈 · 관심사 히스토리 · 리포트)는 토큰 주인의 범위로 한정한다.
- 외부에는 **다이제스트 · 리포트의 LLM 종합문, 기사 메타데이터, 원문 링크**만 제공한다. RSS 요약문 원문은 외부 API로 내보내지 않는다 ([security.md](security.md)).

## 모듈 이벤트

```java
// source → article. 수집 작업(PollFeed)이 새 항목마다 하나 발행한다.
public record ArticleFetched(SourceId sourceId, FeedId feedId, Instant fetchedAt, FetchedArticle article) {}

// 정규화된 기사 — 원문 본문은 담지 않는다.
public record FetchedArticle(String title, URI link, @Nullable String guid, @Nullable String summary,
        Instant publishedAt, boolean publishedAtEstimated, @Nullable String author, List<String> categories,
        @Nullable URI thumbnail) {}
```

- `SourceId` · `FeedId` 는 `sources.id` · `feeds.id`(1 이상의 `long`)다. 피드 식별자는 수집 프로필 안의 피드 키와 다르다.
- `fetchedAt` 은 그 항목을 받은 수집의 시각이다. `article` 은 이것을 처음 발견한 시각 · 본 시각으로 쓴다.
- `link` · `thumbnail` 은 `http` · `https` 웹 주소, `summary` 는 `FetchedArticle.SUMMARY_MAX_CODE_POINTS`(1,000 코드 포인트) 이하, `categories` 는 피드 순서이고 같은 값이 없다. `guid` 는 피드의 항목 식별자 원문이다. 정규화 규칙은 [pipeline.md](pipeline.md) §2.
