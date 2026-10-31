# ADR-0045: 운영자 API 는 `admin` 모듈이 내고, 각 모듈은 운영 기능과 결과 타입을 최상위 공개 API 로 낸다

- 상태: 승인
- 날짜: 2026-10-31
- 근거: [이슈 #25](https://github.com/Theia0382/yozm-news/issues/25) — 기점 `94495a8` 의 모듈 구조와 Spring Modulith 2.1.1 의 경계 검증. 구현 `f72eaa6`(닫힌 열거의 공개) · `7c19ca1`(`source` 공개 운영 API) · `231ab94`(`admin` 모듈)
- 관련: [ADR-0013](0013-architecture-patterns.md)(컨트롤러는 각 모듈 안, 공개 API · 이벤트로만 연결), [ADR-0034](0034-module-spec-as-public-interface-specification-and-docs-by-kind.md)(공개 인터페이스의 정본은 Spec), [ADR-0044](0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md)(응답 모양)

## 맥락

- 모듈 지도는 `admin` 을 운영자 기능(피드 · 태그 체계 · 평가 결과 · 사건)의 모듈로 두었지만, `admin` 이 다른 모듈의 운영 기능에 닿는 경계는 정하지 않았다.
- 운영자 화면은 여러 모듈에 걸친다 — 지금은 `source`(소스 · 피드), 다음은 수집 지표(`source` · `article`), 평가 결과 · 사건.
- `source` 의 운영 유스케이스와 결과 타입은 모두 하위 패키지(`application` · `domain/*`)에 있다. 다른 모듈의 하위 패키지 접근은 Modulith 검증이 막는다.
- 결과에는 닫힌 집합(켜기 거절 이유 · 활성화 기준 · 수집 실패 이유와 분류 · 건너뛴 이유 · 문서 문제 종류)이 든다.

## 결정

- **운영자 HTTP API(`/api/admin/**`)는 `admin` 모듈이 낸다.** `admin` 은 공개 타입이 없고, 각 모듈의 공개 운영 API 만 부르며, 의존을 `source` · `platform` 으로 선언한다(`@ApplicationModule(allowedDependencies)`). 컨트롤러는 트랜잭션을 열지 않는다.
- **각 모듈은 운영 기능을 최상위 패키지의 공개 인터페이스 하나로 낸다.** `source` 는 `SourceAdministration` — 목록 · 등록 · 원문 수정 · 원문 미리보기 · 켜기 · 끄기 · 수집 주기. 결과는 최상위 패키지의 공개 레코드 · sealed 타입이고, 도메인 결과를 옮기는 매핑은 그 모듈의 구현이 한다. 없는 대상은 공개 예외(`UnknownSourceException` · `UnknownFeedException`)다.
- **닫힌 열거는 사본 없이 최상위 패키지로 옮겨 공개한다.** 도메인이 그 열거를 그대로 쓴다.
- **공개 결과 타입은 HTTP 모양을 갖지 않는다.** 응답 모양(판별자 · ID 를 수로)은 `admin` 의 응답 레코드가 정한다. ID 가 없는 값 레코드(미리보기 · 실패 · 문제)는 응답에 그대로 싣는다.
- **화면 값의 정본은 그 모듈이다.** 예: 수집 주기의 하한은 `source` 의 공개 상수(`SourceAdministration.MINIMUM_POLL_INTERVAL_MINUTES`)이고, 그 출처는 프로필 모델의 하한 하나다.

## 결과

- 운영자 화면이 쓰는 모듈 기능이 공개 API 로 드러나고, Spec §3 이 그 선언을 갖는다 — `source` 의 공개 타입이 크게 는다.
- 도메인 결과와 공개 결과 사이에 매핑이 하나 생긴다. 열거는 매핑하지 않는다.
- 여러 모듈에 걸친 운영자 화면(수집 지표 등)이 `admin` 한 곳에 모인다.
- `admin` 의 웹 계층 시험은 공개 운영 API 의 대역을 쓴다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 각 모듈의 `adapter/in/web` 에 운영자 컨트롤러를 둔다 | 공개 표면은 늘지 않지만 운영자 엔드포인트가 모듈마다 흩어지고, 여러 모듈에 걸친 운영자 화면의 자리가 없다 |
| 모듈의 `domain` 하위 패키지를 named interface 로 연다 | 내부 타입이 공개 계약이 되어 내부 구조를 바꿀 때마다 공개 API 변경이 된다 |
| 닫힌 열거의 공개 사본을 두고 망라 `switch` 로 옮긴다 | 컴파일러가 어긋남을 잡아도 같은 집합의 두 번째 정본이다 |
| 공개 결과 타입에 Jackson 다형 애너테이션을 달아 응답으로 쓴다 | 모듈의 Java API 에 HTTP 직렬화 관심이 들어오고, 이벤트 직렬화에 쓰는 식별자 타입의 모양이 흔들린다 |
