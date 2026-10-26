# ADR-0017: DB 접속 정보는 Spring 표준 환경 변수로 받는다

- 상태: 승인
- 날짜: 2026-10-26
- 근거: [이슈 #2](https://github.com/Theia0382/yozm-news/issues/2) — 기점 `bff9dec` 의 실행 jar 를 접속 설정 조합별로 기동해 실측
- 관련: [ADR-0006](0006-deployment-compose-cloudflare.md)(배포 Compose), [ADR-0012](0012-swappable-external-dependencies.md)(외부 의존성 교체)

## 맥락

- 같은 이미지를 운영 · 셀프 호스팅 · 관리형 DB 환경에서 띄운다. DB 접속 정보(URL · 사용자 · 비밀번호)는 저장소에 두지 않고 실행 환경이 준다. 운영자는 호스트의 환경 파일에 값을 적는다.
- Spring Boot 는 relaxed binding 으로 환경 변수 `SPRING_DATASOURCE_URL` 을 속성 `spring.datasource.url` 로 읽는다. 환경 변수는 `application*.yaml` 보다 우선하므로, 설정 파일에 무엇을 쓰든 표준 이름은 항상 동작한다.
- 프로젝트 고유 이름(`DB_URL` 등)을 설정 파일의 자리표시자로 이으면 같은 값을 받는 경로가 둘이 된다. 실측에서 고유 이름의 자리표시자는 값이 빠졌을 때 기본값이 없으면 실패 분석기 없이 `'url' must start with "jdbc"` 예외로 멈췄고, 빈 기본값을 주면 Spring 표준 메시지로 멈췄지만 빠진 변수 이름은 나오지 않았다.

## 결정

- 앱은 DB 접속 정보를 Spring 표준 환경 변수 `SPRING_DATASOURCE_URL` · `SPRING_DATASOURCE_USERNAME` · `SPRING_DATASOURCE_PASSWORD` 로 받는다.
- 기본 설정 파일(`application.yaml`)에는 `spring.datasource` 키도, 다른 이름의 환경 변수를 잇는 자리표시자도 두지 않는다.
- 앱이 받는 환경 변수 이름은 [infrastructure.md](../infrastructure.md) "비밀 값" 절에 적는다.

## 결과

- 받는 경로가 하나다. URL 이 없으면 기동이 실패 분석기 메시지 `Failed to configure a DataSource: 'url' attribute is not specified …` 로 멈춘다. 사용자 · 비밀번호만 빠진 경우는 재지 않았다.
- 이름이 길고 Spring 을 모르는 운영자에게 낯설다. 설정 파일만 보고는 어떤 환경 변수를 받는지 알 수 없어 문서가 필요하다.
- 공식 `postgres` 이미지의 `POSTGRES_PASSWORD` 같은 값과 이 이름을 잇는 일은 Compose 파일이 한다(`SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}` 식).
- 이후 앱이 받는 설정 값(외부 로그인 client secret, Jev 키 등)도 속성 이름에 대응하는 표준 환경 변수로 받는 것을 선례로 삼는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 프로젝트 고유 이름(`DB_URL` · `DB_USERNAME` · `DB_PASSWORD`)을 설정 파일의 자리표시자로 매핑 | 표준 이름도 계속 동작해 받는 경로가 둘이 된다. 값이 빠졌을 때 메시지가 변수 이름을 말하지 않고, 기본값을 빠뜨리면 진단이 어려운 예외가 난다 |
| 접속 요소를 나눠 받아(`DB_HOST` · `DB_PORT` · `DB_NAME` · `DB_USERNAME` · `DB_PASSWORD`) 설정 파일에서 JDBC URL 조립 | 관리형 DB 의 URL 파라미터(`sslmode` 등)를 넣을 자리가 없거나 변수가 더 는다. 위 대안의 단점을 그대로 가진다 |
