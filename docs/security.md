# 보안 · 개인정보 · 저작권

## 인증

계정 · 로그인 · 세션 · 계정을 만드는 길 · 복구 코드의 규칙은 [account Spec](specs/account.md)에 있다. 첫 운영자의 값과 절차는 [운영 설정 "계정과 고지"](operations/configuration.md#계정과-고지) · [배포와 운영 "첫 운영자"](operations/deployment.md#첫-운영자)에 있다.

## 인가와 데이터 격리

- 화면 · API 요청의 사용자 데이터 조회는 세션의 `userId`로 범위를 제한한다. 요청 파라미터로 받은 사용자 ID를 신뢰하지 않는다.
- 화면용 API 처리기는 사용자를 세션에서 채우는 인자로만 받고, 공개 표시가 없는 처리기는 세션을 요구한다([account Spec](specs/account.md#화면용-api-의-공개와-세션-사용자) account-R5 · account-R6).
- 사용자 범위가 없는 조회는 백그라운드 작업(다이제스트 일정 · 보존 기한 지우기 등) 전용 조회 모델에만 두고, 화면 · API 경로에서는 쓰지 않는다.
- 사용자 A로 사용자 B의 아래 데이터에 접근하는 테스트를 기본 테스트 세트에 포함한다. 운영자 경로 밖의 처리기는 공개 표시가 있거나 격리 시험에 등록되어야 하고, 등록 가드가 앱의 처리기를 열거해 확인한다([test-strategy](qa/test-strategy.md#13-사용자-격리-시험)).
  - 알림
  - 다이제스트
  - 키
  - 피드백
  - 스토리라인 구독(추적 상태 포함)
  - 사용자 스토리라인의 정리와 그 사용자 스토리라인의 지난 다이제스트 항목
  - 관심사 히스토리
  - 구독 제안과 그 거절
  - 변화 리포트
- LLM 키가 없는 세션 사용자에게는 키 없이도 여는 표시가 있는 처리기만 열고, 그 밖의 화면용 API 는 서버가 403 으로 막는다. 그 처리기의 목록은 [account-R47](specs/account.md#화면용-api-의-공개와-세션-사용자)이 갖는다. 저장한 키가 하나라도 있으면(상태 무관) 키가 있는 것이다.
- 운영자 기능(`/api/admin/**`)은 `ADMIN` 역할만 접근할 수 있다. 운영자도 사용자의 LLM 키 평문은 볼 수 없다.
- 초대 링크 발급 · 취소, 계정 생성 · 초기 비밀번호 다시 정하기, 공개 가입 켬 · 끔은 `ADMIN` 만 할 수 있다.
- 운영자 경로는 `ADMIN` 계정 세션으로 인증하고, 세션으로 인증한 변경 요청에는 CSRF 토큰([account Spec "세션"](specs/account.md#세션))을 요구한다. 비밀번호를 바꿔야 하는 운영자는 바꾸기 전에 쓰지 못하고, LLM 키는 보지 않는다([ADR-0183](adr/0183-admin-paths-open-with-admin-account-sessions-and-keep-the-operator-token-as-an-optional-script-bearer-with-csrf-only-for-sessions.md) · [ADR-0185](adr/0185-admin-paths-stay-outside-the-llm-key-gate-and-look-only-at-the-role.md)).
- 스크립트 · 도구(평가 도구 · 태그 체계 curl · 배포 스모크)는 선택 설정인 운영자 토큰(서버 설정 하나)을 `Authorization: Bearer` 로 보낸다 — 운영자 경로만 열고, 쿠키 자격이 아니라 CSRF 를 면하며, 세션에 남지 않는다. 틀린 토큰은 세션으로 넘어가지 않고 거부된다. 화면은 토큰을 쓰지 않는다. 토큰 형식 · 대조 · 거부 응답의 규칙은 [account Spec "운영자 접근 통제"](specs/account.md#운영자-접근-통제)에 있다.

## BYOK 키 보관

봉투 암호 · 복호화 위치 · 키 검증의 규칙은 [credential Spec](specs/credential.md) · [llm Spec](specs/llm.md)에, KEK 의 운영은 [배포와 운영 "LLM 키 암호화 키(KEK)"](operations/deployment.md#llm-키-암호화-키kek)에 있다. 평문을 다루는 규약은 [overview §6 "비밀 값"](overview.md#6-횡단-규약)이다.

- DB 덤프와 KEK 를 함께 얻은 사람은 사용자 키를 열 수 있다. 그래서 KEK 는 DB 백업과 다른 곳에 둔다

## LLM 입력과 출력

- RSS 제목 · 요약문 · 출처 이름은 **신뢰할 수 없는 입력**이다. 지시와 사용자 구획을 나누고, 사용자 구획은 JSON 문서 하나로 실어 글이 구획 구조를 흉내 내지 못하게 하며, 그 안의 지시를 따르지 않도록 지시에 명시한다. DB ID 는 싣지 않고 요청마다의 별칭만 쓴다 — 규칙은 [digest Spec](specs/digest.md#프롬프트-구성) digest-R26.
- LLM 출력은 데이터로 다룬다.
  - 출력 JSON을 스키마로 검증하고, 인용 ID는 후보 목록과 대조한다 — 읽기 · 스키마 그리기는 [platform Spec](specs/platform.md#llm-출력-읽기) platform-R31 · R32, 서술과 대조는 [digest Spec](specs/digest.md#출력-읽기) digest-R4 ~ R6 · [report Spec](specs/report.md#출력-서술과-읽기) report-R5 ~ R7.
  - 링크는 DB에서 렌더링한다. LLM이 생성한 URL은 쓰지 않는다.
  - 종합문은 일반 텍스트(또는 제한된 마크다운)로 렌더링하고, `dangerouslySetInnerHTML`을 쓰지 않는다. 마크다운을 쓴다면 HTML을 허용하지 않는 렌더러와 링크 허용 목록을 쓴다.
- 사용자 스토리라인의 정리도 같은 원칙이다 — 사건의 대표 기사 제목 · 요약문을 JSON 문서의 사용자 구획으로 싣고 꼭대기는 요청마다의 별칭으로만 가리키며, 출력을 서술대로 읽어 일반 텍스트로 그린다. 정리는 그 사용자의 키로 만든 글이라 그 사용자의 화면에만 내고 공용 스토리라인에는 두지 않는다. 로그에는 글과 프롬프트를 남기지 않는다. 지운 기사에서 나온 글이 남지 않게 그 기사를 넣은 정리는 지운다([user Spec](specs/user.md#사용자-스토리라인의-정리) user-R70 · R72)
- 변화 리포트도 같은 원칙이다 — 사용자 스토리라인의 정리 · 사건의 대표 기사 제목 · 주제 이름 · 직전 리포트 글을 JSON 문서의 사용자 구획으로 싣는다. 기사 수 · 관심 값은 싣지 않고 판정 · 변화는 낱말로 싣는다. 스토리라인 · 사건은 요청마다의 별칭으로만 가리키며 DB ID · 주제 키는 싣지 않는다. 출력은 서술대로 읽고 별칭을 입력과 대조한다. 로그에는 글 · 프롬프트 · 출력 원문을 남기지 않는다([report Spec](specs/report.md#프롬프트) report-R23)

## 악용 방지

| 대상 | 대책 |
|---|---|
| 로그인, 복구 코드 사용 | 이메일별 실패 지연([account Spec "로그인"](specs/account.md#로그인) account-R19) + IP별 요청 제한 + 봇 차단(켠 인스턴스) |
| 초대 링크 확인 · 초대로 가입 | IP별 요청 제한(토큰은 추측할 수 없는 길이) |
| 공개 가입(운영자가 연 경우) | IP별 요청 제한 + 봇 차단(켠 인스턴스) |
| 키 등록, 키 검증 | 사용자 · IP별 요청 제한 — 둘이 한도를 함께 쓴다(애플리케이션 레벨. Cloudflare 를 앞단에 두면 그 규칙을 더한다) |
| 좋아요 / 싫어요 누르기 · 되돌리기 | 사용자별 요청 제한 — 되풀이해 눌러 보존하는 이력을 늘리는 것을 묶는다([ADR-0198](adr/0198-feedback-recording-is-request-limited-per-session-user.md)) |
| 수동 다이제스트 생성 | 사용자당 하루(사용자 시간대) 세 번 — 다이제스트 행으로 센다. 생성 중이면 받지 않는다([digest Spec](specs/digest.md#수동-생성) digest-R31) |
| 운영자 토큰(스크립트용 선택 설정) | 32자 이상 무작위 값으로 대입을 막는다. 대조는 상수 시간이고 요청 제한은 두지 않는다. 운영자 화면의 로그인은 자체 로그인의 실패 지연 · 요청 제한 · 봇 차단을 그대로 받는다 |
| 운영자 비용 (Jev) | 사용자 행동과 무관하게 기사 수에만 비례한다. 사용자 피드 추가를 열 때 별도 상한이 필요하다 |

- **요청 제한** — 처리기 앞에서 지키는 동작마다 클라이언트 주소 · 세션 사용자로 고정 창 하나의 요청을 세고, 넘으면 429 와 다시 시도할 때까지의 초를 준다. 세는 기록은 여러 앱 프로세스가 함께 쓰는 DB 에 있고, 대상(IP · 사용자 ID)은 다이제스트로만 둔다. 한도 · 축 · 창은 [account Spec "요청 방어"](specs/account.md#요청-방어) account-R30 이 정본이다([ADR-0169](adr/0169-request-limits-and-bot-checks-are-platform-marks-applied-by-the-user-session-chain-before-the-handler.md) · [ADR-0170](adr/0170-request-limits-count-fixed-windows-in-a-user-postgresql-table-with-a-code-constant-policy-table.md)). 창 경계 앞뒤로 한도의 두 배까지 몰릴 수 있다.
- **클라이언트 IP 의 신뢰 경계** — 클라이언트 IP 는 배포 proxy 가 정하고, 앱은 proxy 가 넘긴 헤더 하나(`X-Real-IP`)만 읽는다([ADR-0168](adr/0168-client-ip-is-read-by-the-proxy-from-trusted-ranges-right-to-left-and-passed-as-one-overwritten-header.md)). 값은 [운영 설정 "공개"](operations/configuration.md#공개), 앞단을 붙이는 절차는 [배포와 운영 "공개 경로와 외부 서비스"](operations/deployment.md#공개-경로와-외부-서비스)에 있다.
  - proxy 는 운영자가 정한 신뢰 범위(기본 사설 대역)의 앞단(Tunnel · 리버스 프록시)에서 온 요청만 헤더의 IP 를 읽는다. 신뢰 범위 밖의 연결이면 그 연결의 주소가 클라이언트 IP 다.
  - 헤더는 오른쪽부터 읽어 신뢰 범위의 주소를 건너뛴 처음 주소를 클라이언트 IP 로 정한다. 클라이언트가 왼쪽에 적은 값은 믿지 않는다.
  - proxy 는 정한 IP 를 앱으로 넘길 때 `X-Real-IP` 에 덮어쓴다. 앱은 이 헤더를 믿으므로 proxy 뒤에만 두고 호스트에 공개하지 않는다.
  - 신뢰 범위를 잘못 두면 IP별 요청 제한이 무너진다. 앞단 뒤인데 아무것도 믿지 않으면 모든 사용자가 앞단 주소 하나로 세여 한도를 함께 쓰고, 한 사람이 한도를 넘기면 모두가 막힌다.
  - 앞단 없이 직접 공개한 proxy 앞의 클라이언트가 신뢰 범위 안에 있으면(같은 LAN, 원래 주소를 지우는 포트 전달 뒤의 클라이언트) 헤더로 주소를 바꿔 한도를 비켜 갈 수 있다.
- **봇 차단** — 운영자가 켜고 끄며 기본은 끔이다. 끄면 요청 제한만 남는다([ADR-0172](adr/0172-bot-checks-use-turnstile-or-off-pass-when-the-verifier-is-unreachable-and-match-the-action.md) · [account Spec](specs/account.md#요청-방어) account-R33). 켜는 값은 [운영 설정 "봇 차단"](operations/configuration.md#봇-차단)에 있다.
  - 켜면 로그인 · 복구 코드 사용 · 공개 가입에서 화면의 Turnstile 위젯이 준 토큰을 서버가 Cloudflare 에 확인한다. 토큰은 한 번만 쓰이고, 다른 화면의 토큰은 동작 이름이 달라 거부한다.
  - 서버가 검증 서비스에 닿지 못하면 경고를 남기고 통과시킨다. 그동안은 끈 것과 같다.
  - 브라우저가 위젯 스크립트를 받지 못하면(그 네트워크가 Cloudflare 를 막으면) 그 화면은 제출할 수 없다.
- **남의 로그인 늦추기(남은 위험)** — 실패 지연은 제출된 이메일마다 세므로, 남의 이메일로 일부러 틀려 그 사람의 로그인을 지연 상한까지 늦출 수 있다. 요청 제한은 IP 로만 세어 한 주소에서 실패를 빠르게 쌓는 속도만 줄이고, 여러 주소를 쓰는 공격자는 막지 못한다. 이메일로 세지 않는 것은 남이 한도를 써 버리는 길을 하나 더 만들지 않기 위해서다. 봇 차단을 켜면 실패 한 번마다 사람 확인이 필요해 비용이 오른다. 복구 코드 실패는 따로 세므로 그동안에도 복구 코드로는 비밀번호를 다시 정할 수 있다([ADR-0171](adr/0171-sign-in-and-recovery-are-limited-per-ip-while-the-email-axis-stays-the-failure-delay.md) · [ADR-0130](adr/0130-sign-in-failure-delays-count-per-digest-of-the-submitted-normalized-email-regardless-of-account.md)).

## 개인정보

- 수집 항목은 아래다. IPv4 주소의 다이제스트는 되돌리기 쉬우므로 IP 주소를 보관하는 것으로 고지한다.
  - 이메일, 표시 이름, 비밀번호 해시
  - 관심 설정
  - 관심 신호 이력 — 좋아요 · 싫어요, 주제 구독의 변경, 흐름 추적(스토리라인 구독)의 변경
  - 사용자 스토리라인과 그 정리 — 사용자 스토리라인은 관심 신호를 준 스토리라인이고 신호 이력에서 낸 값이다. 정리는 그 사용자의 LLM 키로 만든 제목 · 요약과 정리에 쓴 토큰 수다
  - 구독 제안 거절(주제 · 그때의 관심 값 · 시각)
  - 다이제스트 · 변화 리포트
  - 암호화된 LLM 키
  - 요청 제한 기록(IP 주소 · 사용자 ID 의 다이제스트) — 제한 창이 끝나면 지운다. 창은 길어야 1시간이다
- 클릭 · 열람은 추적하지 않는다. 원문 링크는 서비스를 거치지 않고 원문 사이트로 바로 이동한다 ([ADR-0028](adr/0028-explicit-signals-and-interest-weighted-candidates.md)).
- v1에서는 이메일을 외부로 전달하지 않는다. 메일 발송을 도입하면 이메일이 발송 서비스로 전달된다는 사실을 외부 전송 고지에 더한다.
- **외부 전송 고지** — 아래 입력이 **쓸 모델의 LLM 공급자**에게 전송된다. 키 등록 화면과 고지 페이지에 명시한다. 입력의 정본은 [digest Spec "프롬프트 구성"](specs/digest.md#프롬프트-구성)(digest-R26) · [user Spec "사용자 스토리라인의 정리"](specs/user.md#사용자-스토리라인의-정리) · [report Spec "프롬프트"](specs/report.md#프롬프트)(report-R23), 쓸 모델은 [user Spec](specs/user.md#관심-설정) user-R51 에 있다.
  - 다이제스트를 생성할 때 — 사용자 프로필(구독 주제 · 지역 선호 · 시간대 · 요약 길이)과 항목마다 사건의 표본 기사 정보다. 시간대는 발행 시각을 그 시간대로 적는 데 든다. 표본 기사 정보는 출처와 발행 시각, 주제와 지역, 제목과 요약문이다. 기사는 이번 요청의 항목 별칭 · 칸(주요 뉴스 · 헤드라인)이 붙은 항목과 사건으로 묶여 간다
  - 관심이 쌓인 사용자 스토리라인을 정리할 때 — 그 사용자 스토리라인의 사건마다 날짜 · 대표 기사 제목 · 요약문과 지난 정리의 글
  - 변화 리포트를 생성할 때 — 아래
    - 리포트 기간(날짜)
    - 추적한 사용자 스토리라인마다 그 정리(제목 · 흐름 요약 · 최근 전개)와 상태, 기간 안에 추적을 더했는지. 주간 리포트는 직전 주간과 견준 보도의 변화(판정 낱말)도 보낸다. 기사 수는 보내지 않는다
    - 추적한 사용자 스토리라인의 그 기간 사건마다 대표 기사 제목 · 날짜와 기간 안에 시작했는지
    - 관심 변화 — 새로 생기거나 사라진 주제 · 사용자 스토리라인, 사용자 스토리라인의 정리, 기간 안 주제 구독 변경(시각 · 더함 뺌 · 주제 이름). 관심 값은 보내지 않는다
    - 직전 리포트의 글(섹션 제목 · 본문). 월간 리포트는 그 달 주간 리포트의 글도 보낸다
  - 쓸 모델의 공급자는 사용자가 고른 공급자(OpenAI · Anthropic · OpenRouter)이고, 고른 모델을 쓸 수 없으면 사용자가 키를 등록한 다른 공급자다. OpenRouter 의 모델이면 OpenRouter 를 거쳐 그 모델을 만든 회사나 그 모델을 호스팅하는 공급자에게 간다. 학습에 쓰는 공급자로는 보내지 않지만 공급자가 요청을 보관할 수는 있다
- **외부 전송 고지(봇 차단)** — 봇 차단을 켠 인스턴스에서는 로그인 · 복구 · 가입 화면의 브라우저가 사람 확인을 위해 Cloudflare(Turnstile)에 직접 연결하고, 서버가 확인을 요청할 때 확인 토큰과 IP 주소를 보낸다. 고지 페이지가 켠 인스턴스에서 이 항목을 보인다.
- 수집 항목과 외부 전송 항목은 로그인 없이 보는 고지 페이지(`/privacy`) 하나에 두고, 가입 · 초대 가입 · 키 · 설정 화면이 그 페이지로 링크한다([ADR-0167](adr/0167-collected-and-externally-sent-items-are-shown-on-one-notice-page-open-without-sign-in.md)).
- **(미구현)** 탈퇴: 즉시 비식별화하고, 30일 후 완전히 삭제한다. 키는 탈퇴 즉시 삭제한다. 아래도 대상이다.
  - 사용자 스토리라인과 그 정리
  - 스토리라인 구독
  - 관심 신호 이력
  - 구독 제안 거절
  - 변화 리포트
- **(미구현)** 운영자가 둔 개인정보처리방침 · 이용약관의 주소를 운영 설정에 적으면 가입 · 키 등록 화면과 바닥글에 링크가 나온다([ADR-0047](adr/0047-self-hosted-software-run-by-instance-operators.md)).

## 저작권과 소스 이용조건

- 인스턴스 사용자의 화면과 외부 API에 노출하는 것은 **LLM 종합문, 기사의 제목 · 출처 · 발행 시각, 원문 링크**로 한정한다. RSS 요약문 원문은 내지 않는다. 운영자 화면은 여기에 더해 RSS 요약문 · 피드 섹션 · RSS 카테고리를 보인다 — 운영자가 공유 평가를 평가기와 같은 입력으로 검수한다. 기사를 공중에 재배포하지 않는다 ([ADR-0068](adr/0068-rss-summaries-shown-only-on-operator-screens-and-users-get-processed-information.md)).
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
