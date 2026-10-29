# 모듈 인터페이스와 백그라운드 작업

아직 모듈이 없는 포트 · 백그라운드 작업과 외부 API · MCP 의 초안을 담는다. 시스템 구성 · 모듈 지도 · 기술 스택은 [overview.md](overview.md)에 있다. 모듈을 만드는 이슈가 그 모듈의 항목을 그 모듈 Spec([specs/](specs/README.md))으로 옮긴다.

## 핵심 인터페이스

```java
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
- `DigestGenerator`의 v1 구현체는 Spring AI `ChatClient` 기반 `LlmDigestGenerator`. 공급자별 차이는 Spring AI가 흡수한다.
- `ReportGenerator`의 v1 구현체는 `DigestGenerator` 와 같은 방식의 LLM 구현이고, Spring profile `local` 은 고정 응답 로컬 대체물을 쓴다 ([ADR-0025](adr/0025-long-term-personal-news-history-in-v1.md)).
- Spring AI의 `ChatModel`은 보통 애플리케이션 전역 빈으로 쓰지만, BYOK에서는 **요청마다 사용자 키로 인스턴스를 만든다**. `LlmClientFactory`가 이 차이를 감춘다.

## 주요 백그라운드 작업

| 작업 | 주기/계기 | 내용 |
|---|---|---|
| `EvaluatePendingArticles` | 1분마다 + `ArticleStored` | 평가 대기 기사를 묶어서 Jev 호출 |
| `AssignArticleToEvent` | `ArticleStored` | 기사 표현(임베딩 · 고유명사) → 사건 후보 → 판정 → 배정 또는 새 사건 ([pipeline.md](pipeline.md) §8) |
| `MergeEvents` | 주기 | 가까운 열린 사건끼리 합친다 |
| `CloseEvents` | 하루 1회 | 새 기사가 일정 기간 없는 사건을 닫는다 |
| `ScheduleDueDigests` | 1분마다 | 생성 시각이 다가온 사용자의 다이제스트 생성 작업 등록 |
| `GenerateDigest(userId, date)` | 위에서 등록 / 사용자 수동 요청 | 후보 선별 → LLM 호출 → 검증 → 저장 → 이슈(`storyline`) 연결 적용 |
| `MarkDormantStorylines` | 하루 1회 | 새 연결이 기준 일수 동안 없는 이슈를 휴면으로 바꾼다. 마지막 연결 날짜는 `digest` 의 타임라인에서 오고, 전이는 `storyline` API 로 한다. 이 작업을 어느 모듈에 둘지는 구현에서 정한다 — 어느 쪽이든 `storyline` 이 `digest` 를 읽어서는 안 된다 |
| `ScheduleDueReports` | 1시간마다 | 리포트 시점이 된 사용자의 변화 리포트 생성 작업 등록 |
| `GenerateReport(userId, period)` | 위에서 등록 | 통계 집계 → 스냅숏 → LLM 호출 → 검증 → 저장 |
| `Retention` | 하루 1회 | 보존 기간이 지난 데이터 정리 ([data-model.md](data-model.md)) — 사건 배정 입력, 요약문(운영 설정을 켰을 때) 포함 |

## 외부 API와 MCP (v1 이후)

- 경로를 분리한다: 화면용 `/api/**`(세션 쿠키), 외부용 `/public-api/v1/**`(Personal Access Token), MCP `/mcp`(OAuth 2.1).
- 화면용 API와 외부 API는 계약을 따로 관리한다. 외부 API는 하위 호환을 지켜야 하는 공개 계약이고, 화면용 API는 프론트엔드와 함께 자유롭게 바뀐다. 화면용 API 계약의 정본은 커밋된 OpenAPI 명세(`api/openapi.json`)이고, 프론트엔드 타입은 그 명세에서 생성한다 ([ADR-0020](adr/0020-screen-api-contract-from-committed-openapi-spec.md)).
- MCP 서버는 Spring AI의 MCP 서버 기능(Streamable HTTP)으로 구현하고, 내부적으로는 외부 API와 같은 서비스 계층을 호출한다. ChatGPT 등 원격 MCP 클라이언트 연결에는 OAuth 2.1 인가 서버(Spring Authorization Server)가 필요하다.
- 조회 대상: 다이제스트(주요 뉴스 · 추가 헤드라인), 기사 메타데이터 · 검색, 사건, 이슈와 타임라인, 관심사 히스토리, 변화 리포트. 사용자 데이터(다이제스트 · 이슈 · 관심사 히스토리 · 리포트)는 토큰 주인의 범위로 한정한다.
- 외부에는 **다이제스트 · 리포트의 LLM 종합문, 기사 메타데이터, 원문 링크**만 제공한다. RSS 요약문 원문은 외부 API로 내보내지 않는다 ([security.md](security.md)).

## 모듈 이벤트

코드가 있는 모듈의 이벤트는 그 모듈 Spec 의 공개 인터페이스 절(§3)에, 모듈 사이의 이벤트 흐름은 [overview.md §5](overview.md#5-모듈-지도)에 있다.
