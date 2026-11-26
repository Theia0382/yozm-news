# 운영

서비스를 띄우고 운영하는 쪽의 문서다. 문서 위계는 [docs/README.md §1](../README.md#1-문서-위계)에 있다.

담는 것:

- 배포 구성 실행과 호스팅 · 외부 서비스 연결
- 운영 설정(환경 변수) 레퍼런스와 비밀 값 관리
- 백업 · 복구와 관측
- 소스 등록 · 태그 체계 바꾸기 · 사건과 흐름 화면 읽기 같은 운영 작업의 안내

담지 않는 것:

- 운영 설정이 바꾸는 규칙의 정의 — 그 규칙을 가진 모듈의 [Spec](../specs/README.md)에 있다. 모듈에 속하지 않는 앱 전체 설정은 운영 문서가 뜻까지 갖는다
- 개발 · 검사 환경([development/](../development/README.md))
- 판단의 이유([adr/](../adr/README.md))

아직 구현되지 않은 항목은 앞에 **(미구현)** 을 적는다. 표기는 [Spec 쓰는 규칙](../specs/README.md#3-쓰는-규칙)과 같다.

## 문서 목록

| 문서 | 내용 |
|---|---|
| [deployment.md](deployment.md) | 배포 구성을 띄우고 업데이트하기, 관측과 백업 · 복구 |
| [configuration.md](configuration.md) | 운영 설정 레퍼런스 · 비밀 값 · 데이터 보존 |
| [sources.md](sources.md) | 소스 등록 · 켜기, 수집 프로필 작성과 예시 |
| [tag-sets.md](tag-sets.md) | 태그 체계를 새 버전으로 바꾸기, 평가 결과와 표본 검수, 태그 기준 · 저장 기준 바꾸기 |
| [events.md](events.md) | 사건 · 흐름 · 큰 화제 화면 읽기와 기준 조정 |
| [event-evaluation.md](event-evaluation.md) | 사건 묶기 평가하기 — 사건 묶기를 정답 라벨로 재고 기준끼리 견주기 |
| [storyline-evaluation.md](storyline-evaluation.md) | 흐름 평가하기 — 흐름 잇기 · 관계 · 큰 화제를 정답 라벨과 견줘 재기 |
| [measurement-clock.md](measurement-clock.md) | 측정용 시계 쓰기 — 지난 데이터를 복원한 일회용 인스턴스에서 시계를 하루씩 넘기기 |
