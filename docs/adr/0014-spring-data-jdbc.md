# ADR-0014: 데이터 접근은 Spring Data JDBC + JdbcClient

- 상태: 승인
- 날짜: 2026-10-25
- 관련: [ADR-0004](0004-tech-stack.md)의 데이터 접근 항목(Spring Data JPA)을 대체한다

## 맥락

[ADR-0004](0004-tech-stack.md)에서는 데이터 접근을 Spring Data JPA로 정했다. 이후 아키텍처 패턴을 정하면서([ADR-0013](0013-architecture-patterns.md)) 다음 사항이 드러났다.

- aggregate 사이를 ID로만 참조해야 Modulith 모듈 경계가 유지된다. JPA의 연관관계 매핑(`@ManyToOne` 등)은 다른 모듈의 엔티티를 객체로 붙잡기 쉽다.
- 핵심 쿼리 상당수가 SQL에 가깝다. 후보 선별(태그 확률과 임계값 비교), 기사 upsert(`ON CONFLICT`), 태그 대량 저장, `jsonb` 컬럼이 그렇다. JPA를 써도 이 부분은 native query가 된다.
- 외부 호출을 트랜잭션 밖에서 하는 구조에서는 지연 로딩, 변경 감지, 영속성 컨텍스트가 언제 동작하는지 추적해야 하는 부담이 크다.
- 스키마는 Flyway로 직접 관리하므로 JPA의 스키마 생성 기능은 쓰지 않는다.

## 결정

| 용도 | 기술 |
|---|---|
| aggregate 저장·조회 (쓰기 쪽) | **Spring Data JDBC** |
| 화면·운영자용 조회, 후보 선별, 대량 처리, upsert (조회 쪽·특수 쓰기) | **JdbcClient** (Spring Framework)로 SQL 직접 작성 |
| 스키마 | Flyway 마이그레이션 SQL |

- aggregate 안의 자식 데이터는 aggregate와 함께 저장·조회한다. 다른 aggregate는 ID로만 참조한다.
- 변경 사항은 `save()`를 명시적으로 호출해야 DB에 반영된다.
- `jsonb`, 열거형 등은 Spring Data JDBC 변환기로 매핑한다.
- 조회 SQL은 각 모듈의 `adapter/out/persistence` 또는 조회 전용 클래스에 두고, 테스트는 Testcontainers PostgreSQL로 한다.

## 결과

- 어떤 SQL이 언제 실행되는지 코드에서 드러난다. 지연 로딩으로 인한 N+1 같은 숨은 문제가 줄어든다.
- 도메인 클래스에 붙는 애노테이션이 적어서, 엔티티를 도메인 모델로 그대로 써도 부담이 작다.
- aggregate 전체를 다시 저장하는 방식이라 큰 aggregate에는 불리하다. aggregate를 작게 유지한다.
- 국내 자료는 JPA보다 적다.
- JobRunr, Spring Session, Modulith 이벤트 레지스트리는 각자 JDBC로 동작하므로 이 결정의 영향을 받지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| Spring Data JPA | 위 "맥락" 참고 |
| jOOQ | 타입 안전한 SQL은 장점이지만 코드 생성 단계가 추가된다. JdbcClient로 부족해지면 조회 쪽에 한해 다시 검토한다 |
| MyBatis | SQL 직접 작성 측면은 JdbcClient로 충분하고, XML 매퍼가 추가로 필요하지 않다 |
