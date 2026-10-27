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
| 9 | 기본 후보 수와 모델별 토큰 예산 | 60건에서 시작해 M4 측정 결과로 조정 |
| 10 | 보존 기간 ([data-model.md](data-model.md)) | 초안 값으로 시작 |
| 11 | 사용자 피드 추가 허용 | v1은 불가. 열 때는 사용자당 피드 수 상한, 피드 볼륨 상한, 운영자 승인 중 하나 이상이 필요하다 (Jev 비용이 운영자 부담이므로) |
| 12 | 본문 추출 도입 | M4 품질 측정 후. 요약문이 거의 없는 소스(제목만 주는 피드)부터 검토 |
| 13 | 저가 모델 기반 기본 티어 | 보류 (사용자 결정) |

## 확인이 필요한 외부 사실

| # | 내용 |
|---|---|
| 14 | 각 언론사 RSS의 이용조건 (특히 AI 처리와 상업적 서비스에 대한 조항). 소스를 활성화하기 전에 확인한다 |
| 15 | Jev 공식 가격, 이용 경로(직접 API 대기열, AI Gateway 지원 여부), 요청당 토큰 상한(32k와 64k로 자료가 엇갈림), 질문 수 상한, Choice 선택지 상한. 현재 수치는 3자 자료 기준이다 |
| 16 | 네이버 검색 API 약관 개정 공식 원문. 보도 기준으로 결정했으며 원문은 직접 확인하지 못했다 |

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
