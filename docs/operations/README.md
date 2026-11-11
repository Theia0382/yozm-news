# 운영

서비스를 띄우고 운영하는 쪽의 문서다. 문서 위계는 [docs/README.md §1](../README.md#1-문서-위계)에 있다.

- **담는 것**: 배포 구성 실행, 운영 설정(환경 변수) 레퍼런스, 호스팅 · 외부 서비스 연결, 비밀 값 관리, 백업 · 복구, 관측, 소스 등록 · 태그 체계 바꾸기 · 사건 화면 읽기 같은 운영 작업의 안내
- **담지 않는 것**: 운영 설정이 바꾸는 규칙의 정의(그 규칙을 가진 모듈의 [Spec](../specs/README.md) — 모듈에 속하지 않는 앱 전체 설정은 이 갈래가 뜻까지 갖는다), 개발 · 검사 환경([development/](../development/README.md)), 판단의 이유([adr/](../adr/README.md))

아직 구현되지 않은 항목은 앞에 **(미구현)** 을 적는다 — 표기는 [Spec 쓰는 규칙](../specs/README.md#3-쓰는-규칙)과 같다.

## 문서 목록

| 문서 | 내용 |
|---|---|
| [deployment.md](deployment.md) | 배포 구성 실행 · 업데이트 · 헬스 체크와 관측 · 공개 경로와 외부 서비스 · 백업과 복구 |
| [configuration.md](configuration.md) | 운영 설정 레퍼런스 · 비밀 값 · 데이터 보존 |
| [sources.md](sources.md) | 소스 등록 · 켜기, 수집 프로필 작성과 예시 |
| [tag-sets.md](tag-sets.md) | 태그 체계 바꾸기 — 원문의 확인 · 활성화, 계보, 재평가 기간과 예시, 평가 결과 · 표본 검수, 태그 기준 · 저장 기준 바꾸기 |
| [events.md](events.md) | 사건 화면 읽기 — 사건 목록 · 상세, 판정 기록, 큰 사건, 큰 사건 기준 · 사건 배정 기준 조정 |
| [event-evaluation.md](event-evaluation.md) | 사건 묶기 평가하기 — 평가 도구, 표본 고르기, 라벨 기준, 측정과 기준 비교, 파일 · 결과 기록 형식 |
| [storyline-evaluation.md](storyline-evaluation.md) | 스토리라인 평가하기 — 재는 것, 평가 인스턴스, 표본과 씨앗, 라벨 기준, 대조 검토와 측정, 결과 파일 · 파일 · 결과 기록 형식 |
