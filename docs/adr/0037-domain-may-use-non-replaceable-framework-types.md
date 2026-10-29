# ADR-0037: `domain` 은 교체하지 않는 프레임워크의 타입을 쓸 수 있고, 그 단위 테스트는 스프링 컨텍스트 없이 돈다

- 상태: 승인
- 날짜: 2026-10-29
- 근거: [이슈 #48](https://github.com/Theia0382/yozm-news/issues/48) — 기점 `7c15503` 의 `platform` · `source` `domain` 패키지와 그 테스트를 대조
- 관련: [ADR-0013](0013-architecture-patterns.md)(모듈 내부 구조 · 테스트 수준 — 이 ADR 이 읽는 법을 정한다), [ADR-0014](0014-spring-data-jdbc.md)(Spring Data JDBC aggregate), [ADR-0023](0023-jobrunr-oss-framework-schema-and-event-resubmission.md)(이벤트 발행 재처리)

## 맥락

- ADR-0013 의 테스트 표는 `domain` 의 단위 테스트를 "스프링 없이 순수 Java"라고 적었다. 같은 ADR 은 "aggregate를 Spring Data JDBC 엔티티로 그대로 쓰고, 저장소 인터페이스는 `domain`에 둔다"고도 정했다 — `domain` 이 스프링 타입을 쓰는 것을 이미 허용한다.
- `platform` 의 `EventResubmissionPolicy`(`domain`)는 Spring Modulith 의 `EventPublication` 을 받아 그 상태를 `default` 없는 망라 `switch` 로 분기해 재처리 대상을 고른다. Modulith 가 상태를 더하면 컴파일 오류가 난다. 그 단위 테스트는 스프링 컨텍스트 없이 돈다.
- `source` 의 `domain/registry` 는 aggregate `Source` · `Feed` 와 저장소 둘에서 Spring Data JDBC 타입을 쓴다.
- 포트는 외부 의존에만 둔다(ADR-0013). 교체 대상은 뉴스 공급원 · 평가기 · LLM · 메일 · 봇 차단 · 외부 로그인이다. Spring Data JDBC · Spring Modulith 는 이 프로젝트가 고른 영속성 · 모듈 프레임워크라 교체 대상이 아니다(ADR-0004 · ADR-0013 · ADR-0014).

## 결정

- **`domain` 은 교체하지 않는 프레임워크(Spring Data JDBC · Spring Modulith)의 타입을 쓸 수 있다.** 그 타입을 감싸는 포트나 자체 사본 타입을 두지 않는다.
- **ADR-0013 의 "단위 — `domain` — 스프링 없이 순수 Java"는 "`domain` 의 단위 테스트는 스프링 컨텍스트 없이 돈다"로 읽는다.** 프레임워크 타입을 쓰는 `domain` 클래스도 컨텍스트 없이 단위 테스트한다.
- 교체 대상인 외부 의존의 타입은 지금처럼 `domain` 에 들이지 않는다 — 포트 뒤에 둔다(ADR-0013).

## 결과

- 판단이 `domain` 에 남는다. 프레임워크가 주는 상태 · 타입으로 바로 분기하고, 망라 `switch` 가 프레임워크 쪽 변경을 컴파일 오류로 드러낸다.
- `domain` 을 프레임워크 없이 컴파일할 수는 없다. 그 프레임워크를 바꾸는 일은 `domain` 까지 고치는 변경이다 — 교체 대상이 아니라서 감수한다.
- 새 `domain` 코드가 프레임워크 타입을 쓸 때 기준은 "교체 대상인가"다. 교체 대상이면 포트, 아니면 그대로 쓴다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 프레임워크 타입을 감싸는 포트를 `domain` 에 둔다 | 교체하지 않는 것을 포트로 감싸는 것은 ADR-0013 이 피하려던 군더더기다 |
| 상태 분기를 `application` 으로 옮긴다 | 재처리 대상을 고르는 판단이 `domain` 밖으로 나가 스프링 없이 단위 테스트할 자리를 잃는다 |
| 자체 상태 타입을 두고 프레임워크 상태를 매핑한다 | 프레임워크 상태 집합의 사본이 생기고, 망라 `switch` 가 지키던 "상태가 늘면 컴파일 오류"가 매핑 코드로 옮겨 간다 |
| ADR-0013 말미 후속 기록에 이 해석을 적는다 | 기준을 넓히는 해석이라 맥락 · 대안을 갖춘 결정 기록이 맞다. 후속 기록은 다른 ADR 을 가리키는 자리이고, 해석 본문을 쓰면 merge 뒤 본문을 고치지 않는 규칙을 우회한다 |
