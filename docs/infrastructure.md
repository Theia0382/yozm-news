# 인프라와 운영

## 원칙

- **배포 단위는 Docker Compose 하나.** VPS, 관리형 클라우드, 셀프 호스팅 어디서든 같은 Compose 파일로 띄운다.
- 서버 포트는 외부에 열지 않고 **Cloudflare Tunnel**로만 공개한다.
- v1의 상태 저장소는 PostgreSQL 하나다.

## Compose 구성

| 서비스 | 이미지 | 역할 |
|---|---|---|
| `proxy` | Caddy (SPA 빌드 결과를 포함한 자체 이미지) | SPA 정적 파일 제공, `/api`·`/oauth2`·`/login`을 `web`으로 프록시 |
| `web` | `newsdigest-app` (profile `web`) | REST API, 인증 |
| `worker` | `newsdigest-app` (profile `worker`) | 백그라운드 작업 |
| `postgres` | PostgreSQL 17+ | 관리형 DB를 쓰는 환경에서는 빼고 접속 정보만 바꾼다 |
| `cloudflared` | cloudflare/cloudflared | Tunnel. `proxy`로만 연결한다 |
| `backup` | pg_dump + S3 호환 업로드 | 매일 덤프를 R2(또는 다른 S3 호환 저장소)에 올린다 |

- 소규모 환경에서는 `web`과 `worker`를 하나의 컨테이너(profile `web,worker`)로 합칠 수 있다.
- 로컬 개발은 Compose `dev` 프로필(`postgres`, `mock-oauth2-server`, 메일 도입 후 `mailpit`)만 띄우고, 백엔드는 IDE에서 Spring profile `local`로, 프론트엔드는 Vite 개발 서버(프록시 설정)로 실행한다. 외부 계정이나 비밀 값 없이 전체 흐름이 돈다 ([ADR-0012](adr/0012-swappable-external-dependencies.md)).

## Cloudflare

Cloudflare는 권장 구성이지 필수가 아니다. 셀프 호스팅에서는 Tunnel 대신 일반 리버스 프록시를, R2 대신 다른 S3 호환 저장소를, Turnstile 대신 다른 봇 차단 수단을 쓰거나 끌 수 있다 ([ADR-0012](adr/0012-swappable-external-dependencies.md)).

도메인은 `yozmnews.net`이다. Cloudflare Registrar로 등록하므로 DNS도 Cloudflare에서 관리한다.

| 기능 | 용도 |
|---|---|
| Registrar | 도메인 등록·갱신(원가), WHOIS 개인정보 보호 |
| DNS · 프록시 · CDN | 도메인, TLS, 정적 파일 캐싱 |
| Tunnel | 인바운드 포트 없이 공개. 셀프 호스팅에서도 같은 방식 |
| WAF · Rate Limiting 규칙 | 로그인·키 등록·수동 재생성 같은 민감한 엔드포인트의 1차 방어 |
| R2 | DB 백업 저장. 외부로 나가는 전송량 요금이 없다 |
| AI Gateway (선택) | Jev 호출 경유. 호출 로그, 비용 집계, 재시도, 한도 설정. 보도에 따르면 `typesafe/jev`로 지원한다 |

- **사용자 BYOK LLM 호출은 AI Gateway를 거치지 않고** 공급자에 직접 보낸다. 사용자 키와 프롬프트가 제3자 로그에 남지 않게 하기 위해서다.
- **Turnstile**은 자체 가입·로그인·복구 코드 사용 화면에 붙인다 ([ADR-0009](adr/0009-authentication-google-and-local.md), [ADR-0011](adr/0011-no-email-in-v1.md)).

## 메일 발송 (v1 제외)

- v1에는 메일 발송이 없다 ([ADR-0011](adr/0011-no-email-in-v1.md)). 아래는 나중에 도입할 때의 방침이다.
- 발송은 표준 **SMTP**로 한다. SMTP 접속 정보만 바꾸면 상용 서비스(SES, Resend 등)와 자체 메일 서버(Postfix 등)를 오갈 수 있다. 설정이 없으면 메일 기능이 꺼진다 ([ADR-0012](adr/0012-swappable-external-dependencies.md)).
- 후보: Resend, Amazon SES, Postmark 등. 선택은 [open-questions.md](open-questions.md)에서 한다.
- 발송 도메인에 SPF, DKIM, DMARC를 설정한다. Cloudflare DNS에서 관리한다.
- 자체 메일 서버를 쓸 때는 전달률(스팸 분류)을 위해 SPF·DKIM·DMARC와 역방향 DNS 설정이 필요하다. 상용 SMTP 릴레이를 쓰는 것도 운영자의 선택지다.

## 호스팅 선택지

| 선택지 | 구성 | 장점 | 단점 |
|---|---|---|---|
| **A. 서울 리전 VPS 한 대** (권장 시작점) | Compose 전체 + Tunnel | 가장 저렴하고 셀프 호스팅과 같은 구조, 국내 응답 속도 | 단일 장애점. 백업·복구 절차가 필수 |
| B. 관리형 컨테이너 + 관리형 Postgres | Fly.io / Railway / Cloud Run + Neon 등 | DB 운영 부담 감소 | 구성이 환경마다 달라지고, JVM 상시 실행 비용, 리전 선택 제약 |
| C. 셀프 호스팅 | 집·사무실 서버에 Compose + Tunnel | 비용 최소 | 가용성이 전원·회선에 의존 |

- A의 후보: Oracle Cloud 무료 ARM 인스턴스(춘천·서울), Vultr 서울, AWS Lightsail 서울 등. ARM을 고려해 컨테이너 이미지는 `linux/amd64`, `linux/arm64` 두 가지로 빌드한다.
- Cloudflare Containers는 2026년 4월 정식 출시됐지만, 상시 실행되는 JVM worker와 PostgreSQL 연결을 전제로 한 이 구성에는 맞지 않아 v1에서는 보류한다.

## 비밀 값

| 값 | 보관 |
|---|---|
| DB 비밀번호, Google OAuth client secret, Jev API 키, Turnstile secret, Tunnel 토큰 | 호스트의 환경 파일 또는 Docker secrets. 저장소에 커밋하지 않는다 |
| BYOK 키 암호화용 KEK | 위와 같되 **별도 파일**로 분리하고 DB 백업에는 포함하지 않는다. 버전을 붙여 교체할 수 있게 한다 ([security.md](security.md)) |

앱은 DB 접속 정보를 Spring 표준 환경 변수 `SPRING_DATASOURCE_URL` · `SPRING_DATASOURCE_USERNAME` · `SPRING_DATASOURCE_PASSWORD` 로 받는다. `SPRING_DATASOURCE_URL` 이 없으면 기동하지 않는다([ADR-0017](adr/0017-standard-datasource-env-vars.md)). 로컬 개발(Spring profile `local`)은 이 값 대신 개발용 Compose 의 PostgreSQL 컨테이너에서 접속 정보를 읽는다 — 이 연동이 켜져 있는 동안 위 환경 변수는 쓰이지 않는다([ADR-0018](adr/0018-local-profile-compose-connection.md)).

## 관측

- **로그**: JSON 구조화 로그. 요청 ID와 사용자 ID를 포함하고, 비밀 값은 마스킹한다.
- **지표**: Spring Boot Actuator와 Micrometer. 핵심 지표는 피드별 수집 성공률, 평가 대기열 길이, Jev 지연·토큰, 다이제스트 생성 성공률·소요 시간, 공급자별 오류율.
- **대시보드**: v1은 운영자 화면과 JobRunr 대시보드로 충분하다. 필요해지면 Grafana Cloud 무료 티어 등으로 지표를 내보낸다.
- **오류 추적**: Sentry 같은 외부 서비스는 선택. 붙인다면 요청 본문과 헤더 전송을 끈다.
- **헬스 체크**: Actuator 는 HTTP 로 `health` 엔드포인트만 노출한다(탐색 페이지 `/actuator` 와 probe 그룹 `/actuator/health/liveness` · `/actuator/health/readiness` 포함). `/actuator/health` 응답에는 집계 상태와 probe 그룹 이름만 담고, 구성요소와 상세는 넣지 않는다. 집계에는 DB 가 들어 있어 DB 장애 때 상태가 `DOWN` 이 되고 HTTP 503 으로 응답한다.

## 백업과 복구

- 매일 `pg_dump`를 R2에 올린다. 보관 기간은 30일.
- 분기마다 복구 리허설을 한다. 새 환경에 Compose로 띄우고 덤프를 복원해 동작을 확인한다.
- KEK는 DB 백업과 다른 위치에 따로 백업한다. KEK를 잃으면 사용자 키를 복호화할 수 없다. 그래도 사용자가 다시 등록하면 되므로 치명적이지는 않다.

## CI/CD (초안)

- GitHub Actions: 테스트 → 멀티 아키텍처 이미지 빌드 → GHCR 푸시.
- 배포: 호스트에서 `docker compose pull && docker compose up -d`. 처음에는 수동으로 하고, 이후 SSH 배포 작업으로 자동화한다.
- DB 마이그레이션은 `web` 기동 시 Flyway가 실행한다. 되돌릴 수 없는 변경은 두 번의 배포로 나눈다(확장 → 축소).
