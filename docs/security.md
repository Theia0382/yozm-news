# 보안 · 개인정보 · 저작권

## 인증

v1은 **자체 로그인(이메일·비밀번호)** 하나를 지원한다 ([ADR-0009](adr/0009-authentication-google-and-local.md)). 외부 로그인(OIDC)은 v1 범위 밖이다 ([ADR-0123](adr/0123-v1-login-is-local-only-and-oidc-and-account-linking-come-after-v1.md)). v1에는 메일 발송이 없으므로 자체 로그인의 이메일은 **미인증 값**이다 ([ADR-0011](adr/0011-no-email-in-v1.md)).

### 가입

누가 계정을 만들 수 있는가와 첫 운영자 계정을 만드는 방법이다 ([ADR-0048](adr/0048-sign-up-closed-by-default-with-invitations-and-operator-bootstrap.md)). 규칙은 [user Spec](specs/user.md#계정을-만드는-길) user-R28 ~ R35 에 있다.

- 가입은 기본으로 닫혀 있다. 운영자가 운영자 화면에서 공개 가입을 열 수 있고(바꾼 이력이 남는다 — [ADR-0140](adr/0140-sign-up-policy-is-a-user-module-operator-setting-with-append-only-history-and-starts-closed.md)), 열면 계정 존재 노출을 감추는 일반 문구를 쓰고, 가입에 IP별 요청 제한이 걸리며, 봇 차단을 켠 인스턴스에서는 가입 화면에 봇 차단이 붙는다 — 아래 자체 로그인 표와 [악용 방지](#악용-방지).
- 닫힌 가입에서 계정을 만드는 길은 둘이다.
  - **초대 링크** — 운영자(`ADMIN`)가 만든 일회용 · 7일 링크다. 운영자가 직접 전한다(메일 없음). 이메일에 묶지 않고, 운영자는 메모로 가른다. 토큰은 128비트 무작위 값이고 링크의 조각(`#` 뒤 — 서버 로그 · `Referer` 에 남지 않는다)에 담으며 SHA-256 다이제스트로만 저장한다. 운영자가 취소할 수 있고, 한 번 쓰면 다시 쓸 수 없다. 받은 사람은 자체 로그인으로 가입한다([ADR-0141](adr/0141-invitation-links-are-seven-day-single-use-links-not-bound-to-an-email-with-the-token-in-the-fragment-stored-only-as-a-digest.md)).
  - **운영자 계정 생성** — 운영자가 이메일 · 표시 이름 · 초기 비밀번호로 자체 로그인 계정을 만든다. 첫 로그인의 규칙은 자체 로그인 표의 "초기 비밀번호" 행을 따른다. 첫 로그인 전이면 운영자가 초기 비밀번호를 다시 정할 수 있고 그 계정의 세션이 끝난다 — 계정 주인이 비밀번호를 바꾼 계정은 건드리지 못한다([ADR-0143](adr/0143-operators-reset-the-initial-password-only-of-accounts-before-their-first-sign-in-and-end-their-sessions.md)).
  - 두 길 모두 일반 사용자(`USER`) 계정만 만든다. 운영자(`ADMIN`)는 첫 운영자로만 생긴다([ADR-0142](adr/0142-invitations-and-operator-created-accounts-are-always-users-and-admins-come-only-from-the-first-admin.md)).
- **첫 운영자** — 서버 설정(환경 변수)의 이메일 · 초기 비밀번호로만 만든다. 기동 로그에 초대 링크 같은 비밀을 내지 않는다([ADR-0139](adr/0139-first-admin-is-created-only-from-environment-variables-and-startup-warns-without-an-admin-or-about-leftover-values.md)).
  - 쓰는 운영자 계정이 없을 때만 적용한다. 운영자도 값도 없으면 경고 로그를 남기고 뜬다. 운영자가 생긴 뒤 남은 값은 무엇이든 경고만 한다.
  - 운영자가 없는데 값이 하나만 있거나 규칙을 어기거나, 이메일이 이미 있는 계정의 것이면 이유(변수 이름)를 말하고 기동하지 않는다 — 있는 계정을 운영자로 올리지 않는다.
  - 이 계정의 첫 로그인도 "초기 비밀번호" 행을 따른다. 초기 비밀번호는 배포 환경 파일에 평문으로 남으므로 만든 뒤 지운다.

### 공통

- 세션 쿠키: `HttpOnly`, `Secure`, `SameSite=Lax`. 세션은 Spring Session JDBC로 DB에 저장하고, 세션에는 사용자 ID 만 담는다 — 역할 · 상태는 요청마다 계정에서 읽어, 정지 · 탈퇴 · 역할 변경이 다음 요청부터 적용된다([ADR-0126](adr/0126-user-paths-authenticate-with-a-jdbc-session-holding-only-the-user-id-and-cookie-csrf-reading-the-account-per-request.md)).
- 세션 · CSRF 쿠키의 `Secure` 는 언제나 단다. 앞단(Cloudflare Tunnel 또는 운영자의 리버스 프록시)이 TLS 를 맡는다는 전제라, TLS 없이 HTTP 로 연 인스턴스에서는 로그인할 수 없다(브라우저가 `localhost` 만 예외로 둔다 — [ADR-0128](adr/0128-session-and-csrf-cookies-are-always-secure-assuming-tls-in-front.md)).
- 세션은 마지막 요청 뒤 비활성 기간이 지나면 끝나고 절대 상한은 없다. 로그인 때 "로그인 유지"를 고르면 7일 · 지속 쿠키, 고르지 않으면 2시간 · 브라우저 세션 쿠키다([ADR-0127](adr/0127-sessions-idle-out-after-seven-days-without-an-absolute-limit-and-without-keep-signed-in-use-a-browser-cookie-and-two-hours.md)).
- 로그인에 성공하면 세션 ID를 새로 발급한다(세션 고정 방지).
- CSRF: Spring Security의 쿠키 기반 CSRF 토큰을 SPA가 헤더로 돌려보낸다. 로그인 · 로그아웃 요청에도 건다.
- 세션 · 쿠키 · CSRF 의 규칙은 [user Spec "세션"](specs/user.md#세션)에 있다.

### 자체 로그인

| 항목 | 정책 |
|---|---|
| 비밀번호 저장 | Argon2id 해시. 파라미터는 서버 사양에 맞춰 로그인 1회가 수백 ms 이내가 되도록 조정하고, 파라미터를 해시 문자열에 함께 저장한다. 값은 [user Spec](specs/user.md#계정과-자격) user-R15 |
| 비밀번호 규칙 | 최소 8자, 최대 128자(NFKC 정규화 뒤 코드 포인트). 문자 종류 강제 규칙은 두지 않고 흔한 비밀번호 목록과 대조하지 않는다([ADR-0129](adr/0129-passwords-are-free-form-from-eight-to-128-code-points-without-a-common-password-list.md) · [user Spec](specs/user.md#계정과-자격) user-R14) |
| 이메일 | 로그인 식별자로만 쓰고 인증하지 않는다. 신원 근거로 쓰지 않는다 |
| 복구 코드 | 가입 직후(초기 비밀번호로 만든 계정은 첫 로그인에서 비밀번호를 바꾼 직후) 한 번 보여주는 무작위 코드(160비트, 계정마다 하나). SHA-256 다이제스트로만 저장. 복구 코드로 비밀번호를 재설정하면 기존 코드는 폐기하고 새 코드를 보여주며, 다른 세션을 모두 끊는다 — 재설정 전에 옛 비밀번호로 대조를 마친 로그인도 세션을 남기지 못한다([ADR-0224](adr/0224-sign-in-rechecks-the-credential-version-under-its-lock-after-storing-the-session-and-sessions-are-saved-as-they-change.md) · [user Spec](specs/user.md#로그인) user-R18). 설정 화면에서 재발급할 수 있다(현재 비밀번호 확인 필요) |
| 계정 복구 | 복구 코드로만 가능하다. 복구 코드가 없으면 복구할 수 없음을 가입 화면(초기 비밀번호로 만든 계정은 첫 로그인 화면)에서 안내한다 |
| 계정 존재 노출 방지 | 로그인 실패와 복구 코드 실패에 같은 응답을 준다. 가입(공개 가입 · 초대 링크로 가입) 시 이메일 중복은 메일 없이는 숨길 수 없으므로 "가입할 수 없는 이메일"이라는 일반 문구를 쓰고, 가입 요청 제한을 강하게 건다 |
| 무차별 대입 방어 | 연속 실패 시 지연을 점점 늘린다(잠금이 아니라 지연). 실패는 제출된 이메일마다 세어, 계정이 없는 이메일도 같은 기록 · 같은 지연을 받는다 — 지연 중에는 비밀번호를 대조하지 않고 남은 시간을 알린다. 복구 코드 실패는 같은 일정의 다른 기록으로 세고, 비밀번호 변경 · 복구 코드 재발급의 현재 비밀번호 확인 실패는 로그인 실패 기록에 센다([ADR-0144](adr/0144-recovery-code-failures-are-delayed-on-the-sign-in-schedule-in-a-separate-record-and-current-password-checks-share-the-sign-in-record.md) · [ADR-0130](adr/0130-sign-in-failure-delays-count-per-digest-of-the-submitted-normalized-email-regardless-of-account.md) · [user Spec](specs/user.md#로그인) user-R19). IP별 요청 제한, 봇 차단을 켠 인스턴스에서 로그인 · 복구 코드 사용 · 공개 가입의 봇 차단(Turnstile — 끌 수 있다) — [악용 방지](#악용-방지) |
| 초기 비밀번호 | 운영자가 만든 계정과 서버 설정으로 만든 첫 운영자 계정(초기 비밀번호로 만든 계정)은 첫 로그인 때 비밀번호를 바꿔야 한다. 바꾸기 전에는 서버가 비밀번호 변경 · 로그인 상태 조회 · 로그아웃만 연다([ADR-0146](adr/0146-sessions-of-accounts-with-an-initial-password-open-only-password-change-status-and-sign-out-marked-by-a-platform-annotation.md) · [user Spec](specs/user.md#비밀번호-변경과-복구-코드) user-R29) |
| 비밀번호 변경 | 현재 비밀번호 확인 필수. 변경 시 지금 세션의 ID 를 바꾸고 다른 세션 종료, 화면 내 알림 기록(첫 로그인의 강제 변경은 알림 없음 — [ADR-0145](adr/0145-password-notices-are-left-for-password-changes-and-recovery-resets-but-not-for-the-forced-first-sign-in-change.md)) |
| 2단계 인증 | v1 범위 밖. 이후 TOTP 추가 검토 |

## 인가와 데이터 격리

- 화면 · API 요청의 사용자 데이터 조회는 세션의 `userId`로 범위를 제한한다. 요청 파라미터로 받은 사용자 ID를 신뢰하지 않는다.
- 화면용 API 처리기는 사용자를 세션에서 채우는 인자로만 받고, 공개 표시가 없는 처리기는 세션을 요구한다([user Spec](specs/user.md#화면용-api-의-공개와-세션-사용자) user-R5 · user-R6).
- 사용자 범위가 없는 조회는 백그라운드 작업(다이제스트 일정 · 보존 정리 등) 전용 조회 모델에만 두고, 화면 · API 경로에서는 쓰지 않는다. 사용자 A로 사용자 B의 알림·다이제스트·키·피드백·스토리라인 구독·관심사 히스토리·변화 리포트에 접근하는 테스트를 기본 테스트 세트에 포함한다 — 운영자 경로 밖의 처리기는 공개 표시가 있거나 격리 시험에 등록되어야 하고, 등록 가드가 앱의 처리기를 열거해 확인한다([test-strategy](qa/test-strategy.md#13-사용자-격리-시험)).
- LLM 키가 없는 세션 사용자는 키 등록 · 계정(비밀번호 변경 · 복구 코드 재발급) · 알림 · 관심 설정만 쓴다 — 그 밖의 화면용 API 는 서버가 403 으로 막는다. 저장한 키가 하나라도 있으면(상태 무관) 키가 있는 것이다([user Spec](specs/user.md#화면용-api-의-공개와-세션-사용자) user-R5).
- 운영자 기능(`/api/admin/**`)은 `ADMIN` 역할만 접근할 수 있다. 운영자도 사용자의 LLM 키 평문은 볼 수 없다.
- 초대 링크 발급 · 취소, 계정 생성 · 초기 비밀번호 다시 정하기, 공개 가입 켬 · 끔은 `ADMIN` 만 할 수 있다.
- 운영자 경로는 `ADMIN` 계정 세션으로 인증하고, 세션으로 인증한 변경 요청에는 CSRF 토큰(공통 절)을 요구한다. 비밀번호를 바꿔야 하는 운영자는 바꾸기 전에 쓰지 못하고, LLM 키는 보지 않는다([ADR-0183](adr/0183-admin-paths-open-with-admin-account-sessions-and-keep-the-operator-token-as-an-optional-script-bearer-with-csrf-only-for-sessions.md) · [ADR-0185](adr/0185-admin-paths-stay-outside-the-llm-key-gate-and-look-only-at-the-role.md)).
- 스크립트 · 도구(평가 도구 · 태그 체계 curl · 배포 스모크)는 선택 설정인 운영자 토큰(서버 설정 하나)을 `Authorization: Bearer` 로 보낸다 — 운영자 경로만 열고, 쿠키 자격이 아니라 CSRF 를 면하며, 세션에 남지 않는다. 틀린 토큰은 세션으로 넘어가지 않고 거부된다. 화면은 토큰을 쓰지 않는다. 토큰 형식 · 대조 · 거부 응답의 규칙은 [user Spec "운영자 접근 통제"](specs/user.md#운영자-접근-통제)에 있다.

## BYOK 키 보관

**Envelope encryption**을 쓴다.

```
KEK (운영자가 주는 파일, 버전 관리)
 └─ 암호화 → DEK (키마다 무작위 생성, AES-256)
              └─ 암호화 → 사용자 API 키 (AES-256-GCM, AAD = user_id + provider)
```

- DB에는 `ciphertext`, `encrypted_dek`, `kek_version`, 끝 4자리 `key_hint`, 상태 · 시각만 저장한다. 저장 꼴과 규칙은 [credential Spec](specs/credential.md#봉투-암호) credential-R1 ~ R4 · R9 에 있다.
- AAD에 사용자 ID와 공급자를 묶어서, 암호문을 다른 사용자 행으로 옮겨도 복호화되지 않게 한다.
- 복호화는 `llm` 이 저장한 키의 길에서 물을 때만, **LLM 호출 직전에** `credential` 의 공급 구현이 한다. 복호화한 키를 내놓는 다른 길은 없다([credential Spec](specs/credential.md#저장한-키와-상태) credential-R13 · [llm Spec](specs/llm.md#저장한-키의-길) llm-R17 · R18). 평문은 래퍼 타입 `SecretValue` 로 다룬다 — 문자열 표현이 값을 숨기고 직렬화하면 실패한다([platform Spec](specs/platform.md#비밀-값) platform-R22).
- 키 등록 시 공급자에 과금되지 않는 가벼운 요청(OpenAI · Anthropic 은 모델 목록 조회, OpenRouter 는 키 조회)을 보내 유효성을 검증한다. 실패하면 저장하지 않는다. 요청과 실패 종류의 규칙은 [llm Spec](specs/llm.md#키-검증) llm-R6 ~ R10 에 있다 — 잔액 · 지출 한도 소진은 이 요청으로 드러나지 않고 생성 호출에서 드러난다.
- API 응답에서 키를 돌려주지 않는다. 화면에는 `…abcd`처럼 힌트(끝 4자리)만 보여준다(credential-R19).
- 키의 상태는 `VALID` · `INVALID` 둘이다. 저장한 키로 공급자에 물은 결과가 무효 · 권한 없음이면 `INVALID` 로 바꾸고 화면 내 알림을 남긴다(credential-R14 · R15). OpenRouter 의 403 은 권한 표시(`permission_denied`)가 있을 때만 권한 없음이다 — 입력 내용 차단(모델 거절 · 내용 필터 · 검열)으로 키가 무효가 되지 않는다([llm Spec](specs/llm.md#키-검증) llm-R8).
- 로그·예외 메시지·오류 추적 서비스에 키가 남지 않게 한다. LLM 호출의 실패 문구 · 로그는 고정 문구 · 상태 코드 · 예외 클래스 이름뿐이고, 공급자 SDK 의 요청 로그는 꺼 둔다 — `Authorization` · `x-api-key` 헤더와, 공급자가 오류 본문에 되돌려주는 키 조각이 남지 않는다([llm Spec](specs/llm.md#키-검증) llm-R9 · R12). 생성 호출의 프롬프트(사용자 프로필 · 관심사가 든다)와 응답 본문도 실패 문구 · 로그에 남기지 않고, 내용 없는 응답에서 프롬프트를 경고 로그에 남기는 Spring AI 채팅 모델의 로그는 앱 설정이 끈다([llm Spec](specs/llm.md#생성-호출) llm-R24).
- 사용자 키와 프롬프트는 공급자에만 간다. OpenRouter 는 중계 공급자라, 프롬프트가 OpenRouter 를 거쳐 그 모델의 회사나 호스팅 공급자로 간다 — 요청마다 학습에 쓰는 공급자를 빼는 라우팅을 싣고(보관은 공급자마다 다를 수 있다), 앱 표시 헤더(인스턴스 주소)를 싣지 않는다([llm Spec](specs/llm.md#생성-호출) llm-R23). 공급자 주소는 코드 상수이고 프로세스 환경 변수 · 운영 설정이 바꾸지 못하며, 전송은 리다이렉트를 따르지 않는다([llm Spec](specs/llm.md#공급자-클라이언트) llm-R11 · R13).
- KEK 는 운영자가 설정 디렉터리의 파일로 준다. 없거나 형식이 틀리거나, 저장된 키가 가리키는 버전이 빠지면 앱이 뜨지 않는다(credential-R5 · R6).
- KEK 교체: 새 KEK 버전을 추가하면 작업 역할의 반복 작업이 DEK를 다시 감싸고, 끝난 뒤 이전 버전을 폐기한다(credential-R7 · [배포와 운영](operations/deployment.md#llm-키-암호화-키kek)).
- 사용자가 키를 삭제하면 행을 즉시 물리적으로 삭제한다.

## LLM 입력과 출력

- RSS 제목 · 요약문 · 출처 이름과 자유 텍스트 관심사는 **신뢰할 수 없는 입력**이다. 지시와 사용자 구획을 나누고, 사용자 구획은 JSON 문서 하나로 실어 글이 구획 구조를 흉내 내지 못하게 하며, 그 안의 지시를 따르지 않도록 지시에 명시한다. DB ID 는 싣지 않고 요청마다의 별칭만 쓴다 — 규칙은 [digest Spec](specs/digest.md#프롬프트-구성) digest-R26.
- LLM 출력은 데이터로 다룬다.
  - 출력 JSON을 스키마로 검증하고, 인용 ID는 후보 목록과 대조한다 — 규칙은 [digest Spec](specs/digest.md#출력-읽기) digest-R4 ~ R6.
  - 링크는 DB에서 렌더링한다. LLM이 생성한 URL은 쓰지 않는다.
  - 종합문은 일반 텍스트(또는 제한된 마크다운)로 렌더링하고, `dangerouslySetInnerHTML`을 쓰지 않는다. 마크다운을 쓴다면 HTML을 허용하지 않는 렌더러와 링크 허용 목록을 쓴다.
- 자유 텍스트 관심사(`interest_text`)도 프롬프트에 들어가지만, 본인 키로 본인 다이제스트를 만드는 데만 쓰이므로 영향 범위가 본인에게 한정된다.

## 악용 방지

| 대상 | 대책 |
|---|---|
| 로그인, 복구 코드 사용 | 이메일별 실패 지연(사용자 축 — 위 자체 로그인 표) + IP별 요청 제한 + 봇 차단(켠 인스턴스) |
| 초대 링크 확인 · 초대로 가입 | IP별 요청 제한(토큰은 추측할 수 없는 길이) |
| 공개 가입(운영자가 연 경우) | IP별 요청 제한 + 봇 차단(켠 인스턴스) |
| 키 등록, 키 검증 | 사용자 · IP별 요청 제한 — 둘이 한도를 함께 쓴다(애플리케이션 레벨. Cloudflare 를 앞단에 두면 그 규칙을 더한다) |
| 좋아요 / 싫어요 누르기 · 되돌리기 | 사용자별 요청 제한 — 되풀이해 눌러 보존하는 이력을 늘리는 것을 묶는다([ADR-0198](adr/0198-feedback-recording-is-request-limited-per-session-user.md)) |
| 수동 다이제스트 재생성 | 사용자당 하루 N회 |
| 자유 텍스트 관심사 | 길이 제한 |
| 운영자 토큰(스크립트용 선택 설정) | 32자 이상 무작위 값으로 대입을 막는다. 대조는 상수 시간이고 요청 제한은 두지 않는다. 운영자 화면의 로그인은 자체 로그인의 실패 지연 · 요청 제한 · 봇 차단을 그대로 받는다 |
| 운영자 비용 (Jev) | 사용자 행동과 무관하게 기사 수에만 비례한다. 사용자 피드 추가를 열 때 별도 상한이 필요하다 |

- **요청 제한** — 처리기 앞에서 지키는 동작마다 클라이언트 주소 · 세션 사용자로 고정 창 하나의 요청을 세고, 넘으면 429 와 다시 시도할 때까지의 초를 준다. 세는 기록은 여러 앱 프로세스가 함께 쓰는 DB 에 있고, 대상(IP · 사용자 ID)은 다이제스트로만 둔다. 한도 · 축 · 창은 [user Spec "요청 방어"](specs/user.md#요청-방어) user-R38 이 정본이다([ADR-0169](adr/0169-request-limits-and-bot-checks-are-platform-marks-applied-by-the-user-session-chain-before-the-handler.md) · [ADR-0170](adr/0170-request-limits-count-fixed-windows-in-a-user-postgresql-table-with-a-code-constant-policy-table.md)). 창 경계 앞뒤로 한도의 두 배까지 몰릴 수 있다.
- **클라이언트 IP 의 신뢰 경계** — 클라이언트 IP 는 배포 proxy 가 정한다. proxy 는 운영자가 정한 신뢰 범위(기본 사설 대역)의 앞단(Tunnel · 리버스 프록시)에서 온 요청만 헤더의 IP 를 오른쪽부터 읽고(클라이언트가 왼쪽에 적은 값은 믿지 않는다), 앱으로 넘길 때 헤더 하나에 덮어쓴다. 앱은 그 헤더만 읽고, proxy 뒤에만 있어 호스트에 공개되지 않는다([ADR-0168](adr/0168-client-ip-is-read-by-the-proxy-from-trusted-ranges-right-to-left-and-passed-as-one-overwritten-header.md) · [운영 설정 "공개"](operations/configuration.md#공개)). 신뢰 범위를 잘못 두면 IP별 요청 제한이 무너진다 — 앞단 뒤인데 아무것도 믿지 않으면 모든 사용자가 앞단 주소 하나로 세여 한도를 함께 쓰고, 직접 공개한 proxy 앞의 클라이언트가 신뢰 범위 안에 있으면 헤더로 주소를 바꿔 한도를 비켜 갈 수 있다.
- **봇 차단** — 운영자가 켜고 끄며 기본은 끔이다(끄면 요청 제한만 남는다). 켜면 로그인 · 복구 코드 사용 · 공개 가입에서 화면의 Turnstile 위젯이 준 토큰을 서버가 Cloudflare 에 확인한다 — 토큰은 한 번만 쓰이고, 다른 화면의 토큰은 동작 이름이 달라 거부한다. 서버가 검증 서비스에 닿지 못하면 경고를 남기고 통과시킨다 — 그동안은 끈 것과 같다. 브라우저가 위젯 스크립트를 받지 못하면 그 화면은 제출할 수 없다([ADR-0172](adr/0172-bot-checks-use-turnstile-or-off-pass-when-the-verifier-is-unreachable-and-match-the-action.md) · [user Spec](specs/user.md#요청-방어) user-R41).
- **남의 로그인 늦추기(남은 위험)** — 실패 지연은 제출된 이메일마다 세므로, 남의 이메일로 일부러 틀려 그 사람의 로그인을 지연 상한까지 늦출 수 있다. 요청 제한은 IP 로만 세어(이메일로 세지 않는다 — 남이 한도를 써 버리는 길을 하나 더 만들지 않는다) 한 주소에서 실패를 빠르게 쌓는 속도만 줄이고, 여러 주소를 쓰는 공격자는 막지 못한다. 봇 차단을 켜면 실패 한 번마다 사람 확인이 필요해 비용이 오른다. 복구 코드 실패는 따로 세므로 그동안에도 복구 코드로는 비밀번호를 다시 정할 수 있다([ADR-0171](adr/0171-sign-in-and-recovery-are-limited-per-ip-while-the-email-axis-stays-the-failure-delay.md) · [ADR-0130](adr/0130-sign-in-failure-delays-count-per-digest-of-the-submitted-normalized-email-regardless-of-account.md)).

## 개인정보

- 수집 항목: 이메일, 표시 이름, 비밀번호 해시 / 관심 설정 / 관심 신호 이력(좋아요 · 싫어요 · 주제와 스토리라인의 구독 변경) / 다이제스트 · 변화 리포트 / 암호화된 LLM 키 / 요청 제한 기록(IP 주소 · 사용자 ID 의 다이제스트 — 제한 창이 끝나면 지운다, 창은 길어야 1시간). IPv4 주소의 다이제스트는 되돌리기 쉬우므로 IP 주소를 보관하는 것으로 고지한다.
- 클릭 · 열람은 추적하지 않는다. 원문 링크는 서비스를 거치지 않고 원문 사이트로 바로 이동한다 ([ADR-0028](adr/0028-explicit-signals-and-interest-weighted-candidates.md)).
- v1에서는 이메일을 외부로 전달하지 않는다. 메일 발송을 도입하면 이메일이 발송 서비스로 전달된다는 사실을 외부 전송 고지에 더한다.
- **외부 전송 고지**: 다이제스트를 생성할 때 사용자 프로필(구독 주제 · 지역 선호 · 자유 텍스트 관심사 · 시간대 · 요약 길이)과 후보 기사 정보 · 큰 스토리라인 묶음이, 변화 리포트를 생성할 때 그 기간의 구독한 스토리라인의 변화 · 보도량 · 관심도 통계와 직전 리포트의 요약이 **쓸 모델의 LLM 공급자**에게 전송된다 — 사용자가 고른 공급자(OpenAI · Anthropic · OpenRouter)이고, 고른 모델을 쓸 수 없으면 사용자가 키를 등록한 다른 공급자다. OpenRouter 의 모델이면 OpenRouter 를 거쳐 그 모델을 만든 회사나 그 모델을 호스팅하는 공급자에게 가고, 학습에 쓰는 공급자로는 보내지 않지만 공급자가 요청을 보관할 수는 있다(입력의 정본은 [pipeline.md](pipeline.md) §6 · §10, 쓸 모델은 [user Spec](specs/user.md#관심-설정) user-R51). 다이제스트의 사용자 프로필에는 시간대(발행 시각을 그 시간대로 적는다)와 요약 길이도 들고, 후보 기사 정보는 제목 · 요약문 · 출처 · 발행 시각 · 주제 · 지역이며, 큰 스토리라인 묶음(구독과 무관하게 넣은 기사가 어느 큰 흐름에 속하는지와 그 흐름의 대표 기사 제목)도 간다. 키 등록 화면과 고지 페이지에 명시한다. 봇 차단을 켠 인스턴스에서는 로그인 · 복구 · 가입 화면의 브라우저가 사람 확인을 위해 Cloudflare(Turnstile)에 직접 연결하고, 서버가 확인을 요청할 때 확인 토큰과 IP 주소를 보낸다 — 고지 페이지가 켠 인스턴스에서 이 항목을 보인다.
- 수집 항목과 외부 전송 항목은 로그인 없이 보는 고지 페이지(`/privacy`) 하나에 두고, 가입 · 초대 가입 · 키 · 설정 화면이 그 페이지로 링크한다([ADR-0167](adr/0167-collected-and-externally-sent-items-are-shown-on-one-notice-page-open-without-sign-in.md)).
- 탈퇴: 즉시 비식별화하고, 30일 후 완전히 삭제한다. 스토리라인 구독 · 관심 신호 이력 · 변화 리포트도 대상이다. 키는 탈퇴 즉시 삭제한다.
- 운영자가 둔 개인정보처리방침 · 이용약관의 주소를 운영 설정에 적으면 가입 · 키 등록 화면과 바닥글에 링크가 나온다([ADR-0047](adr/0047-self-hosted-software-run-by-instance-operators.md)).

## 저작권과 소스 이용조건

- 인스턴스 사용자의 화면과 외부 API에 노출하는 것은 **LLM 종합문, 기사 제목, 출처, 발행 시각, 원문 링크**로 한정한다. RSS 요약문 원문은 내지 않는다. 운영자 화면은 여기에 더해 RSS 요약문 · 피드 섹션 · RSS 카테고리를 보인다 — 운영자가 공유 평가를 평가기와 같은 입력으로 검수한다. 기사를 공중에 재배포하지 않는다 ([ADR-0068](adr/0068-rss-summaries-shown-only-on-operator-screens-and-users-get-processed-information.md)).
- 썸네일 이미지는 v1에서 표시하지 않는다(이미지 저작권). 나중에 쓰더라도 원본 URL을 그대로 참조하고 복사본을 저장하지 않는다.
- 소스(언론사)마다 RSS 이용조건 검토 날짜를 수집 프로필의 `termsReviewedAt` 에 기록한다 — 프리셋은 개발팀이 반드시 적고, 운영자 프로필은 운영자가 적는다. 검토일과 소스 활성화의 규칙은 [source Spec](specs/source.md#등록과-켜기)에 있다.
- 프리셋의 이용조건 검토는 개인 · 비상업 셀프 호스팅 인스턴스에서의 이용을 전제로 한다. 프리셋이 있는 소스를 실제로 켤지는 인스턴스 운영자가 자기 인스턴스의 이용 형태로 판단한다.
- 언론사가 삭제를 요청하면 운영자는 그 소스를 지울 수 있다 — 그 소스만 가진 기사가 요약문과 함께 지워지고, 다른 소스도 가진 기사는 남는다([source Spec 숨김과 삭제](specs/source.md#숨김과-삭제)). 수집만 멈추고 기사를 남기려면 숨긴다. 요약문의 보존 기간은 [data-model.md](data-model.md) 보존 정책을 따른다 ([ADR-0029](adr/0029-indefinite-summary-retention-with-optional-deletion.md)).
- 사건 묶기 평가 세트(사건 라벨을 붙인 기사 표본)와 스토리라인 평가 세트(덤프의 사건에 스토리라인 라벨을 붙인 표본)의 실제 기사 제목 · 요약문은 공개 저장소에 넣지 않는다. 라벨 파일 · 결과 파일 · 결과 기록 · 덤프도 인스턴스에 묶인 데이터라 공개 저장소에 두지 않고 운영자가 고른 디렉터리에 둔다 ([ADR-0026](adr/0026-event-clustering-with-local-embeddings-and-jev.md) · [ADR-0096](adr/0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)).
- 네이버 검색 API를 쓰지 않기로 한 결정은 [ADR-0001](adr/0001-news-source-rss.md) 참고.

## 운영자 프로필 입력

운영자 프로필 원문의 방어는 이 원문을 읽는 `source` 모듈의 규칙이다 — [source Spec "운영자 프로필 입력"](specs/source.md#운영자-프로필-입력).

## 피드 요청

피드 요청의 서버 측 요청 위험(내부 주소 · 리다이렉트 · 프록시 · 실패 문구) 방어는 `source` 모듈의 규칙이다 — [source Spec "피드 요청"](specs/source.md#피드-요청).

## 피드 XML 입력

피드 문서(신뢰할 수 없는 입력)의 방어는 `source` 모듈의 규칙이다 — [source Spec "피드 XML 입력"](specs/source.md#피드-xml-입력).
