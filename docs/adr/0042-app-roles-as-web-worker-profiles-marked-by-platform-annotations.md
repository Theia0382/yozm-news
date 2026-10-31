# ADR-0042: 앱 역할은 Spring profile `web` · `worker` 로 나누고, 소속은 `platform` 역할 애너테이션으로 표시하며, 역할이 없으면 기동하지 않는다

- 상태: 승인
- 날짜: 2026-10-31
- 근거: [이슈 #6](https://github.com/Theia0382/yozm-news/issues/6) — 기점 `94947ec` 의 저장소와 JobRunr Spring Boot 4 starter 9.0.2 · Spring Modulith 2.1.1 · spring-context 7.0.10 소스를 읽고, Spring Boot 4.1.2 의 profile 동작을 실측해 확인. 구현 `1b41260`(역할 타입과 기동 검사) · `008ff54`(구성요소의 역할 소속과 설정 배치) · `f367e4e`(배포 구성의 역할 명시)
- 관련: [ADR-0004](0004-tech-stack.md)(같은 이미지를 `web` · `worker` profile 로 나눠 실행), [ADR-0021](0021-separate-deploy-compose-with-single-app-service.md)(기본 배포 구성은 두 역할을 켠 `app` 하나), [ADR-0023](0023-jobrunr-oss-framework-schema-and-event-resubmission.md)(작업 서버 · 멈춤 감시 — 감시를 어느 역할에서 돌릴지를 이 ADR 이 정한다), [ADR-0013](0013-architecture-patterns.md)(`adapter/in/web` · `adapter/in/job` 패키지 규약), [ADR-0041](0041-activation-gate-previews-a-feed-on-enable-and-judges-by-code-constants.md)(피드 켜기의 미리보기 — `web` 도 피드에 요청한다)

## 맥락

- [ADR-0004](0004-tech-stack.md)는 같은 이미지를 `web` · `worker` profile 로 나눠 실행하기로 했고, [ADR-0021](0021-separate-deploy-compose-with-single-app-service.md)은 기본 배포 구성을 두 역할을 켠 서비스 하나로 정했다. 그때까지 앱에는 profile 구분이 없어 모든 구성요소가 언제나 켜졌다.
- 역할에 걸리는 것은 셋이다. 화면용 API(컨트롤러 · 명세 설정), JobRunr 작업(진입점 · 작업 서버), 그리고 작업이 아닌 실행 둘 — Spring Modulith 의 멈춤 감시(Spring 스케줄링, 1분)와 프리셋 기동 대조(기동 때 한 번).
- JobRunr 작업 서버는 설정 키 하나로 꺼진다. 꺼져도 작업 등록(`JobScheduler`)은 되고, 헬스 지표는 `UP` 이다. 반복 작업 등록은 작업 서버가 아니라 작업 빈이 있는 프로세스에서 일어난다.
- Spring 의 기본 profile(`spring.profiles.default`)은 활성 profile 이 하나도 없을 때만 쓰인다. 개발 실행은 `local` 을 켜므로, 기본값만 두면 개발 앱은 역할 없이 오류 없이 뜬다(실측).
- profile 별 설정 파일은 활성화 순서대로 덮어쓴다. 두 역할 파일이 같은 키를 다르게 쓰면 `web,worker` 와 `worker,web` 의 결과가 다르다(실측).
- Spring Modulith 의 비동기 리스너는 이벤트를 발행한 프로세스에서 돈다. 발행 기록은 그 프로세스에 있는 리스너에 대해서만 만들어지고, 재처리는 재제출하는 프로세스에서 리스너를 찾는다.
- 멈춤 감시를 돌리는 Spring 스케줄링은 Spring Modulith Moments 자동 구성의 `@EnableScheduling` 이 켜고 있었다. Moments 의 시간 이벤트를 받는 곳은 없다.
- 피드 켜기의 미리보기([ADR-0041](0041-activation-gate-previews-a-feed-on-enable-and-judges-by-code-constants.md))는 켜기 호출 안에서 피드를 받는다. 켜기는 운영자 API 가 부르므로 `web` 도 피드에 요청한다.

## 결정

- **역할은 Spring profile `web` · `worker` 둘이다.** 두 profile 을 함께 켜면 한 프로세스가 두 역할을 다 한다.
- **구성요소의 소속은 `platform` 공개 타입인 역할 애너테이션 `@WebRole` · `@WorkerRole` 로 표시한다.** 둘은 `@Profile` 메타 애너테이션이고, profile 이름의 코드 쪽 정본은 각 애너테이션의 상수다. `adapter/in/web` 의 구성요소는 `@WebRole`, `adapter/in/job` 의 구성요소는 `@WorkerRole` 이어야 하고, 두 역할을 함께 단 구성요소는 없다 — 구조 테스트가 지킨다.
- **역할 profile 을 하나도 켜지 않으면 두 역할을 함께 한다**(`spring.profiles.default: web,worker`). **역할이 하나도 켜지지 않으면 기동하지 않는다** — 다른 profile 만 켜서 기본 profile 이 쓰이지 않는 경우다. 판정은 `platform` 의 `domain` 이 하고, 메시지가 켜는 방법을 알린다. 개발 실행은 `local` 과 함께 역할을 적는다(`local,web,worker`).
- **`web` 은 화면용 API 를 연다.** `worker` 만 켠 프로세스도 웹 서버를 띄우고 관리 엔드포인트(health)를 연다 — 화면용 API 는 웹 쪽 구성요소를 빼서 닫는다.
- **`worker` 는 작업 진입점(반복 작업 등록 포함) · 작업 서버 · 프리셋 기동 대조 · Spring 스케줄링과 멈춤 감시를 맡는다.** 이로써 `web` 이 DB 에 쓰는 계기는 요청과 그 요청이 발행한 이벤트의 리스너, 기동 마이그레이션뿐이다.
- **역할이 켜는 설정은 `application-worker.yaml` 에만 두고, 기본 파일은 꺼진 값이다** — 작업 서버의 `enabled`, 멈춤 감시 세 기간. 역할끼리 같은 키를 다르게 쓰지 않으므로 활성화 순서와 무관하다.
- **Spring 스케줄링은 작업 역할 구성이 켜고, Moments 는 끈다.** 주기 실행은 JobRunr 반복 작업으로 두고, Spring 스케줄링은 작업 역할에서만 쓴다.
- **두 역할에 공통인 것**: 애플리케이션 서비스 · 도메인 · 저장소, 작업 등록(`JobScheduler`), 피드 요청 관문(`ArticleSource`) · 정규화 · 같은 언론사 요청 예약, 이벤트 리스너, Flyway 마이그레이션, 관리 엔드포인트, 역할 검사.
- **이벤트 리스너는 역할을 달지 않는다.** 한 역할에서 빼면 그 역할이 발행한 이벤트의 발행 기록이 생기지 않아 처리되지 않는다. 화면 요청이 발행한 이벤트의 리스너는 `web` 에서 돈다 — 무거운 일은 리스너가 작업으로 등록해 `worker` 로 보낸다(리스너는 짧은 처리 — [ADR-0023](0023-jobrunr-oss-framework-schema-and-event-resubmission.md)).
- **화면용 API 명세 엔드포인트는 역할에 묶지 않는다.** 배포 기본은 닫혀 있고, `local` 에서 `worker` 만 켜면 경로 없는 명세가 열릴 뿐이다.

## 결과

- 모든 모듈의 웹 · 작업 진입점이 `platform` 의 역할 애너테이션에 의존한다. 그래서 `platform` 이 다른 모듈의 타입에 의존하면 순환이 된다(Modulith 검증이 실패한다). 앞으로 `platform` 의 보안 설정은 사용자 조회 등을 Spring 인터페이스 타입(`UserDetailsService` · `OAuth2UserService` 등)으로 주입받아 다른 모듈 타입을 import 하지 않는다. 그 길이 막히면 역할 애너테이션의 자리를 다시 본다.
- 개발 실행 명령이 `local,web,worker` 로 바뀐다. 예전 명령(`local` 만)은 기동 검사가 멈추고 고칠 명령을 알린다.
- 공통 빈에 Spring `@Scheduled` 를 두면 `web` 만 켠 프로세스에서는 경고 없이 돌지 않는다. 지금 `@Scheduled` 는 없다. 외부 라이브러리가 `web` 에서 Spring 스케줄링을 요구하면 그 기능을 만들 때 다시 본다.
- 역할을 나눠 띄우면 프로세스마다 DB 연결 풀이 생긴다. 나눠 띄우는 배포 구성은 아직 없다([ADR-0021](0021-separate-deploy-compose-with-single-app-service.md)의 `web` · `worker` 두 서비스).
- 종료 순간 멈춤 감시가 닫힌 DataSource 로 한 번 더 돌아 ERROR 로그 한 줄이 남을 수 있는 것은 `worker` 에 그대로 남는다(동작 영향 없음).
- 역할 조합마다 실제 PostgreSQL 로 띄운 테스트 컨텍스트가 늘어 테스트 실행이 길어진다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 클래스마다 `@Profile("web")` · `@Profile("worker")` 를 직접 단다 | profile 이름 리터럴이 클래스마다 반복된다. 빠뜨리면 두 역할 모두에서 뜬다 |
| 패키지 규약으로 한 곳에서 거른다(역할이 꺼지면 `adapter.in.web` · `adapter.in.job` 빈 정의를 등록하지 않는다) | 소속이 클래스에 보이지 않고, ADR-0013 의 패키지 이름이 런타임 동작 계약이 된다. 스캔 단계의 자체 장치라 Spring 의 조건 보고서에 나오지 않고, 패키지 밖 구성요소(기동 대조 · 스케줄링)는 따로 처리해야 한다 |
| 기본 profile 에 `local` 그룹(`spring.profiles.group.local: web,worker`)을 더한다 | `local` 에서는 언제나 두 역할이 된다 — `local,worker` 도 두 역할로 떠서 적은 것과 다르게 뜬다 |
| 기본 profile 을 두지 않고 역할을 반드시 적게 한다 — 적지 않으면 기동하지 않는다 | 모든 테스트 컨텍스트 · 개발 실행 · jar 실행에 역할을 적어야 한다. 기본 구성은 두 역할이라는 결정과 결이 다르다 |
| 멈춤 감시를 모든 역할에서 돌린다 | `web` 이 요청 없이 1분마다 DB 를 쓴다. 감시가 실패로 바꾼 발행을 다시 처리하는 작업은 `worker` 에만 있다 |
| 프리셋 기동 대조를 모든 역할에서 돌린다 | `web` 이 요청 없이 쓰는 예외가 하나 생기고 인스턴스마다 같은 경고 로그가 남는다. 같은 이미지를 함께 띄우는 배포에서는 정합의 차이가 없다 |
| Moments 를 두고 작업 역할 구성만 스케줄링을 켠다 | `web` 에서 Moments 의 시간 이벤트가 매시 · 매일 돈다. 받는 곳이 없다 |
| 지금처럼 Moments 에 기대어 스케줄링을 켠다 | Moments 를 끄면 멈춤 감시가 조용히 사라지고, `web` 에도 스케줄링이 남는다 |
| `worker` 만 켠 프로세스는 웹 서버를 띄우지 않는다 | Compose healthcheck 와 "작업 서버가 멈추면 health 가 DOWN"이라는 생존 신호를 잃는다 |
| 작업 서버 · 감시를 `application-web.yaml` 에서 끈다 | 두 역할을 함께 켤 때 활성화 순서에 따라 꺼질 수 있다 |
| 역할 검사를 컨텍스트 전(`EnvironmentPostProcessor`)에 한다 | Flyway 보다 먼저 멈추는 이점뿐이고 마이그레이션은 두 역할 공통이다. 등록 파일 결선을 따로 따라가야 한다 |
| 명세 엔드포인트를 `local` 과 `web` 이 함께일 때만 연다 | 역할 조건이 `application-worker.yaml` 밖에 하나 더 생기고 `local` 값이 두 곳으로 갈린다. 얻는 것은 개발 환경에서 `worker` 만 띄웠을 때 빈 명세가 닫히는 것뿐이다 |
