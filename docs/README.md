# 요즘뉴스 설계 문서

AI를 활용해 뉴스 기사를 수집·분류하고, 사용자마다 개인화된 일일 다이제스트를 만들어 웹으로 제공하는 공개 서비스의 설계 문서 모음이다.

- 원본 기획 초안: [00-proposal.md](00-proposal.md) (2026-10-06 작성. 이후 결정으로 달라진 점은 [01-overview.md](01-overview.md) 참고)
- 문서 상태: 초안 (2026-10-25 작성)

## 읽는 순서

| 문서 | 내용 |
|---|---|
| [00-proposal.md](00-proposal.md) | 원본 기획 초안 (보존용, 수정하지 않음) |
| [01-overview.md](01-overview.md) | 목표, 범위, 설계 원칙, 기획 초안과 달라진 점 |
| [02-architecture.md](02-architecture.md) | 시스템 구성, 모듈 구조, 핵심 인터페이스, 런타임 역할 |
| [03-pipeline.md](03-pipeline.md) | 수집 → 평가 → 다이제스트 생성 파이프라인 상세 |
| [04-data-model.md](04-data-model.md) | 데이터 모델과 보존 정책 |
| [05-infrastructure.md](05-infrastructure.md) | 배포 토폴로지, 호스팅, 운영 |
| [06-security.md](06-security.md) | 인증·인가, BYOK 키 보관, 저작권·개인정보 |
| [07-roadmap.md](07-roadmap.md) | 마일스톤 |
| [open-questions.md](open-questions.md) | 미결 사항과 권장안 |
| [adr/](adr/) | 아키텍처 결정 기록 |
| [research/](research/) | 사전 조사 자료 |

## 용어

| 용어 | 뜻 |
|---|---|
| 소스(Source) | 언론사 등 기사 발행 주체 |
| 피드(Feed) | 소스가 제공하는 RSS/Atom 피드 하나. 한 소스가 여러 피드를 가질 수 있다 |
| 기사(Article) | 중복 제거된 기사 1건. 여러 피드에서 같은 기사가 들어와도 1건으로 저장한다 |
| 공유 평가 | 사용자와 무관하게 기사마다 한 번만 수행하는 Jev 평가(태그·형식·중요도). 비용은 운영자가 부담 |
| 태그 체계(Taxonomy) | 운영자가 관리하는 통제 어휘. 주제(2단계)·지역·형식 축으로 나뉘고 버전을 가진다. 기사에는 여러 태그가 붙을 수 있다 |
| 후보(Candidate) | 다이제스트 생성 시 한 사용자에게 LLM 입력으로 넘기는 기사 집합 |
| 다이제스트(Digest) | 한 사용자의 한 기간(기본: 하루)에 대해 LLM이 여러 기사를 종합해 만든 결과물 |
| BYOK | Bring Your Own Key. 사용자가 자기 LLM API 키를 등록해 다이제스트 생성 비용을 직접 부담하는 방식 |
