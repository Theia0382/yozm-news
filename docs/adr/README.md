# 아키텍처 결정 기록 (ADR)

프로젝트 결정의 맥락 · 대안 · 채택 근거 · 결과를 남긴다. 지금 유효한 상태는 그 지식을 소유한 현재 상태 문서(`overview` · 모듈 Spec · 개발 인프라 · 운영 문서 등 — [문서 위계](../README.md#1-문서-위계))에서 읽는다. 쓰고 고치는 규칙은 [운영 규칙](#운영-규칙)에 있다.

## 목록

| 번호 | 제목 | 상태 |
|---|---|---|
| [0001](0001-news-source-rss.md) | 뉴스 소스: 네이버 검색 API 대신 운영자 큐레이션 RSS | 승인 |
| [0002](0002-shared-evaluation-with-jev.md) | Jev는 사용자 무관 공유 평가에만 사용 | 승인 (부분 대체: 0010 · 0026 · 0028) |
| [0003](0003-byok-personalized-digest.md) | 사용자 BYOK LLM으로 개인화 종합 다이제스트 생성 | 승인 (부분 대체: 0047) |
| [0004](0004-tech-stack.md) | 백엔드 기술 스택 | 승인 (부분 대체: 0014 · 0030) |
| [0005](0005-frontend-react-spa.md) | 프론트엔드: React SPA, 단일 오리진 제공 | 승인 |
| [0006](0006-deployment-compose-cloudflare.md) | 배포: Docker Compose + Cloudflare Tunnel | 승인 (부분 대체: 0012 · 0021 · 0047 · 0063) |
| [0007](0007-defer-event-clustering.md) | 같은 사건 묶기는 v1에서 LLM 종합에 맡김 | 대체됨 (0025) |
| [0008](0008-v1-llm-providers.md) | v1 지원 LLM 공급자는 OpenAI(ChatGPT)와 Anthropic(Claude) | 승인 |
| [0009](0009-authentication-google-and-local.md) | v1 인증은 Google 로그인과 자체(이메일·비밀번호) 로그인 | 승인 (부분 대체: 0011 · 0012 · 0048) |
| [0010](0010-tag-taxonomy.md) | 태그 체계: 축 분리 + 통제 어휘 다중 태그 | 승인 |
| [0011](0011-no-email-in-v1.md) | v1에서는 메일 발송을 하지 않는다 | 승인 (부분 대체: 0048) |
| [0012](0012-swappable-external-dependencies.md) | 외부 의존성은 교체 가능하게, 개발 환경에는 로컬 대체물을 둔다 | 승인 (부분 대체: 0021 · 0057 · 0061) |
| [0013](0013-architecture-patterns.md) | 아키텍처 패턴: 모듈러 모놀리스 + 실용적 헥사고날 | 승인 (부분 대체: 0023 · 0040) |
| [0014](0014-spring-data-jdbc.md) | 데이터 접근은 Spring Data JDBC + JdbcClient | 승인 (부분 대체: 0089) |
| [0015](0015-source-presets-and-collection-profiles.md) | 뉴스 소스는 수집 프로필로 등록하고, 주요 언론사는 프리셋으로 제공한다 | 승인 (부분 대체: 0024 · 0041) |
| [0016](0016-containerized-checks.md) | 검사는 컨테이너에서 돌리고, CI 와 로컬이 같은 Compose 정의를 쓴다 | 승인 (부분 대체: 0035) |
| [0017](0017-standard-datasource-env-vars.md) | DB 접속 정보는 Spring 표준 환경 변수로 받는다 | 승인 |
| [0018](0018-local-profile-compose-connection.md) | 로컬 개발의 DB 접속 정보는 개발용 Compose 컨테이너에서 읽는다 | 승인 |
| [0019](0019-dev-compose-per-checkout-and-test-image-source.md) | 개발용 Compose 는 checkout 마다 따로 뜨고, 테스트의 PostgreSQL 이미지는 그 파일에서 읽는다 | 승인 (부분 대체: 0021) |
| [0020](0020-screen-api-contract-from-committed-openapi-spec.md) | 화면용 API 계약은 커밋된 OpenAPI 명세가 정본이고, 프론트엔드 타입은 그 명세에서 생성한다 | 승인 |
| [0021](0021-separate-deploy-compose-with-single-app-service.md) | 배포 구성은 개발용과 따로 둔 Compose 파일이고, 앱은 서비스 `app` 하나로 띄운다 | 승인 |
| [0022](0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md) | 컨테이너 이미지는 측별 Dockerfile 하나에 검사 · 배포 target 으로 두고, 실행 단계는 명령 없이 조립한다 | 승인 (부분 대체: 0104) |
| [0023](0023-jobrunr-oss-framework-schema-and-event-resubmission.md) | 백그라운드 작업은 JobRunr OSS 로 돌리고, 프레임워크 스키마는 라이브러리 원문을 Flyway 에 담으며, 실패한 이벤트는 반복 작업이 상한까지 다시 처리한다 | 승인 (부분 대체: 0050) |
| [0024](0024-collection-profile-yaml-and-preset-reference-or-own-profile.md) | 수집 프로필은 YAML 로 쓰고, 소스는 프리셋을 참조하거나 자기 프로필을 갖는다 | 승인 (부분 대체: 0056) |
| [0025](0025-long-term-personal-news-history-in-v1.md) | 장기 개인 뉴스 히스토리를 v1 핵심 가치로 둔다 — 공유 사건 · 사용자별 이슈 · 변화 리포트 | 승인 (부분 대체: 0047 · 0101) |
| [0026](0026-event-clustering-with-local-embeddings-and-jev.md) | 사건 묶기는 로컬 임베딩과 고유명사 산식으로 하고, 애매한 구간만 Jev 로 판정한다 | 승인 (부분 대체: 0063 · 0101 · 0103) |
| [0027](0027-pgvector-for-vector-storage.md) | 벡터 저장은 PostgreSQL pgvector 확장으로 한다 | 승인 (부분 대체: 0075 · 0079 · 0088) |
| [0028](0028-explicit-signals-and-interest-weighted-candidates.md) | 관심 신호는 명시적 행동만 쓰고, 관심도를 후보 순위에 반영하되 큰 사건을 끼워 넣는다 | 승인 (부분 대체: 0101 · 0103) |
| [0029](0029-indefinite-summary-retention-with-optional-deletion.md) | RSS 요약문은 기본 무기한 보존하고, 자동 삭제는 운영 설정으로 켠다 | 승인 (부분 대체: 0068 · 0101) |
| [0030](0030-feed-xml-own-parser-and-rome-for-format-title-link.md) | 피드 문서는 자체 구성한 XML 파서로 읽어 DTD 선언을 거부하고, Rome 은 형식 판별 · 제목 · 링크에만 쓴다 | 승인 |
| [0031](0031-feed-request-blocks-internal-addresses-with-apache-httpclient-deadline.md) | 피드 요청은 내부 주소를 기본으로 막고, Apache HttpClient 로 한 번의 수집 전체에 마감 · 크기 상한을 건다 | 승인 (부분 대체: 0049 · 0065) |
| [0032](0032-same-article-matching-avoids-false-merges-with-per-feed-entries-under-source-lock.md) | 같은 기사 판정은 기사를 잃지 않는 쪽으로 좁히고, 피드별 기록은 (기사, 피드)마다 한 행으로 소스 단위 잠금 안에서 쓴다 | 승인 (부분 대체: 0052) |
| [0033](0033-feed-polling-db-lease-per-feed-new-items-and-per-source-request-spacing.md) | 피드 수집은 DB 점유로 겹침을 막고, 피드별 직전 항목으로 새 항목을 가르며, 같은 언론사 요청 사이는 요청 직전의 DB 예약으로 띄운다 | 승인 (부분 대체: 0040 · 0051 · 0066) |
| [0034](0034-module-spec-as-public-interface-specification-and-docs-by-kind.md) | 모듈 Spec 은 모듈 공개 인터페이스의 명세로 코드보다 먼저 쓰고, 설계 문서는 종류 · 독자별 갈래로 나눈다 | 승인 (부분 대체: 0036) |
| [0035](0035-public-markdown-links-and-anchors-checked-by-separate-check-service.md) | 공개 마크다운의 상대 링크와 앵커는 별도 검사 서비스가 확인한다 | 승인 (부분 대체: 0100) |
| [0036](0036-module-spec-holds-internal-structure-and-port-declarations-in-eight-sections.md) | 모듈 Spec 은 책임 단위의 내부 구조와 포트 선언까지 갖고, 절을 바깥에서 안쪽 순서의 여덟 절로 둔다 | 승인 |
| [0037](0037-domain-may-use-non-replaceable-framework-types.md) | `domain` 은 교체하지 않는 프레임워크의 타입을 쓸 수 있고, 그 단위 테스트는 스프링 컨텍스트 없이 돈다 | 승인 |
| [0038](0038-container-images-use-major-tags-and-revisit-pinning-at-publication.md) | 컨테이너 이미지는 모두 메이저 태그로 받고, 버전 고정과 갱신 자동화는 이미지를 게시할 때 다시 본다 | 승인 |
| [0039](0039-code-comments-point-to-owning-docs-and-javadoc-format-checked-by-javac-and-javadoc.md) | 코드 주석은 Spec · ADR · 문서가 덮지 않는 것만 담고 그 위치를 가리키며, Javadoc 형식은 javac 과 javadoc 이 빌드에서 검사한다 | 승인 |
| [0040](0040-feed-status-stored-in-poll-state-and-rejudged-hourly-by-health-check.md) | 피드 상태는 수집 상태 행에 저장하고, 피드 건강 검사가 매시간 켜진 피드를 다시 판정한다 | 승인 |
| [0041](0041-activation-gate-previews-a-feed-on-enable-and-judges-by-code-constants.md) | 활성화 관문은 피드를 켤 때 한 번 받아 미리보고, 코드 상수 기준으로 판정한다 | 승인 (부분 대체: 0046) |
| [0042](0042-app-roles-as-web-worker-profiles-marked-by-platform-annotations.md) | 앱 역할은 Spring profile `web` · `worker` 로 나누고, 소속은 `platform` 역할 애너테이션으로 표시하며, 역할이 없으면 기동하지 않는다 | 승인 |
| [0043](0043-admin-functions-guarded-by-env-bearer-token-until-account-roles.md) | 운영자 기능은 계정 인증 전까지 환경 변수의 Bearer 토큰으로 막고, 계정 인증이 생기면 같은 경로의 `ADMIN` 역할 판정으로 바꾼다 | 승인 |
| [0044](0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md) | 화면 API 는 판정 결과를 판별자 하나의 평평한 200 응답으로, 요청의 문제만 RFC 9457 형식의 자체 오류 레코드로 앱 전체에서 낸다 | 승인 |
| [0045](0045-admin-module-serves-operator-api-over-module-public-operation-apis.md) | 운영자 API 는 `admin` 모듈이 내고, 각 모듈은 운영 기능과 결과 타입을 최상위 공개 API 로 낸다 | 승인 |
| [0046](0046-operator-profile-revision-disables-feeds-whose-reading-changed.md) | 운영자 원문을 고치면 읽기 설정이 바뀐 켜진 피드(검토일이 사라지면 소스)를 끄고, 피드 켜기는 저장 직전에 읽기 설정 전체를 다시 대조한다 | 승인 |
| [0047](0047-self-hosted-software-run-by-instance-operators.md) | 요즘뉴스는 개인이 자기 서버에 띄워 쓰는 셀프 호스팅 소프트웨어이고, 소스와 함께 쓸 사람은 인스턴스 운영자가 정한다 | 승인 |
| [0048](0048-sign-up-closed-by-default-with-invitations-and-operator-bootstrap.md) | 가입은 기본으로 닫고 운영자의 초대 링크와 계정 생성으로 사람을 들이며, 첫 운영자는 기동 때 로그의 초대 링크나 환경 변수로 만든다 | 승인 |
| [0049](0049-feed-request-blocks-ipv6-reserved-space-and-local-nat64.md) | 피드 요청의 차단 대역을 IPv6 예약 공간과 로컬 NAT64 접두까지 넓힌다 | 승인 |
| [0050](0050-operator-resubmits-once-or-discards-stranded-event-publications-read-from-registry.md) | 이벤트를 읽을 수 없는 발행은 재제출 전에 거르고, 상한에 이른 · 읽을 수 없는 발행은 운영자가 즉시 1회 재제출하거나 기록 없이 폐기하며, 목록 · 폐기는 `platform` 이 레지스트리 테이블을 직접 다룬다 | 승인 |
| [0051](0051-operator-hides-or-deletes-sources-and-feeds-and-deletion-removes-exclusive-articles.md) | 운영자는 소스 · 피드를 숨기거나 삭제하고, 숨김은 끄고 남기며 삭제는 그 소스 · 피드만 가진 기사까지 지운다 | 승인 |
| [0052](0052-article-sources-live-in-feed-entries-and-late-items-of-deleted-feeds-are-dropped.md) | 기사의 소스는 피드별 기록이 갖고, 제목 판정은 그 소스의 기록이 있는 기사에서 하며, 지운 피드의 늦은 항목은 버린다 | 승인 |
| [0053](0053-source-records-last-confirmed-name-apart-from-operating-settings.md) | 출처는 source 가 프로필에서 마지막으로 확인한 소스 이름을 운영 설정과 다른 테이블에 기록한다 | 승인 |
| [0054](0054-tag-set-seeded-from-default-yaml-on-first-start-with-immutable-versions-and-question-templates.md) | 태그 체계는 기본 YAML 파일을 첫 기동에 버전 1로 들이고, 버전 내용은 바꾸지 않으며, 질문은 태그 종류마다 둔 틀에 태그 설명을 끼워 만든다 | 승인 |
| [0055](0055-tag-and-store-thresholds-change-with-history-and-warn-instead-of-blocking.md) | 태그 기준 · 저장 기준은 버전의 설정으로 이력과 함께 바꾸고, 앱은 두 기준을 막지 않고 이번 변경이 어긋남을 만들 때 경고한다 | 승인 (부분 대체: 0069 · 0070) |
| [0056](0056-platform-reads-yaml-text-and-applies-common-rejection.md) | YAML 원문 읽기와 공통 거부는 `platform` 이 맡고, 문서별 형식 · 검증은 각 모듈의 `domain` 이 한다 | 승인 |
| [0057](0057-local-substitutes-registered-only-in-local-profile-without-operator-switch.md) | 로컬 대체물은 `local` profile 에서만 등록하고, 운영 설정으로 켜는 스위치를 두지 않는다 | 승인 |
| [0058](0058-evaluator-failures-in-three-kinds-and-unavailable-evaluator-skips-article-retries.md) | 평가기 실패는 일시 · 기사 거부 · 평가기 사용 불가 셋으로 나누고, 평가기 사용 불가는 기사 재시도를 쓰지 않는다 | 승인 |
| [0059](0059-evaluation-job-limits-concurrency-and-retries-with-db-leases-on-queue-rows.md) | 평가 작업은 대기 행의 DB 점유와 상태로 동시성 · 재시도를 제한하고, 정착 시간 뒤 평가하며, 결과가 체계와 어긋나거나 평가기가 예상 못한 예외를 던지면 회차를 멈춘다 | 승인 |
| [0060](0060-article-deletion-announced-by-removed-article-ids-and-followed-by-referencing-modules.md) | 기사 삭제는 지운 기사 ID 이벤트로 알리고, 기사를 가리키는 모듈이 받아 자기 행을 지운다 | 승인 |
| [0061](0061-worker-role-does-not-run-without-an-evaluator.md) | 작업 역할은 평가기 없이 운영하지 않고, 그 기동 검사는 운영 평가기의 설정과 함께 둔다 | 승인 |
| [0062](0062-tag-set-versions-from-operator-text-activated-forward-with-key-lineage-and-recent-reevaluation.md) | 태그 체계는 운영자 원문으로 새 버전을 만들어 앞으로만 켜고, 키 계보를 원문에 적으며, 켤 때 정한 일수 안에 처음 본 기사를 재평가 대기에 넣는다 | 승인 |
| [0063](0063-jev-called-through-one-system-one-schema-context-overflow-probed-with-empty-state-latest-model-alias-by-default.md) | Jev 는 System One 스키마 하나로 부르고, 문맥 초과는 빈 state 로 다시 물어 원인을 가리며, 모델 기본값은 최신 별칭이다 | 승인 |
| [0064](0064-topic-coverage-counts-articles-by-first-evaluation-time-with-query-time-judgement-over-key-lineage-in-the-app.md) | 주제별 보도량은 기사의 첫 평가 시각으로 기간에 넣고, 최신 평가의 조회 시점 판정을 계보로 활성 버전 키에 이어 앱에서 센다 | 승인 |
| [0065](0065-blocked-address-fetch-failure-counts-as-temporary.md) | 막힌 주소로 가는 피드 요청은 일시 실패로 센다 | 승인 |
| [0066](0066-overflow-suspected-only-when-previous-and-current-fetches-both-have-ten-or-more-keys.md) | 넘침 의심은 직전 · 이번 수집의 키가 모두 10개 이상일 때만 본다 | 승인 |
| [0067](0067-partial-supersession-marked-in-status-line-and-above-replaced-sentence-and-supplements-in-follow-up-records.md) | ADR 의 부분 대체는 상태 줄과 대체된 문장 위 표시로 알리고, 결정을 바꾸지 않는 보충은 후속 기록으로 남긴다 | 승인 |
| [0068](0068-rss-summaries-shown-only-on-operator-screens-and-users-get-processed-information.md) | RSS 요약문은 운영자 화면에만 보이고, 인스턴스 사용자와 외부 API 에는 가공한 정보만 낸다 | 승인 |
| [0069](0069-missing-tags-filled-by-the-evaluator-rereading-stored-raw-responses-over-an-operator-chosen-period.md) | 저장 기준을 낮춘 뒤 빠진 태그는 운영자가 정한 기간의 평가를 저장된 원본 응답으로 평가기가 다시 읽어 채운다 | 승인 |
| [0070](0070-past-store-threshold-maximum-and-mixed-store-floors-computed-from-history-and-finished-backfills.md) | 지난 저장 기준 최댓값과 평가 현황의 저장 기준 섞임은 이력과 끝난 채움 기록으로 평가 시각 축의 저장 바닥을 계산해 낸다 | 승인 |
| [0071](0071-event-membership-judged-from-article-ids-with-questions-built-in-evaluation.md) | 사건 소속 판정은 판정할 기사와 후보 사건의 서술 기사를 ID 로 받아 `evaluation` 이 질문을 만들고, 판정 원본에는 ID 만 남긴다 | 승인 (부분 대체: 0094) |
| [0072](0072-event-membership-question-wording-as-one-code-constant-with-its-version-in-raw-records.md) | 사건 소속 질문 문구는 코드 상수 하나로 두고 판정 원본에 문구 판을 남긴다 | 승인 |
| [0073](0073-event-membership-api-in-both-roles-looks-up-the-evaluator-on-call-and-fails-without-one.md) | 사건 소속 판정 공개 API 는 두 역할 공통 빈으로 두고, 평가기는 부를 때 찾아 없으면 예외로 드러낸다 | 승인 |
| [0074](0074-article-features-built-by-event-intake-queue-job-and-skipped-without-an-embedder.md) | 사건 묶기의 기사 표현은 `event` 대기와 작업 역할의 반복 작업이 만들고, 임베더가 없으면 건너뛰어 대기에 쌓는다 | 승인 (부분 대체: 0092 · 0093) |
| [0075](0075-article-vectors-in-a-dimensionless-vector-column-one-row-per-model-version.md) | 기사 벡터는 차원 없는 `vector` 컬럼에 기사 × 모델 버전마다 한 행으로 두고, 모델 교체는 새 행으로 한다 | 승인 (부분 대체: 0079) |
| [0076](0076-key-terms-from-nori-without-decompounding-proper-common-and-foreign-nouns.md) | 기사의 핵심 말은 Nori 를 분해 없이 돌려 고유명사 · 일반명사 · 외국어를 고르고 한 글자 한글 일반명사를 버린다 | 승인 |
| [0077](0077-event-assignment-queue-judged-one-article-at-a-time-with-evaluation-failure-kinds.md) | 사건 배정은 따로 둔 배정 대기를 사건 묶기 반복 작업이 기사 하나씩 판정해 커밋하고, 사건 소속 판정의 실패는 평가 작업과 같은 갈래로 처리한다 | 승인 |
| [0078](0078-articles-ending-in-a-judgement-failure-open-a-new-event-marked-as-failed.md) | 사건 소속 판정에 실패해 끝낸 기사는 실패 표지를 단 새 사건으로 연다 | 승인 |
| [0079](0079-event-centroids-in-a-dimensionless-halfvec-column-with-a-model-version-per-event.md) | 사건 중심 벡터는 차원 없는 `halfvec` 에 사건마다 모델 버전과 함께 둔다 | 승인 |
| [0080](0080-event-centroids-and-key-terms-accumulated-with-article-counts-and-updated-incrementally.md) | 사건 중심 · 핵심 말은 기사 수와 함께 누적해 붙이고 뗄 때 증분 갱신한다 | 승인 |
| [0081](0081-event-assignment-criteria-in-settings-and-rejudgement-within-stored-scores.md) | 사건 기준 · 저장 기준은 설정에 두고(기본값은 코드), 기준이 바뀌면 저장된 점수 · 확률 안에서 재판정한다 | 승인 (부분 대체: 0087) |
| [0082](0082-big-events-judged-at-query-time-by-distinct-sources-and-mean-importance-both-reaching-criteria.md) | 큰 사건은 기간 안의 서로 다른 소스 수와 중요도 평균이 둘 다 기준 이상인 사건이고, 조회할 때 지금 기준으로 판정한다 | 승인 (부분 대체: 0103) |
| [0083](0083-big-event-criteria-as-operator-settings-with-append-only-history-and-preview.md) | 큰 사건 기준은 운영자 설정으로 두고 추가 전용 이력과 미리보기를 둔다 | 승인 (부분 대체: 0103) |
| [0084](0084-event-merge-applies-assignment-scoring-between-events-and-keeps-merged-rows-pointing-to-survivors.md) | 사건 병합은 배정 산식 · 판정을 사건 대 사건에 쓰고, 합쳐진 사건은 남는 사건을 가리키는 행으로 남긴다 | 승인 (부분 대체: 0093) |
| [0085](0085-rejudgement-knows-opened-events-from-an-assignment-flag-and-reads-merged-candidates-as-survivors.md) | 재판정은 그 기사가 연 사건을 배정의 표지로 알고, 저장 후보의 합쳐진 사건은 남는 사건으로 읽는다 | 승인 |
| [0086](0086-events-close-after-an-idle-period-since-the-last-join-or-a-maximum-period-since-opening.md) | 사건은 마지막으로 기사가 붙은 뒤 닫는 기간이 지나거나 연 뒤 최대 기간이 지나면 닫는다 | 승인 (부분 대체: 0101) |
| [0087](0087-closed-event-assignments-are-not-rejudged-and-closing-and-merging-are-not-undone.md) | 닫힌 사건의 배정은 재판정하지 않고, 닫기 · 병합은 기준이 바뀌어도 되돌리지 않는다 | 승인 |
| [0088](0088-article-features-kept-until-their-event-closes-and-unassigned-ones-for-the-window-plus-a-margin.md) | 기사 표현은 그 기사의 사건이 닫힐 때까지 보관하고, 배정 없는 표현은 만든 뒤 시간 창 + 여유만큼 보관한다 | 승인 |
| [0089](0089-long-term-hnsw-indexes-on-closed-events-created-per-model-version-at-runtime-by-the-worker-role.md) | 장기 검색 HNSW 인덱스는 닫힌 사건에 모델 버전마다 작업 역할이 실행 중에 만든다 | 승인 |
| [0090](0090-article-embedding-defaults-to-an-in-app-local-model-with-an-openai-compatible-api-by-setting.md) | 기사 임베딩의 기본은 앱 안 로컬 모델이고, OpenAI 호환 외부 API 를 설정으로 고르며, 외부 방식의 차원은 운영자가 정한다 | 승인 |
| [0091](0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md) | 기본 로컬 모델은 multilingual-e5-small int8 을 DJL 토크나이저로 돌리고, 고정 리비전 · 해시로 빌드 때 받아 이미지에 담으며, ONNX Runtime 텔레메트리를 끄는 환경 변수가 없으면 모델을 열지 않는다 | 승인 |
| [0092](0092-embedder-failures-in-three-kinds-with-rejected-articles-isolated-and-dropped-after-a-limit.md) | 임베더 실패는 일시 · 입력 거부 · 사용 불가 셋으로 나누고, 입력 거부는 기사 하나씩 다시 불러 가린 뒤 거부 수가 상한에 이르면 사건 묶기 대기에서 뺀다 | 승인 |
| [0093](0093-worker-role-does-not-run-without-an-embedder-built-only-in-the-worker-role.md) | 작업 역할은 임베더 없이 운영하지 않는다 — 운영 임베더는 작업 역할에서만 만들고, 반복 작업이 생성자로 받는다 | 승인 |
| [0094](0094-event-membership-judgements-identified-by-id-read-by-operators-and-referenced-by-the-records-that-apply-them.md) | 사건 소속 판정 원본은 ID 로 식별되어 운영자 조회로 읽히고, 판정을 반영한 `event` 기록이 그 ID 를 가리켜 판정의 쓰임과 결과를 잇는다 | 승인 |
| [0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md) | 사건 묶기 평가는 운영자 API 의 결과를 날짜 단위 전수 라벨과 견주는 운영자 도구로 한다 | 승인 (부분 대체: 0098) |
| [0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md) | 인스턴스에 묶인 평가 데이터는 공개 저장소 밖에 두고, 기본값의 근거는 "평가 세트로 정했다"로 적는다 | 승인 |
| [0097](0097-event-clustering-evaluation-labels-reports-of-one-happening-narrower-than-the-system-event.md) | 사건 묶기 평가의 정답 사건은 한 번 일어난 일을 다룬 보도들로 붙이고, 지금 시스템의 사건보다 좁은 차이는 정밀도 손실로 읽는다 | 승인 (부분 대체: 0102) |
| [0098](0098-label-rule-2-samples-hold-no-big-event-gold-and-big-event-precision-is-not-measured-without-marks.md) | 라벨 기준 2판 표본에는 큰 사건 정답을 두지 않고, 큰 사건 표시가 없는 표본은 큰 사건 정밀도를 재지 않는다 | 승인 |
| [0099](0099-importance-judged-by-what-the-matter-changes-and-its-reach-with-levels-written-as-reach-without-article-type-examples.md) | 중요도는 사안이 실제로 바꾸거나 일으킨 일의 크기와 영향 범위로 매기고, 수준 설명은 기사 종류 예시 없이 영향 범위로 쓴다 | 승인 |
| [0100](0100-repository-tool-unit-tests-run-in-one-tools-check-service-and-docs-check-checks-links-only.md) | 저장소 도구의 단위 테스트는 검사 서비스 `tools` 하나가 돌리고, 문서 검사는 링크와 앵커만 본다 | 승인 (부분 대체: 0104) |
| [0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md) | 사건을 잇는 흐름은 모든 사용자가 공유하는 스토리라인이 맡고, 개인 쪽은 스토리라인 구독과 관심사로 나눈다 | 승인 |
| [0102](0102-an-event-is-the-reports-of-one-happening-narrowed-by-the-event-membership-question-wording.md) | 사건은 한 번 일어난 일을 다룬 보도들이고, 시스템의 사건 범위는 사건 소속 질문 문구로 좁힌다 | 승인 |
| [0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md) | 큰 판정은 스토리라인 단위로 하고, 사건 묶기 바로 뒤 스토리라인 마일스톤이 사건의 큰 사건 판정을 거둔다 | 승인 |
| [0104](0104-arm64-run-checked-on-arm64-runners-with-a-symmetric-deploy-check-per-architecture.md) | arm64 실행은 arm64 러너의 배포 검사로 확인하고, 배포 검사는 아키텍처마다 대칭 job 으로 둔다 | 승인 |
| [0105](0105-storylines-link-incrementally-from-event-change-notifications-and-events-publish-changed-event-ids.md) | 스토리라인은 사건 변화 알림으로 증분으로 잇고, `event` 는 바뀐 사건 ID 를 발행한다 | 승인 |
| [0106](0106-provisional-storyline-linking-opens-only-with-a-partner-event-and-adds-the-single-best-storyline-without-removal.md) | 스토리라인의 임시 잇기 규칙 — 다른 사건과 이어질 때만 열고, 기준 이상 가운데 가장 높은 하나를 더하며 빼지 않는다 | 승인 (부분 대체: 0108) |
| [0107](0107-storyline-state-is-judged-at-query-time-from-the-last-article-and-an-ended-storyline-still-links.md) | 스토리라인 상태는 저장하지 않고 조회 때 마지막 기사 시각으로 진행 · 잠잠 · 끝남을 가르며, 끝나도 잇기를 막지 않는다 | 승인 |
| [0108](0108-storylines-with-the-same-events-merge-and-a-storyline-left-with-one-event-merges-or-dissolves.md) | 구성 사건이 같은 스토리라인은 합치고, 사건 하나만 남은 스토리라인은 그 사건을 가진 스토리라인에 합치거나 풀며, 합쳐진 스토리라인은 남는 쪽을 가리킨다 | 승인 |
| [0109](0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md) | 사건 묶기 기본값은 기본 임베딩 모델에 맞춰 평가 세트로 견주고 운영자 표본 검수로 확인해 정하며, 반감기는 상수로 두고 닫는 기간은 시간 창에 늦은 수집의 여유를 더한다 | 승인 |

## 운영 규칙

### 선별 기준

다음 가운데 하나를 충족하는 결정을 ADR 로 남긴다. 충족하지 않는 결정은 그 작업의 PR · 문서에 남긴다.

- 실질적인 대안이 둘 이상이고 되돌리기 비싸다
- 모듈을 가로지르는 규약이다
- 6개월 뒤에 "왜 이렇게 했나"를 물을 결정이다

### 결정 하나에 파일 하나

독립적으로 바뀔 수 있는 결정 둘은 ADR 을 나눈다. 이미 있는 묶음 ADR 은 그대로 둔다.

### 대체와 보충

새 ADR 이 옛 ADR 의 결정을 바꾸거나 보충하면, 새 ADR 의 `관련:` 줄에 옛 ADR 과 그 범위를 적고 같은 작업에서 옛 ADR 에 알린다. 본문을 고칠 수 있는 범위는 [작성 규칙](../README.md#4-작성-규칙)의 "ADR 은 기록이다"를 따른다.

- **전체 대체** — 제목이 말하는 결정을 바꾼다. 옛 ADR 의 상태를 `대체됨(ADR-NNNN)` 으로 바꾼다
- **부분 대체** — 일부를 바꿔, 옛 문장을 지금 읽으면 지금 결정과 다른 것을 믿게 된다. 좁힘 · 넓힘 · 철회도 여기에 들고, 이름은 "부분 대체" 하나로 쓴다. 옛 ADR 의 상태 줄에 더하고, 대체된 문장 위에 대체 표시를 단다
- **보충** — 옛 결정을 바꾸지 않는다. 미뤄 둔 사항을 다른 ADR 이 정했거나, 옛 범위 밖으로 더했거나, 문면이 구현과 어긋나는 것 등이다. 옛 ADR 의 후속 기록에 남긴다

### 대체 표시

대체된 문장 바로 위에 인용 블록 한 줄로 단다. 대체한 ADR 과 지금 상태의 자리(그 지식을 소유한 현재 상태 문서의 절 — 규칙 식별자가 있으면 함께)만 적고, 소유 문서가 없으면 자리를 뺀다. 새 결정의 내용은 옮겨 적지 않는다. 대체된 문장은 지우거나 고치지 않는다.

> 부분 대체 — [ADR-0014](0014-spring-data-jdbc.md). 지금 상태: [`overview` §7](../overview.md#7-기술-스택)

- 이어진 여러 줄이 함께 대체되면 첫 줄 위에 하나를 달고 덮는 범위를 적는다 — `> 부분 대체(아래 두 항목) — …`. 문장을 특정할 수 없으면(절 전체 등) 절 제목 바로 아래에 단다
- 표 안의 문장이면 표 바로 위에 달고 행을 밝힌다 — `> 부분 대체("데이터 접근" 행) — …`. 서로 다른 ADR 이 한 표의 여러 행을 대체했으면 표시 하나에 행별로 적는다 — `> 부분 대체 — "데이터 접근" 행은 [ADR-…](…), "RSS" 행은 [ADR-…](…). 지금 상태: …`
- 하위 목록 안의 문장이면 상위 항목의 내용 들여쓰기에 맞춘다
- 표시 바로 다음이 표나 문단이면 그 사이에 빈 줄을 둔다 — 빈 줄이 없으면 다음 줄이 표시에 붙는다. 목록 항목 사이에는 빈 줄을 더하지 않는다
- 한 줄에 문장이 여럿이고 일부만 대체됐으면 어느 문장인지 적는다 — `> 부분 대체(둘째 문장) — …`. 서로 다른 ADR 이 대체했으면 표시 하나에 문장별로 적는다 — `> 부분 대체 — 첫 문장은 [ADR-…](…), 둘째 문장은 [ADR-…](…). 지금 상태: …`
- 대체된 문장을 다른 ADR 이 다시 대체하면 그 표시에 ADR 을 더하고 자리를 고친다

### 상태 줄과 목록 표

- 상태 줄 — `승인. 부분 대체 — {대체된 부분}([ADR-NNNN](…)) · {대체된 부분}([ADR-NNNN](…))`. 대체된 부분은 짧은 명사구이고 본문에 나오는 순서로 적는다
- 목록 표의 상태 열 — `승인 (부분 대체: NNNN · NNNN)`, 전체 대체는 `대체됨 (NNNN)`

### 후속 기록

보충은 말미 `## 후속 기록` 절에 날짜와 한 줄로 남긴다 — `- YYYY-MM-DD: {옛 ADR 의 자리} — {그 자리를 정하거나 보충한 ADR · 지금 상태 문서의 자리}`. 정해진 내용은 옮겨 적지 않는다.

## 양식

```markdown
# ADR-NNNN: 제목

- 상태: 제안 | 승인 | 대체됨(ADR-XXXX)
- 날짜: YYYY-MM-DD
- 근거: 결정의 근거 — 이슈 · 실측 · 조사 · 구현 커밋
- 관련: [ADR-XXXX](…)(관계 — 이 ADR 이 대체 · 보충하면 그 범위)

## 맥락
## 결정
## 결과
## 검토한 대안
## 후속 기록
```

`근거:` · `관련:` 줄과 `## 후속 기록` 절은 해당하는 것이 없으면 뺀다.
