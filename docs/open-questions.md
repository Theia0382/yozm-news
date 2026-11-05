# 미결 사항

결정되면 해당 문서와 ADR에 반영하고 여기서 지운다.

## 착수 전에 필요

| # | 질문 | 권장안 | 필요 시점 |
|---|---|---|---|
| 1 | 초기 태그 체계 세부 문구(태그 설명, 중요도 기준) | 구조는 확정([ADR-0010](adr/0010-tag-taxonomy.md)). 초안 어휘로 파일럿 후 문구 확정 | M2 |

## 운영하면서 결정

| # | 질문 | 권장안 |
|---|---|---|
| 7 | 주제 · 지역의 태그 기준 · 저장 기준, 중요도 기준 | M2에서 표본 검수 후 결정 |
| 8 | 다이제스트 주기: 하루 1회 고정인가, 사용자가 여러 번 받을 수 있는가 | v1은 하루 1회 + 수동 재생성 |
| 9 | 기본 후보 수와 모델별 토큰 예산 | 60건에서 시작해 M5 측정 결과로 조정 |
| 10 | 보존 기간 ([data-model.md](data-model.md)) | 초안 값으로 시작. RSS 요약문은 결정됨(아래) |
| 11 | 사용자 피드 추가 허용 | v1은 불가. 열 때는 사용자당 피드 수 상한, 피드 볼륨 상한, 운영자 승인 중 하나 이상이 필요하다 (Jev 비용이 운영자 부담이므로) |
| 12 | 본문 추출 도입 | M5 품질 측정 후. 요약문이 거의 없는 소스(제목만 주는 피드)부터 검토 |
| 20 | 사건 묶기 값: 후보 시간 창, 위 · 아래 판정 기준, 사건을 닫는 기간, 임베딩 모델 ([event Spec §1](specs/event.md#1-책임과-범위)) | M3 평가 세트로 정한다 |
| 21 | 큰 사건 기준(소스 수 · 중요도)과 큰 사건 슬롯 수 | M3 평가 세트와 운영자 표본 검수로 시작해 M5 측정으로 조정 |
| 22 | 사건 배정 입력(기사 벡터)의 보관 기간 | 후보 시간 창에 여유를 더한 값. M3 에서 정한다 |
| 23 | 진행 중인 이슈(`storyline`) 구획의 상한, 이슈 휴면 기준 일수 | M5 토큰 · 연결 측정으로 정한다 |
| 24 | 관심도 감쇠 · 가중치, 구독 제안 기준 | 중립에서 시작해 M6 측정으로 정한다 |
| 31 | 메일 발송 서비스 선택(메일을 도입할 때) | SMTP 로 붙인다([ADR-0012](adr/0012-swappable-external-dependencies.md)). 후보 Resend · Amazon SES · Postmark. 발송 도메인에 SPF · DKIM · DMARC 를 설정한다. 자체 메일 서버는 전달률을 위해 역방향 DNS 도 필요하다 |
| 32 | 키가 없는 사용자에게 Jev 평가 결과 기반 헤드라인 목록을 보여 줄지 | 보류 — 생성형 LLM 을 쓰지 않아 운영자 비용이 늘지 않는다. 지금은 키 등록 페이지만 보여 준다([ADR-0003](adr/0003-byok-personalized-digest.md)) |

## 확인이 필요한 외부 사실

| # | 내용 |
|---|---|
| 16 | 네이버 검색 API 약관 개정 공식 원문. 보도 기준으로 결정했으며 원문은 직접 확인하지 못했다 |
| 25 | pgvector 이미지(OS 계열을 맞춘 PG 18 태그)의 재빌드 주기 — PostgreSQL 보안 패치가 반영되는 속도 |
| 26 | ONNX Runtime Java 배포본에 linux-aarch64 네이티브가 들어 있는지, 네이티브를 푸는 임시 경로가 root 가 아닌 컨테이너에서 쓰이는지 |
| 27 | 한국어 임베딩 모델 후보의 크기 · 메모리 · 품질, 모델 파일의 배포 방식(이미지에 포함 · 처음 기동 때 내려받기 — [ADR-0022](adr/0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md)) |
| 28 | Jev 사건 소속 판정 질문의 품질 (M3 평가 세트) |

## 결정됨 (2026-10-25)

| # | 내용 | 반영 |
|---|---|---|
| 2 | v1 LLM 공급자: OpenAI(ChatGPT), Anthropic(Claude) | [ADR-0008](adr/0008-v1-llm-providers.md) |
| 3 | v1 로그인: Google + 자체 로그인 | [ADR-0009](adr/0009-authentication-google-and-local.md) |
| 4 | 데이터 접근: Spring Data JDBC + JdbcClient (처음엔 JPA로 정했다가 변경) | [ADR-0014](adr/0014-spring-data-jdbc.md) |
| 18 | 아키텍처 패턴, 기본 패키지 `net.yozmnews`, 컨트롤러는 각 모듈 안 | [ADR-0013](adr/0013-architecture-patterns.md) |
| 5 | 프론트엔드 세부: React Router, TanStack Query, Tailwind + shadcn/ui | [ADR-0005](adr/0005-frontend-react-spa.md) |
| 17 | 메일 발송: v1에서 하지 않음 | [ADR-0011](adr/0011-no-email-in-v1.md) |
| 6 | 서비스 이름: 요즘뉴스(Yozm News), 프로젝트 도메인 `yozmnews.net` (Cloudflare Registrar). 비상업 개인 프로젝트 성격이라 상표 리스크는 감수 | [ADR-0047](adr/0047-self-hosted-software-run-by-instance-operators.md) |

## 결정됨 (2026-10-27)

| # | 내용 | 반영 |
|---|---|---|
| 10 (요약문) | RSS 요약문은 기본 무기한 보존하고, 자동 삭제는 운영 설정으로 켠다 | [ADR-0029](adr/0029-indefinite-summary-retention-with-optional-deletion.md) |

## 결정됨 (2026-10-31)

| # | 내용 | 반영 |
|---|---|---|
| 13 | 저가 모델 기반 기본 티어: 두지 않는다 | [ADR-0047](adr/0047-self-hosted-software-run-by-instance-operators.md) |

## 결정됨 (2026-11-02)

| # | 내용 | 반영 |
|---|---|---|
| 15 | Jev 공식 자료 확인 — 가격은 입력 100만 토큰당 $0.042(출력 무료). 경로는 TypeSafe System One API(`POST /v1/systemone`)이고, OpenRouter 가 같은 스키마의 호환 경로를 둔다. Cloudflare · Vercel AI Gateway 경유는 공식 문서에 없다. 문맥 상한은 요청 전체 64k · 질문 하나 32k 토큰이고, 질문 수 상한은 없다. Choice 선택지는 1 ~ 255개, Score 수준은 1 ~ 10개다 | [evaluation Spec](specs/evaluation.md#구현과-로컬-대체물) · [ADR-0063](adr/0063-jev-called-through-one-system-one-schema-context-overflow-probed-with-empty-state-latest-model-alias-by-default.md) |
