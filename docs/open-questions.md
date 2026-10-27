# 미결 사항

결정되면 해당 문서와 ADR에 반영하고 여기서 지운다.

## 착수 전에 필요

| # | 질문 | 권장안 | 필요 시점 |
|---|---|---|---|
| 1 | 초기 태그 체계 세부 문구(태그 설명, 중요도 기준) | 구조는 확정([ADR-0010](adr/0010-tag-taxonomy.md)). 초안 어휘로 파일럿 후 문구 확정 | M2 |
| 19 | 첫 프리셋 대상 언론사와 활성화 관문 기준값(날짜 해석률 등) | 구조는 확정([ADR-0015](adr/0015-source-presets-and-collection-profiles.md)). [조사 자료](research/2026-10-rss-survey.md)의 12곳 중 이용조건을 확인한 곳부터, 기준값은 프리셋 테스트 데이터로 정한다 | M1 |

## 운영하면서 결정

| # | 질문 | 권장안 |
|---|---|---|
| 7 | 축별 태그 임계값 `τ`, 저장 하한, 중요도 기준 | M2에서 표본 검수 후 결정 |
| 8 | 다이제스트 주기: 하루 1회 고정인가, 사용자가 여러 번 받을 수 있는가 | v1은 하루 1회 + 수동 재생성 |
| 9 | 기본 후보 수와 모델별 토큰 예산 | 60건에서 시작해 M5 측정 결과로 조정 |
| 10 | 보존 기간 ([data-model.md](data-model.md)) | 초안 값으로 시작. RSS 요약문은 결정됨(아래) |
| 11 | 사용자 피드 추가 허용 | v1은 불가. 열 때는 사용자당 피드 수 상한, 피드 볼륨 상한, 운영자 승인 중 하나 이상이 필요하다 (Jev 비용이 운영자 부담이므로) |
| 12 | 본문 추출 도입 | M5 품질 측정 후. 요약문이 거의 없는 소스(제목만 주는 피드)부터 검토 |
| 13 | 저가 모델 기반 기본 티어 | 보류 (사용자 결정) |
| 20 | 사건 묶기 값: 후보 시간 창, 위 · 아래 판정 기준, 사건을 닫는 기간, 임베딩 모델 ([pipeline.md](pipeline.md) §8) | M3 평가 세트로 정한다 |
| 21 | 큰 사건 기준(소스 수 · 중요도)과 큰 사건 슬롯 수 | M3 평가 세트와 운영자 표본 검수로 시작해 M5 측정으로 조정 |
| 22 | 사건 배정 입력(기사 벡터)의 보관 기간 | 후보 시간 창에 여유를 더한 값. M3 에서 정한다 |
| 23 | 진행 중인 이슈(`storyline`) 구획의 상한, 이슈 휴면 기준 일수 | M5 토큰 · 연결 측정으로 정한다 |
| 24 | 관심도 감쇠 · 가중치, 구독 제안 기준 | 중립에서 시작해 M6 측정으로 정한다 |

## 확인이 필요한 외부 사실

| # | 내용 |
|---|---|
| 14 | 각 언론사 RSS의 이용조건 (특히 AI 처리와 상업적 서비스에 대한 조항). 소스를 활성화하기 전에 확인한다 |
| 15 | Jev 공식 가격, 이용 경로(직접 API 대기열, AI Gateway 지원 여부), 요청당 토큰 상한(32k와 64k로 자료가 엇갈림), 질문 수 상한, Choice 선택지 상한. 현재 수치는 3자 자료 기준이다 |
| 16 | 네이버 검색 API 약관 개정 공식 원문. 보도 기준으로 결정했으며 원문은 직접 확인하지 못했다 |
| 25 | pgvector 이미지(OS 계열을 맞춘 PG 18 태그)의 재빌드 주기 — PostgreSQL 보안 패치가 반영되는 속도 |
| 26 | ONNX Runtime Java 배포본에 linux-aarch64 네이티브가 들어 있는지, 네이티브를 푸는 임시 경로가 root 가 아닌 컨테이너에서 쓰이는지 |
| 27 | 한국어 임베딩 모델 후보의 크기 · 메모리 · 품질, 모델 파일의 배포 방식(이미지에 포함 · 처음 기동 때 내려받기 — [ADR-0022](adr/0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md)) |
| 28 | Jev 사건 소속 판정 질문의 품질 (M3 평가 세트) |
| 29 | 관리형 PostgreSQL 의 PG 18 · pgvector 버전 지원과 확장 설치 권한 (호스팅을 정할 때) |

## 결정됨 (2026-10-25)

| # | 내용 | 반영 |
|---|---|---|
| 2 | v1 LLM 공급자: OpenAI(ChatGPT), Anthropic(Claude) | [ADR-0008](adr/0008-v1-llm-providers.md) |
| 3 | v1 로그인: Google + 자체 로그인 | [ADR-0009](adr/0009-authentication-google-and-local.md) |
| 4 | 데이터 접근: Spring Data JDBC + JdbcClient (처음엔 JPA로 정했다가 변경) | [ADR-0014](adr/0014-spring-data-jdbc.md) |
| 18 | 아키텍처 패턴, 기본 패키지 `net.yozmnews`, 컨트롤러는 각 모듈 안 | [ADR-0013](adr/0013-architecture-patterns.md) |
| 5 | 프론트엔드 세부: React Router, TanStack Query, Tailwind + shadcn/ui | [ADR-0005](adr/0005-frontend-react-spa.md) |
| 17 | 메일 발송: v1에서 하지 않음 | [ADR-0011](adr/0011-no-email-in-v1.md) |
| 6 | 서비스 이름: 요즘뉴스(Yozm News), 도메인 `yozmnews.net` (Cloudflare Registrar). 비상업 개인 프로젝트 성격이라 상표 리스크는 감수 | [infrastructure.md](infrastructure.md) |

## 결정됨 (2026-10-27)

| # | 내용 | 반영 |
|---|---|---|
| 10 (요약문) | RSS 요약문은 기본 무기한 보존하고, 자동 삭제는 운영 설정으로 켠다 | [ADR-0029](adr/0029-indefinite-summary-retention-with-optional-deletion.md) |
