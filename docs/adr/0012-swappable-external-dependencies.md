# ADR-0012: 외부 의존성은 교체 가능하게, 개발 환경에는 로컬 대체물을 둔다

- 상태: 승인
- 날짜: 2026-10-25
- 관련: [ADR-0006](0006-deployment-compose-cloudflare.md), [ADR-0009](0009-authentication-google-and-local.md), [ADR-0011](0011-no-email-in-v1.md)

## 맥락

- 셀프 호스팅을 전제로 한다. 운영자마다 쓸 수 있는 외부 서비스가 다르다. Cloudflare를 안 쓰거나, Google OAuth 앱이 없거나, 자체 메일 서버만 있을 수 있다.
- 개발자가 Google OAuth 클라이언트를 만들고 비밀 값을 나눠 갖는 것은 부담이다. 테스트가 외부 서비스에 의존하면 느려지고 불안정해진다.
- 기존 문서에는 "셀프 호스팅에서도 직접 SMTP 서버를 운영하지 않는다"는 문장이 있었다. 이 전제와 맞지 않아 철회한다.

## 결정

### 원칙

1. 외부 서비스는 모두 **설정으로 교체하거나 끌 수 있어야** 한다. 코드는 특정 공급자가 아니라 표준 프로토콜이나 자체 인터페이스에 의존한다.
2. 꺼도 서비스가 동작해야 하는 것(선택 기능)과, 대체 구현이 반드시 있어야 하는 것(필수 기능)을 구분한다.
3. 개발과 테스트에는 **로컬 대체물**을 두어, 외부 계정이나 비밀 값 없이 전체 흐름을 돌릴 수 있게 한다.

### 의존성별 정리

| 의존성 | 코드가 의존하는 것 | 교체·비활성 | 개발·테스트 대체물 |
|---|---|---|---|
| 외부 로그인 (Google) | 표준 **OIDC** (Spring Security OAuth2 Client) | 공급자 목록은 설정으로 정한다. 설정된 공급자만 로그인 버튼에 나온다. 0개도 허용되며, 그러면 자체 로그인만 남는다. 셀프 호스팅은 Keycloak, Authentik 등 아무 OIDC 공급자나 붙일 수 있다 | `mock-oauth2-server` 컨테이너(개발), Spring Security 테스트 지원 `oidcLogin()`(단위·통합 테스트) |
| 메일 (v1 미사용) | **SMTP** (Spring `JavaMailSender`). 앱 쪽 인터페이스는 `MailSender` | SMTP 접속 정보만 바꾸면 SES, Resend 같은 상용 서비스(모두 SMTP 지원)나 자체 Postfix 등으로 교체된다. 설정이 없으면 메일 기능이 꺼진다. 상용 HTTP API 어댑터는 필요할 때 추가한다 | Mailpit 컨테이너(발송 메일을 웹 화면으로 확인) |
| 봇 차단 (Turnstile) | `HumanVerifier` 인터페이스 | Turnstile, hCaptcha, 또는 끄기. 끄면 요청 제한만 남는다 | 항상 통과하는 구현(개발), Turnstile 공식 테스트 키 |
| Jev | `ArticleEvaluator` | 다른 평가기 구현으로 교체 | WireMock 응답(테스트), 고정 규칙 기반 `FakeArticleEvaluator`(로컬 개발) |
| LLM (OpenAI, Anthropic) | `LlmClientFactory` | 공급자 추가·제거 | WireMock(테스트), 고정 응답 `FakeDigestGenerator`(로컬 개발) |
| 공개 경로 (Cloudflare Tunnel) | 없음 (앱은 HTTP만 안다) | Tunnel, 일반 리버스 프록시 + 포트 공개, 다른 터널 중 선택 | 로컬은 직접 접속 |
| 백업 저장소 (R2) | **S3 호환 API** | MinIO, 다른 S3 호환 저장소, 로컬 디스크 | 로컬 디스크 |
| 클라이언트 IP | 신뢰할 프록시 헤더 설정 | `CF-Connecting-IP`, `X-Forwarded-For` 중 설정으로 선택 | 직접 접속 IP |

### 개발 환경

- Docker Compose **프로필**로 구성을 나눈다.
  - `dev`: `postgres`, `mock-oauth2-server`, (메일 도입 후) `mailpit`
  - `prod`: `proxy`, `web`, `worker`, `postgres`, `cloudflared`, `backup`
- Spring profile `local`은 mock OIDC 공급자를 "Google" 자리에 등록하고, Jev와 LLM은 기본적으로 Fake 구현을 쓴다. 실제 Jev나 LLM으로 확인하고 싶을 때만 키를 환경 변수로 넣는다.
- 실제 Google 로그인은 스테이징·운영 환경에서만 확인한다. Google OAuth 동의 화면을 "테스트" 상태로 두면 지정한 테스트 사용자로만 로그인할 수 있어, 검수 없이 스테이징에서 쓸 수 있다.

## 결과

- 셀프 호스팅 운영자는 Google, Cloudflare, 상용 메일 서비스, Jev 키 없이도 서비스를 띄울 수 있다. Jev가 없으면 평가 품질은 떨어지고, 이 경우 대체 평가기를 써야 한다.
- 새 개발자는 `docker compose --profile dev up`과 IDE 실행만으로 로그인부터 다이제스트 생성까지 전체 흐름을 볼 수 있다.
- 공급자 이름("Google")이 도메인 코드나 DB 스키마에 하드코딩되지 않는다. `user_identities.provider`는 설정의 registration ID를 저장한다.
- Fake 구현은 운영 프로필에서 활성화되지 않도록 기동 시 검사한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 개발용 로그인 우회 엔드포인트 | 운영에 잘못 켜질 위험이 크고, 실제 OIDC 흐름(리다이렉트, 세션 생성, 계정 연결)을 검증하지 못한다 |
| 개발에도 Keycloak | 기능은 충분하지만 무겁다. 개발용으로는 mock 서버로 충분하다 |
| 메일을 공급자별 HTTP API로만 구현 | 공급자를 바꿀 때마다 코드가 필요하다. SMTP는 거의 모든 공급자와 자체 서버가 지원한다 |
