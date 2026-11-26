# 요즘뉴스 문서

요즘뉴스의 설계 문서 진입점이다. 문서 위계 · 문서 목록 · 갱신 트리거 · 작성 규칙을 담는다. 개발 절차(브랜치 · 커밋 · PR · 개발 환경)는 [CONTRIBUTING.md](../CONTRIBUTING.md)에 있다.

## 1. 문서 위계

지식의 종류마다 한 층이 소유한다. 같은 지식을 두 층에 적지 않는다. 예외는 셋이다.

- 모듈 공개 인터페이스와 포트의 선언은 2층(Spec)이 정본이고, 3층(코드)의 선언은 사본이다
- 모듈 테이블의 컬럼은 Flyway 마이그레이션이 정본이고, Spec 의 데이터 절에 주요 컬럼의 사본이 있다
- 코드 주석은 정본의 위치를 가리킨다. Spec 이 정본인 공개 타입 · 포트 · 구성 요소에는 한 줄 요약을 함께 두고, 개발 인프라 · 운영 문서가 정본인 구성 파일에는 그 자리의 제약 한 문장을 함께 둔다. 그 밖의 지식은 위치로만 가리킨다([ADR-0039](adr/0039-code-comments-point-to-owning-docs-and-javadoc-format-checked-by-javac-and-javadoc.md))

| 층 | 문서 | 소유하는 지식 |
|---|---|---|
| **1층 개요** | [overview.md](overview.md) | 목적과 범위, 품질 목표, 시스템 구성과 모듈 지도, 횡단 규약과 용어 |
| **2층 모듈 · 화면 Spec** | [specs/](specs/README.md) | 모듈 Spec 은 모듈의 책임과 용어, 공개 인터페이스(시그니처 포함)와 이벤트 계약, 내부 구조(구성 요소와 그 책임 · 협력)와 포트 선언, 규칙과 불변식과 데이터를 갖는다. 공개 인터페이스와 포트 선언의 정본이고 코드는 Spec 을 따른다. 새 모듈은 Spec 을 먼저 만든다. 화면 Spec([specs/web.md](specs/web.md))은 프론트엔드의 화면 이름 · 화면 경로 · 화면 동작을 갖는다 |
| **3층 코드** | Javadoc · 테스트 · 코드 | 클래스 내부 상세(필드 · 메서드 · 알고리즘) · 비공개 시그니처의 정본. 모듈 공개 인터페이스 · 포트의 선언은 2층의 사본이다. 주석의 규칙은 [CONTRIBUTING.md §7](../CONTRIBUTING.md#주석) |
| **개발 인프라** | [development/](development/README.md) | 개발 · 검사 환경, CI, 이미지 구성 |
| **운영** | [operations/](operations/README.md) | 셀프 호스팅 운영자가 쓰는 배포 · 설정 · 백업 · 관측 |
| **결정 원장** | [adr/](adr/README.md) | 결정의 맥락 · 대안 · 채택 근거 · 결과. 프로젝트 결정만 담는다 |
| **QA** | [qa/](qa/test-strategy.md) | 무엇을 테스트 증거로 인정하는가 |
| **조사 자료** | [research/](research/) | 결정에 쓰인 사전 조사. 작성 시점의 사실이다 |
| **동결 자료** | [archives/](archives/README.md) | 현행 규범이 아닌 과거 문서. 인용하지 않는다 |

**전환 중인 문서**는 위 갈래가 생기기 전에 쓴 설계 초안이다. 남은 내용은 현행 설계로 읽고, 아래 행선지로 옮긴다. 다 옮긴 문서는 `archives/` 로 보낸다.

| 문서 | 남은 내용의 행선지 |
|---|---|
| [security.md](security.md) | 한 모듈이 구현하는 규칙 → 그 모듈의 Spec. 횡단 정책(인가 · 데이터 격리 · 개인정보 · 저작권 · 악용 방지)은 이 문서에 남는다 — 전환이 끝나면 횡단 보안 문서로 남는다 |

[roadmap.md](roadmap.md) · [open-questions.md](open-questions.md) 는 계획 문서라 전환 대상이 아니다.

## 2. 문서 목록

종류 · 독자별로 묶는다. 하위 디렉터리 가운데 `README.md` 가 있는 것은 그 파일이 문서 목록을 갖는다.

| 묶음 | 문서 | 내용 |
|---|---|---|
| 처음 읽기 | [overview.md](overview.md) | 시스템 개요 (1층) |
| 설계 | [specs/](specs/README.md) | 모듈 · 화면 Spec (2층)의 목록 · 공통 구조 · 쓰는 규칙 |
| | [adr/](adr/README.md) | 아키텍처 결정 기록 |
| | [data-model.md](data-model.md) | 모듈 사이 데이터 관계 · 보존 정책 · 프레임워크 테이블 |
| | [security.md](security.md) | 인증과 인가, BYOK 키 보관, 저작권과 개인정보 (전환 중) |
| 개발 | [CONTRIBUTING.md](../CONTRIBUTING.md) | 개발 환경과 검사 명령, 이슈 · 브랜치 · 커밋 · PR 규약 |
| | [development/](development/README.md) | 개발 인프라: 개발 · 검사 환경, CI, 이미지 구성 |
| | [qa/test-strategy.md](qa/test-strategy.md) | 테스트 전략 |
| 운영 | [operations/](operations/README.md) | 셀프 호스팅 운영 |
| 계획 | [roadmap.md](roadmap.md) | 마일스톤 |
| | [open-questions.md](open-questions.md) | 미결 사항과 권장안 |
| 자료 | [research/](research/) | 사전 조사 자료 |
| | [archives/](archives/README.md) | 동결 자료 (원본 기획 초안 등) |

## 3. 갱신 트리거

문서 갱신은 **변경 영향**이 결정한다. 변경이 아래 트리거에 해당하면 해당 문서 갱신은 **그 작업의 산출물에 포함된다**. 별도 문서 작업으로 미루지 않는다.

| 트리거 (변경 유형) | 갱신 대상 |
|---|---|
| 모듈 신설 · 폐지 · 의존(방향 · 공개 API) 변경 | `overview.md` §5 모듈 지도 |
| 모듈 신설 | 그 모듈의 Spec(`specs/{모듈}.md`) 신설 + [specs/README.md](specs/README.md) §1 목록. Spec 은 구현보다 먼저 만든다([CONTRIBUTING.md §2](../CONTRIBUTING.md#2-이슈)) |
| 모듈 공개 인터페이스 · 이벤트 · 포트 변경 | 해당 모듈 Spec + 의존하는 모듈 Spec |
| 모듈의 작업 · 규칙 · 불변식 변경 | 해당 모듈 Spec + 의존하는 모듈 Spec |
| 모듈 내부 구성 요소(Spec 내부 구조 절에 있는 것)의 신설 · 삭제 · 이름 변경 · 책임 이동 | 그 모듈 Spec 의 내부 구조 절 |
| 횡단 규약 변경 (트랜잭션 경계 · 사용자 범위 · 비밀 값 · 오류 응답 형식 등) | `overview.md` §6 + 관련 ADR |
| 도메인 용어 신설 · 이름 변경 | 모듈 Spec 의 용어 절, 횡단 용어면 `overview.md` §8 |
| 화면 신설 · 화면 동작 · 화면 이름 변경 | 화면 Spec([specs/web.md](specs/web.md)) |
| 여러 모듈을 잇는 흐름 · 데이터 모델 · 보안 설계 변경 | 한 모듈에 속하면 그 모듈 Spec. 여러 모듈을 잇는 흐름이면 `overview.md` §1, 모듈 사이 데이터 관계 · 보존이면 `data-model`, 보안 횡단 정책이면 `security` |
| 개발 인프라 변경 (개발 환경 · 검사 정의 · CI · 이미지) | `development/`. 문서 검사의 판정이 바뀌면 §4 의 링크 규칙 |
| 운영 변경 (배포 구성 · 운영 설정 · 호스팅 · 백업) | `operations/` |
| DB 스키마 변경 | Flyway 마이그레이션이 정본 + 그 모듈 Spec 의 데이터 절(주요 컬럼 · 의미 · 제약의 뜻) |
| 확정된 프로젝트 결정 ([ADR 선별 기준](adr/README.md#선별-기준) 충족) | ADR 신설 + [adr/README.md](adr/README.md) 표 행 + 관련 문서의 근거 링크 |
| 결정 역전 · 보충 ([대체와 보충](adr/README.md#대체와-보충)) | 새 ADR + 구 ADR 에 알림. 대체면 상태 줄 · 표 행(부분 대체면 대체 표시도), 보충이면 후속 기록 |
| 미결 사항 결정 | [open-questions.md](open-questions.md) 정리 + 해당 문서 · ADR |
| 테스트 규범 변경 | [qa/test-strategy.md](qa/test-strategy.md) |
| 개발 절차 변경 (이슈 · 브랜치 · 커밋 · PR 규약, 개발 환경) | [CONTRIBUTING.md](../CONTRIBUTING.md) |
| 로드맵 · 범위 변경 | [roadmap.md](roadmap.md) · `overview.md` §2 |
| 문서 이동 · 이름 변경 · 동결 | 이 문서의 §2 목록 + 깨진 링크 없음 확인 + 동결이면 [archives/README.md](archives/README.md) |
| 이 표 자체의 변경 | 이 절 |

표에 **없는** 변경은 문서 갱신 의무가 없다. 클래스 내부 상세 · 알고리즘 · 비공개 시그니처와 내부 구조 절에 없는 보조 타입이 그렇다. 이것들은 3층(Javadoc · 테스트)이 코드와 함께 갱신된다.

## 4. 작성 규칙

- **무버전 단일 파일** — 개정은 현행 파일을 직접 고친다. 버전을 붙인 새 파일을 만들지 않는다. 과거 상태는 git 이력이 보관한다
- **현재 상태 · 자기완결** — 참조 문서는 현재 상태만 쓴다. "~에서 바뀜" 같은 변경 이력, 이슈 번호에 기댄 서술을 넣지 않는다. 읽는 자리에서 조건과 귀결을 이해할 수 있게 쓴다
- **절 번호 유지** — 다른 문서 · 코드 주석이 `overview.md §5` 처럼 절을 인용한다. 개정할 때 기존 절 번호를 유지하고, 새 절은 끝에 붙인다
- **ADR 은 기록이다** — 본문은 등재한 작업의 PR 이 merge 되기 전까지만 고친다. merge 뒤에는 상태 줄 갱신 · 대체 표시 · 말미 후속 기록 · 형식 정정(링크 · 오탈자)만 한다. 결정을 바꿀 때는 새 ADR 을 쓰고 구 ADR 에 알린다([대체와 보충](adr/README.md#대체와-보충))
- **한 말에 한 뜻** — 개발 용어는 코드 이름 하나의 뜻으로만 쓴다. 같은 말을 다른 뜻으로 쓰지 않고, 정의된 말과 겹치는 일반어도 피한다. 용어의 정본은 그 개념을 소유한 모듈의 Spec §2 다. 주인을 한 모듈로 정할 수 없는 횡단 용어의 정본은 `overview.md` §8 이고, 화면 이름의 정본은 화면 Spec 의 §2 다([ADR-0331](adr/0331-a-development-term-has-only-the-meaning-of-one-code-name-and-words-with-several-meanings-are-renamed-per-meaning.md) · [ADR-0333](adr/0333-a-term-is-defined-in-the-spec-of-the-module-owning-the-concept-and-only-cross-cutting-terms-go-to-the-overview.md))
- **판단의 이유와 측정 기록** — 판단의 이유와 실측 결과는 그 판단을 정한 ADR 이 갖는다. 본문에 없는 실측은 그 ADR 의 말미 후속 기록에 남긴다. 그 판단을 정한 ADR 이 없으면 문서에 쓰지 않는다. 아직 재지 않은 잠정값에는 값 옆에 `(측정 전)` 만 단다([ADR-0332](adr/0332-reasons-and-measurements-belong-to-the-deciding-adr-are-dropped-without-one-and-unmeasured-values-are-marked.md))
- **문장 꼴** — 리뷰가 읽어 보고 판정한다. 길이(글자 수) 상한은 두지 않는다
  - 한 문단과 한 표 칸은 한 가지를 말한다
  - 괄호는 한 겹까지 쓰고, 괄호 안에 줄표를 넣지 않는다
  - 한 문장에 줄표는 하나까지 쓴다. 정의를 끝낸 문장 뒤에 다른 사실을 줄표로 덧붙이지 않고 문장을 나눈다
  - 넷 넘게 잇는 나열은 " · " 로 잇든 쉼표로 잇든 목록이나 표로 쓴다. 코드 이름 · 경로 · 값 · 문서 링크 같은 식별자의 나열은 표 칸이든 문장이든 예외다. 표 칸의 설명 항목 나열은 표 밖 목록으로 뺀다
- **한 문서 안의 같은 계약** — 한 곳에만 쓰고, 다른 자리는 그 자리를 규칙 식별자나 절로 가리킨다
- **링크는 상대 경로** — GitHub 에서 열리도록 문서 위치 기준 상대 경로와 `.md` 확장자를 쓴다. 문서 검사(`docs`, [CONTRIBUTING.md §1](../CONTRIBUTING.md#1-개발-환경))가 링크를 아래 형식으로 읽어 확인한다
  - 대상은 저장소 안의 커밋된 경로다. 문서 밖 경로는 검사 이미지의 허용 목록에도 있어야 한다(더하는 방법은 같은 절)
  - `#앵커` 는 대상 문서의 `#` 제목이 만드는 앵커다(GitHub 규칙). 밑줄로 그은 제목은 검사가 제목으로 읽지 않으므로, 링크로 가리킬 제목은 `#` 으로 쓴다
  - 참조형 링크 · HTML `<a href>` · 저장소 루트 기준 경로(`/…`)는 쓰지 않는다
  - 위 셋을 어기면 검사가 실패한다
  - 대상에 공백이 있으면 `<…>` 로 감싸고, 대상의 괄호는 한 겹까지 쓴다. 이것을 어긴 링크는 링크로 읽히지 않아 실패 없이 검사를 빠져나간다
- **동결 자료** — `archives/` 의 본문은 고치지 않는다. 경로가 바뀌어 깨진 링크의 경로 정정만 한다
- **모듈 · 화면 Spec** — 공통 구조와 쓰는 규칙은 [specs/README.md](specs/README.md)에 있다. 정하는 것은 아래다
  - 경계와 코드 이름
  - 규칙 식별자와 규칙의 크기 · 배치 · 폐지
  - 미구현 · 측정 전 표시
  - 다이어그램과 하위 제목
  - 화면 동작의 자리
