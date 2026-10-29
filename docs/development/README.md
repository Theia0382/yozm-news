# 개발 인프라

개발 · 검사 환경을 만드는 쪽의 문서다. 문서 위계는 [docs/README.md §1](../README.md#1-문서-위계)에 있다.

- **담는 것**: 개발용 Compose 구성, 검사 정의(`compose.check.yaml`)와 검사 이미지, CI(`.github/workflows/ci.yml`), 이미지 · Dockerfile 구성, 개발용 로컬 대체물의 구성
- **담지 않는 것**: 개발 절차와 실행 명령([CONTRIBUTING.md](../../CONTRIBUTING.md)), 무엇을 테스트 증거로 인정하는가([qa/test-strategy.md](../qa/test-strategy.md)), 셀프 호스팅 운영([operations/](../operations/README.md)), 모듈의 설계([specs/](../specs/README.md))

아직 구현되지 않은 항목은 앞에 **(미구현)** 을 적는다 — 표기는 [Spec 쓰는 규칙](../specs/README.md#3-쓰는-규칙)과 같다.

## 문서 목록

| 문서 | 내용 |
|---|---|
| [infrastructure.md](infrastructure.md) | 개발용 Compose · 검사 환경 · 이미지 · CI 의 구성과 그 불변식 |
