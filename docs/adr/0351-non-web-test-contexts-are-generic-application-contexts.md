# ADR-0351: 웹이 아닌 시험 컨텍스트는 GenericApplicationContext 로 만든다

- 상태: 승인
- 날짜: 2026-11-27
- 근거: [이슈 #158](https://github.com/Theia0382/yozm-news/issues/158) — 메모리 부족 순간의 힙 덤프에서 캐시 안 `@JdbcTest` 컨텍스트의 무게를 지배 트리로 따라가고, Spring Framework 7.0.10 · Spring Boot 4.1.2 소스로 기제를 대조함. 같은 공장을 넣은 전체 테스트로 효과를 잼. 구현 커밋 `018d400`
- 관련: [ADR-0122](0122-test-postgresql-container-shared-per-test-jvm-with-an-empty-database-per-context.md)(컨텍스트 캐시 — "컨텍스트 구성을 합쳐 컨텍스트 수를 줄인다" 기각은 그대로), [ADR-0349](0349-test-jvm-heap-cap-stays-at-least-twice-the-measured-live-heap.md)(힙 상한)

## 맥락

- Spring Boot 는 웹이 아닌 컨텍스트를 `AnnotationConfigApplicationContext` 로 만든다(AOT 실행이면 `GenericApplicationContext`). `@JdbcTest` · `@DataJdbcTest` 가 이 종류다.
- Spring Framework 7.0.10 에서 이 컨텍스트는 생성자에서 자기 스캐너를 만들고, 스캐너의 `CachingMetadataReaderFactory` 가 컨텍스트의 공유 메타데이터 맵을 받는다. refresh 의 컴포넌트 스캔이 같은 맵을 채운다. 슬라이스도 `@SpringBootApplication` 의 스캔으로 제품 패키지 전체를 읽고 필터로 뺀다. refresh 끝의 `clearResourceCaches()` 는 컨텍스트가 그 맵을 들고 있던 바깥 맵만 비우므로, 스캐너가 붙잡은 안쪽 맵의 메타데이터는 컨텍스트가 닫힐 때까지 남는다.
- 시험 컨텍스트 캐시에 남은 `@JdbcTest` 컨텍스트 하나가 약 19 MB 였고 그 가운데 약 18.5 MB 가 이 맵이었다. 메모리 부족 순간의 덤프에서 캐시 안의 18개가 힙의 33% 였다. 크기는 제품 클래스 수에 비례한다.
- 같은 기제를 짚은 업스트림 보고([spring-framework#37705](https://github.com/spring-projects/spring-framework/issues/37705))는 작성자가 닫았고, 2026-11 에 그 보고와 관련 논의를 본 범위에서는 고친 판이 보이지 않았다.

## 결정

- **시험 클래스패스의 `META-INF/spring.factories` 에 `ApplicationContextFactory` 하나(`NonWebTestContextFactory`)를 등록한다.** 웹이 아닌 종류면 `GenericApplicationContext` 를 내고, 그 밖에는 답하지 않아(`null`) Spring Boot 의 공장이 만든다. `GenericApplicationContext` 에는 그 스캐너가 없어 refresh 가 끝나면 메타데이터가 풀린다.
- **지울 조건**: 프레임워크의 `AnnotationConfigApplicationContext` 가 refresh 뒤 스캐너의 메타데이터를 놓게 되면 공장과 등록 줄을 지운다.
- 현재 상태는 [개발 인프라 — 테스트 JVM](../development/infrastructure.md#테스트-jvm)이 갖는다.

## 결과

- 줄이기 전 설정에 이 공장만 더한 전체 테스트에서 살아 있는 힙 최대가 1,373 MiB 에서 1,057 MiB 로 줄었다. 시험 결과와 컨텍스트 생성 수는 같았다.
- 바뀌는 컨텍스트는 `@JdbcTest` · `@DataJdbcTest` 와 시험이 직접 띄우는 웹이 아닌 앱이다. 내장 서버 없는 웹 시험(`@SpringBootTest` 기본 · `@WebMvcTest` · `@ApplicationModuleTest`)은 원래 스캐너가 없는 `GenericWebApplicationContext` 이고, `RANDOM_PORT` 시험의 서버 컨텍스트는 Spring Boot 의 서블릿 공장이 만들어 이 처분 밖이다. `bootTestRun` 은 시험 클래스패스로 돌지만 서블릿 앱이라 영향이 없다.
- 테스트 `NonWebTestContextTests` 가 `@JdbcTest` 가 받은 컨텍스트의 종류와 공장이 웹 종류에 답하지 않음을 본다.
- 지울 조건을 지키는 장치는 없다 — Spring Framework 를 올릴 때 손으로 확인한다. 고쳐진 뒤에 남아도 해롭지 않다(AOT 실행과 같은 종류).

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| refresh 뒤 리스너가 스캐너의 메타데이터 캐시를 리플렉션으로 비운다 | 같은 효과를 Spring 내부 필드에 기대 얻는다 — 판 올림 때 필드가 바뀌면 깨진다 |
| `@JdbcTest` 시험들을 저장소 구현을 함께 띄우는 공용 구성으로 묶는다 | 시험 수십 곳의 구성이 바뀌고 슬라이스가 띄우는 범위가 넓어진다([ADR-0122](0122-test-postgresql-container-shared-per-test-jvm-with-an-empty-database-per-context.md) 의 기각 이유). 원인과 무관한 비용이다 |
| 그대로 두고 컨텍스트 캐시 크기 · 힙 상한으로 흡수한다 | 컨텍스트 하나의 크기가 제품 클래스 수와 함께 계속 는다 |
