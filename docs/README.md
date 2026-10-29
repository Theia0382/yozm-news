# 요즘뉴스 문서

요즘뉴스의 설계 문서 진입점이다. 문서 위계 · 문서 목록 · 갱신 트리거 · 작성 규칙을 담는다. 개발 절차(브랜치 · 커밋 · PR · 개발 환경)는 [CONTRIBUTING.md](../CONTRIBUTING.md)에 있다.

## 1. 문서 위계

지식의 종류마다 한 층이 소유한다. 같은 지식을 두 층에 적지 않는다. 예외는 둘이다 — 모듈 공개 인터페이스는 2층(Spec)이 정본이고 3층(코드)의 선언이 사본이다. 모듈 테이블의 컬럼은 Flyway 마이그레이션이 정본이고 Spec 의 데이터 절에 주요 컬럼의 사본이 있다.

| 층 | 문서 | 소유하는 지식 |
|---|---|---|
| **1층 개요** | [overview.md](overview.md) | 목적 · 범위 · 품질 목표 · 시스템 구성 · 모듈 지도 · 횡단 규약 · 용어 |
| **2층 모듈 Spec** | [specs/](specs/README.md) | 모듈의 공개 인터페이스(시그니처 포함) · 이벤트 계약, 규칙 · 불변식, 작업, 데이터, 모듈 용어. 공개 인터페이스의 정본이고 코드는 Spec 을 따른다. 새 모듈은 Spec 을 먼저 만든다 |
| **3층 코드** | Javadoc · 테스트 · 코드 | 클래스 구조 · 알고리즘 상세 · 비공개 시그니처의 정본. 모듈 공개 인터페이스의 선언은 2층의 사본이다 |
| **개발 인프라** | [development/](development/README.md) | 개발 · 검사 환경, CI, 이미지 구성 |
| **운영** | [operations/](operations/README.md) | 셀프 호스팅 운영자가 쓰는 배포 · 설정 · 백업 · 관측 |
| **결정 원장** | [adr/](adr/README.md) | 결정의 맥락 · 대안 · 채택 근거 · 결과. 프로젝트 결정만 담는다 |
| **QA** | [qa/](qa/test-strategy.md) | 무엇을 테스트 증거로 인정하는가 |
| **조사 자료** | [research/](research/) | 결정에 쓰인 사전 조사. 작성 시점의 사실이다 |
| **동결 자료** | [archives/](archives/README.md) | 현행 규범이 아닌 과거 문서. 인용하지 않는다 |

**전환 중인 문서**는 위 갈래가 생기기 전에 쓴 설계 초안이다. 남은 내용은 현행 설계로 읽고, 아래 행선지로 옮긴다. 다 옮긴 문서는 `archives/` 로 보낸다.

| 문서 | 남은 내용의 행선지 |
|---|---|
| [architecture.md](architecture.md) | 모듈 포트 · 작업 → 그 모듈의 Spec(모듈을 만드는 이슈가 옮긴다). 외부 API · MCP → 1층 또는 그 기능을 가진 모듈의 Spec(그 기능을 설계하는 이슈가 고른다) |
| [pipeline.md](pipeline.md) | 단계마다 그 단계를 가진 모듈의 Spec. 상단의 단계 흐름 그림은 이 문서를 `archives/` 로 보낼 때 `overview.md` 로 옮긴다 |
| [data-model.md](data-model.md) | 모듈 테이블 → 그 모듈 Spec 의 데이터 절. 모듈 사이 관계(ER 개요) · 보존 정책 · 프레임워크 테이블은 이 문서에 남는다 — 전환이 끝나면 데이터 개요 문서로 남는다 |
| [security.md](security.md) | 한 모듈이 구현하는 규칙 → 그 모듈의 Spec. 횡단 정책(인가 · 데이터 격리 · 개인정보 · 저작권 · 악용 방지)은 이 문서에 남는다 — 전환이 끝나면 횡단 보안 문서로 남는다 |
| [infrastructure.md](infrastructure.md) | `development/`(개발 · 검사 · CI) · `operations/`(운영)로 나눈다 |

[roadmap.md](roadmap.md) · [open-questions.md](open-questions.md) 는 계획 문서라 전환 대상이 아니다.

## 2. 문서 목록

종류 · 독자별로 묶는다. `specs/` · `development/` · `operations/` · `adr/` · `archives/` 의 문서 목록은 그 디렉터리의 `README.md` 에 있다.

| 묶음 | 문서 | 내용 |
|---|---|---|
| 처음 읽기 | [overview.md](overview.md) | 시스템 개요 (1층) |
| 설계 | [specs/](specs/README.md) | 모듈 Spec (2층) — 목록 · 공통 구조 · 쓰는 규칙 |
| | [adr/](adr/README.md) | 아키텍처 결정 기록 |
| | [architecture.md](architecture.md) | 모듈 인터페이스 초안, 백그라운드 작업, 외부 API · MCP (전환 중) |
| | [pipeline.md](pipeline.md) | 수집 → 평가 → 사건 묶기 → 다이제스트 생성 파이프라인, 관심 신호 · 이슈 · 변화 리포트 상세 (전환 중) |
| | [data-model.md](data-model.md) | 데이터 모델과 보존 정책 (전환 중) |
| | [security.md](security.md) | 인증 · 인가, BYOK 키 보관, 저작권 · 개인정보 (전환 중) |
| 개발 | [CONTRIBUTING.md](../CONTRIBUTING.md) | 개발 환경 · 검사 명령, 이슈 · 브랜치 · 커밋 · PR 규약 |
| | [development/](development/README.md) | 개발 인프라 — 개발 · 검사 환경, CI, 이미지 구성 |
| | [qa/test-strategy.md](qa/test-strategy.md) | 테스트 전략 |
| 운영 | [operations/](operations/README.md) | 셀프 호스팅 운영 |
| | [infrastructure.md](infrastructure.md) | 배포 토폴로지, 호스팅, 운영, CI (전환 중) |
| 계획 | [roadmap.md](roadmap.md) | 마일스톤 |
| | [open-questions.md](open-questions.md) | 미결 사항과 권장안 |
| 자료 | [research/](research/) | 사전 조사 자료 |
| | [archives/](archives/README.md) | 동결 자료 (원본 기획 초안 등) |

## 3. 갱신 트리거

문서 갱신은 **변경 영향**이 결정한다. 변경이 아래 트리거에 해당하면 해당 문서 갱신은 **그 작업의 산출물에 포함된다**(별도 문서 작업으로 미루지 않는다).

| 트리거 (변경 유형) | 갱신 대상 |
|---|---|
| 모듈 신설 · 폐지 · 의존 방향 변경 | `overview.md` §5 모듈 지도 |
| 모듈 신설 | 그 모듈의 Spec(`specs/{모듈}.md`) 신설 + [specs/README.md](specs/README.md) §1 목록 — 구현보다 먼저 만든다([CONTRIBUTING.md §2](../CONTRIBUTING.md#2-이슈)) |
| 모듈 공개 인터페이스 · 이벤트 · 작업 · 규칙 · 불변식 변경 | 해당 모듈 Spec + 의존하는 모듈 Spec |
| 횡단 규약 변경 (트랜잭션 경계 · 사용자 범위 · 비밀 값 · 오류 응답 형식 등) | `overview.md` §6 + 관련 ADR |
| 도메인 용어 신설 · 이름 변경 | 모듈 Spec 의 용어 절, 횡단 용어면 `overview.md` §8 |
| 파이프라인 · 데이터 모델 · 보안 설계 변경 | 한 모듈에 속하면 그 모듈 Spec, 아니면 `pipeline` · `data-model` · `security`(§1 전환 중인 문서의 행선지) |
| 개발 인프라 변경 (개발 환경 · 검사 정의 · CI · 이미지) | `development/` |
| 운영 변경 (배포 구성 · 운영 설정 · 호스팅 · 백업) | `operations/` — 지금 그 내용은 `infrastructure.md` 에 있다 |
| DB 스키마 변경 | Flyway 마이그레이션이 정본 + 그 모듈 Spec 의 데이터 절(주요 컬럼 · 의미 · 제약의 뜻) |
| 확정된 프로젝트 결정 (ADR 선별 기준 충족) | ADR 신설 + [adr/README.md](adr/README.md) 표 행 + 관련 문서의 근거 링크 |
| 결정 역전 | 새 ADR + 구 ADR 의 상태 줄 + 표 행 |
| 미결 사항 결정 | [open-questions.md](open-questions.md) 정리 + 해당 문서 · ADR |
| 테스트 규범 변경 | [qa/test-strategy.md](qa/test-strategy.md) |
| 개발 절차 변경 (브랜치 · 커밋 · PR · 이슈 · 개발 환경) | [CONTRIBUTING.md](../CONTRIBUTING.md) |
| 로드맵 · 범위 변경 | [roadmap.md](roadmap.md) · `overview.md` §2 |
| 문서 이동 · 이름 변경 · 동결 | 이 문서의 §2 목록 + 깨진 링크 없음 확인 + 동결이면 [archives/README.md](archives/README.md) |
| 이 표 자체의 변경 | 이 절 |

표에 **없는** 변경(클래스 내부 구조 · 알고리즘 상세 · 비공개 시그니처)은 문서 갱신 의무가 없다 — 3층(Javadoc · 테스트)이 코드와 함께 갱신된다.

## 4. 작성 규칙

- **무버전 단일 파일** — 개정은 현행 파일을 직접 고친다. 버전을 붙인 새 파일을 만들지 않는다. 과거 상태는 git 이력이 보관한다
- **현재 상태 · 자기완결** — 참조 문서는 현재 상태만 쓴다. "~에서 바뀜" 같은 변경 이력, 이슈 번호에 기댄 서술을 넣지 않는다. 읽는 자리에서 조건과 귀결을 이해할 수 있게 쓴다
- **절 번호 유지** — 다른 문서 · 코드 주석이 `overview.md §5` 처럼 절을 인용한다. 개정할 때 기존 절 번호를 유지하고, 새 절은 끝에 붙인다
- **ADR 은 기록이다** — 본문은 등재한 작업의 PR 이 merge 되기 전까지만 고친다. merge 뒤에는 상태 줄 갱신 · 말미 후속 기록 · 형식 정정(링크 · 오탈자)만 한다. 결정을 뒤집을 때는 새 ADR 을 쓰고 구 ADR 의 상태 줄만 고친다
- **링크는 상대 경로** — GitHub 에서 열리도록 문서 위치 기준 상대 경로와 `.md` 확장자를 쓴다
- **동결 자료** — `archives/` 의 본문은 고치지 않는다. 경로가 바뀌어 깨진 링크의 경로 정정만 한다
- **모듈 Spec** — 공통 구조와 쓰는 규칙(경계 · 규칙 식별자 · 미구현 표시)은 [specs/README.md](specs/README.md)
