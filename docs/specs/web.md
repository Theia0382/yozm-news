# web 화면 Spec

프론트엔드(`frontend/`)의 화면 Spec 이다. 공통 구조와 쓰는 규칙은 [README.md](README.md)에 있고, 이 Spec 은 여덟 절을 [화면 Spec 의 뜻](README.md#화면-spec)으로 쓴다.

## 1. 책임과 범위

화면은 사용자에게 데이터를 보이고 사용자의 조작을 화면용 API 로 보낸다.

- **독자 화면** — 로그인한 사용자가 쓰는 화면이다
  - 다이제스트와 사건 흐름
  - 관심사 히스토리와 변화 리포트
  - 알림
  - 관심 설정 · LLM 키 · 계정 설정
- **운영자 화면** — `ADMIN` 계정이 쓰는 화면이다(`/admin`)
  - 소스 · 피드와 수집 현황
  - 평가와 태그 체계
  - 사건 · 흐름과 큰 화제
  - 계정 · 초대
- **로그인 밖의 화면** — 로그인하지 않고 여는 화면이다
  - 로그인과 복구 재설정
  - 공개 가입과 초대 가입
  - 개인정보 고지

화면이 하지 않는 것:

- 판정과 저장. 판정은 서버 모듈이 하고, 화면은 응답을 그린다
- 화면용 API 명세의 정의. 명세는 `api/openapi.json` 이 정본이고 화면은 그 명세에서 만든 타입을 쓴다([ADR-0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md))
- 사용자 범위의 판단. 화면이 보내는 사용자 ID 는 없고, 서버가 세션 사용자로 범위를 정한다

## 2. 용어

화면 이름의 정본이다([ADR-0328](../adr/0328-the-frontend-has-one-screen-spec-web-holding-screen-names-routes-and-screen-behavior.md)). 개발 용어의 뜻은 [overview.md §8](../overview.md#8-용어)과 각 모듈 Spec 의 §2 에 있다.

| 화면 이름 | 쓰는 화면 | 개발 용어 |
|---|---|---|
| 사건 흐름 · 흐름 | 탭 · 메뉴 · 화면 제목은 "사건 흐름", 본문은 "흐름". 독자 화면과 운영자 화면이 함께 쓴다 | 스토리라인(`storyline`). 독자 화면의 사건 흐름 탭은 정리된 사용자 스토리라인을 보인다 |
| 개인 흐름 화면 | `/user-storylines/:id` | 정리된 사용자 스토리라인 하나 |
| 흐름 요약 · 최근 전개 | 사건 흐름 탭 · 개인 흐름 화면 | 정리의 칸 `summary` · `recent` |
| 갈래 · "갈래 N개" | 사건 흐름 탭의 카드 · 개인 흐름 화면의 "이 흐름의 갈래" | 사용자 스토리라인의 추적 대상 꼭대기와 그 수 |
| 공용 타임라인 | `/storylines/:id` | 스토리라인 하나의 타임라인 |
| 흐름 추적 | 흐름 추적 버튼 · `/storylines/subscriptions` | 스토리라인 구독 |
| 큰 화제 | 다이제스트 항목의 배지 · 운영자 화면 | 큰 스토리라인, 선정 이유 `MAJOR_STORYLINE` |
| 큰 화제 판정 · 큰 화제 기준 | 운영자 화면 | 큰 판정 · 큰 판정 기준 |
| 구독 주제 · 관심 반영 | 다이제스트 항목의 배지 | 선정 이유 `SUBSCRIBED` · `INTEREST` |
| 갈래(사건 표시) | 공용 타임라인의 사건 줄 | 포함의 하위 스토리라인에서 센 사건 |
| 처리 현황 | 운영자 화면 `/admin/processing` | 반복 작업의 회차(`*Round`)마다의 대기 현황(`RoundQueue`)과 회차 기록. 회차 이름은 "흐름 잇기"처럼 화면 말로 바꿔 보인다(web-R30) |
| 띠 | 운영자 화면의 사건 흐름 소속 근거 | 스토리라인 잇기의 아래 기준 이상 위 기준 미만 |

## 3. 공개 인터페이스

### 화면 경로

`frontend/src/app/routes.tsx` 의 화면이다. 독자 화면은 로그인 확인(`RequireSignIn`) 아래에 있다.

| 경로 | 화면 | 기능 폴더 |
|---|---|---|
| `/` | 다이제스트 홈 | `digests` |
| `/digests` | 다이제스트 날짜 목록 | `digests` |
| `/digests/:date` | 날짜의 다이제스트 | `digests` |
| `/notices` | 알림 | `notices` |
| `/settings` | 계정 설정 | `account` |
| `/interests` | 관심 설정 | `interests` |
| `/storylines` | 사건 흐름 탭 | `storylines` |
| `/storylines/subscriptions` | 흐름 추적 목록 | `storylines` |
| `/storylines/:id` | 공용 타임라인 | `storylines` |
| `/user-storylines/:id` | 개인 흐름 화면 | `storylines` |
| `/interest-history` | 관심사 히스토리 | `interestHistory` |
| `/reports` | 변화 리포트 목록 | `reports` |
| `/reports/:id` | 변화 리포트 한 편 | `reports` |
| `/keys` | LLM 키 | `keys` |
| `/password` | 비밀번호 변경 | `account` |
| `/login` | 로그인 | `session` |
| `/sign-up` | 공개 가입 | `account` |
| `/invite` | 초대 가입 | `account` |
| `/recover` | 복구 재설정 | `account` |
| `/privacy` | 개인정보 고지 | `privacy` |
| `/admin` | 소스 | `admin` |
| `/admin/collection` | 수집 현황 | `admin` |
| `/admin/evaluation` | 평가 현황 | `admin` |
| `/admin/processing` | 처리 현황 | `admin` |
| `/admin/evaluations` | 평가 목록 | `admin` |
| `/admin/evaluations/:id` | 평가 상세 | `admin` |
| `/admin/samples` | 평가 검수 | `admin` |
| `/admin/tag-sets` | 태그 체계 | `admin` |
| `/admin/thresholds` | 태그 기준 | `admin` |
| `/admin/events` | 사건 목록 | `admin` |
| `/admin/events/:id` | 사건 상세 | `admin` |
| `/admin/articles/:id` | 기사 상세 | `admin` |
| `/admin/storylines` | 사건 흐름 목록 | `admin` |
| `/admin/storylines/:id` | 사건 흐름 상세 | `admin` |
| `/admin/big-storylines` | 큰 화제 | `admin` |
| `/admin/event-judgements` | 사건 판정 기록 | `admin` |
| `/admin/event-publications` | 이벤트 발행 | `admin` |
| `/admin/accounts` | 계정 · 초대 · 가입 정책 | `admin` |
| `/admin/sources/new` | 소스 등록(원문 편집) | `admin` |
| `/admin/sources/:id/profile` | 소스 원문 편집 | `admin` |

### 화면이 부르는 화면용 API

기능 폴더의 코드가 부르는 경로다. 다른 기능 폴더의 조회를 가져다 쓰는 화면은 그 폴더의 경로도 부른다. 경로의 요청 · 응답 형식은 `api/openapi.json` 에 있다.

| 기능 폴더 | 화면용 API |
|---|---|
| `session` | `/api/session` · `/api/me` |
| `account` | `/api/sign-up-policy` · `/api/sign-ups` · `/api/invitation-checks` · `/api/invitation-acceptances` · `/api/account-recoveries` · `/api/account/password` · `/api/account/recovery-code` |
| `humanCheck` | `/api/human-check` |
| `digests` | `/api/digests*` · `/api/feedback*` · `/api/topic-suggestions` · `/api/interests/declined-suggestions` |
| `storylines` | `/api/user-storylines*` · `/api/storylines*` · `/api/storyline-cards` · `/api/storyline-subscriptions*` |
| `interestHistory` | `/api/interest-history` |
| `reports` | `/api/reports*` |
| `notices` | `/api/notices*` |
| `interests` | `/api/interests/*` |
| `keys` | `/api/llm-keys*` |
| `admin` | `/api/admin/**` · `/api/human-check` |
| `privacy` | 없음 |

## 4. 내부 구조

### 개관

| 자리 | 책임 |
|---|---|
| `app` | 화면 경로(`routes.tsx`), 바깥 틀(`RootLayout`), 조회 캐시(`queryClient`) |
| `features/{기능}` | 화면 하나 또는 몇 개와 그 조회 · 요청 · 문구. 폴더는 `account` · `admin` · `digests` · `humanCheck` · `interestHistory` · `interests` · `keys` · `notices` · `privacy` · `reports` · `session` · `storylines` 열두 개다 |
| `shared/api` | 화면용 API 클라이언트(openapi-fetch)와 명세에서 만든 타입 |
| `shared/lib` | 여러 기능이 쓰는 보조 함수 |
| `shared/ui` | 공용 부품(shadcn/ui) |

조회와 요청은 TanStack Query 로 하고, 로그인 상태가 바뀌면 조회 캐시를 비운다([web-R3](#로그인과-계정)).

## 5. 규칙과 불변식

판단의 이유는 각 하위 절의 ADR 에 있다. 서버가 지키는 계약은 모듈 Spec 의 규칙이 갖고, 아래 규칙은 그 규칙 식별자를 가리킨다.

### 로그인과 계정

판단의 이유는 [ADR-0141](../adr/0141-invitation-links-are-seven-day-single-use-links-not-bound-to-an-email-with-the-token-in-the-fragment-stored-only-as-a-digest.md)(초대 링크)에 있다.

- **web-R1** — 운영자 틀은 로그인 상태([account-R20](account.md#로그인))로 가른다.
  - 로그인하지 않았으면 로그인 화면(`/login`)으로 보내고, 강제 변경이면 비밀번호 변경 화면(`/password`)으로 보낸다. 둘 다 돌아올 주소(`next`)를 단다
  - `ADMIN` 이 아니면 권한 없음을 알린다
  - LLM 키가 없어도 열고, 머리줄에 알림 링크 · 사용자 화면 링크 · 이름 · 로그아웃을 둔다
  - 운영자 API 가 401 이면 로그인 상태를 다시 읽는다
  - 사용자 화면의 머리줄은 `ADMIN` 이면 운영자 화면 링크를 둔다
- **web-R2** — 화면은 운영자 토큰([account-R2](account.md#운영자-접근-통제))을 받지도 보내지도 않는다
- **web-R3** — 로그인 상태에 따른 이동과 캐시:
  - 강제 변경을 먼저 본다. 그 다음 `llmKeyRequired` 면 아래 화면 밖의 주소를 키 화면(`/keys`)으로 보낸다. 비밀번호 변경 화면은 키 없이 여는 계정 처리기를 쓰므로 첫 로그인의 변경 뒤 돌아갈 곳으로 간다
    - 키 · 설정 · 비밀번호 변경 화면
    - 알림 · 관심 설정(`/interests`) 화면
  - 로그인 상태 조회가 앞서 읽은 것과 다른 사용자(익명 포함)를 돌려주면 로그인 상태 조회 밖의 조회 캐시를 모두 비운다. 지금 화면의 조회는 새 사용자로 다시 받는다. 같은 창에서 바뀐 사용자와 같은 브라우저의 다른 탭에서 바뀐 사용자(탭이 다시 보일 때 다시 읽는다)의 화면에도 앞 사용자의 데이터가 그려지지 않는다
  - 로그인 · 가입 · 복구 재설정 · 강제 변경 뒤에는 로그인 상태를 다시 읽은 뒤 화면을 옮긴다. 로그아웃하면 캐시를 모두 버린다
  - 관심 설정 · LLM 키 · 알림 화면은 조회가 401 이면 로그인 상태를 다시 읽는다. 로그인 이동은 다시 읽은 로그인 상태로 정한다
- **web-R4** — 강제 변경 중인 사용자는 어느 화면에서든 비밀번호 변경 화면(`/password`)으로 보내고, 머리줄에 이름 · 로그아웃만 둔다([account-R22](account.md#비밀번호-변경과-복구-코드)).
  - 바꾸고 복구 코드 저장을 확인하면 돌아갈 곳(`next`)으로 간다. `next` 는 같은 오리진의 경로만 받고 운영자 틀이 단다(web-R1). 없으면 첫 화면으로 간다
  - 설정(`/settings`)이 비밀번호 변경과 복구 코드 재발급을 낸다
- **web-R5** — 복구 코드([account-R23](account.md#비밀번호-변경과-복구-코드))는 한 번 보이고, 복사 단추와 다시 볼 수 없다는 안내를 둔다. 저장했다고 확인해야 다음으로 간다. 복구 재설정 화면은 `/recover` 이고 로그인 화면이 링크한다
- **web-R6** — 초대 링크는 운영자 화면이 자기 주소로 만든 `/invite#토큰` 이다. 토큰은 조각에 있어 서버로 가지 않고, 초대 가입 화면이 상태 확인 · 가입 요청 본문에 싣는다([account-R24](account.md#초대))
- **web-R7** — 가입 화면([account-R26](account.md#가입)):
  - 공개 가입 화면은 `/sign-up` 이고 가입 정책이 열렸을 때만 입력을 보인다. 로그인 화면은 가입 정책이 열렸을 때만 가입 링크를 둔다
  - 같은 이메일의 계정이 있어 받지 않은 가입(`EMAIL_UNAVAILABLE`)은 "가입할 수 없는 이메일"이라고만 한다
  - 가입하면 복구 코드를 보인 뒤 첫 화면으로 간다
- **web-R8** — 봇 차단([account-R33](account.md#요청-방어)): 공개 조회 `GET /api/human-check` 가 `TURNSTILE` 이면 그 사이트 키 · 동작 이름으로 위젯을 띄운다.
  - 확인을 마쳐야 제출하고, 제출마다 위젯을 다시 받는다(토큰은 한 번만 쓰인다)
  - 설정을 읽지 못했으면 칸 없이 보낸다
  - 위젯 스크립트를 받지 못하면 제출할 수 없는 까닭을 말한다
- **web-R9** — 처음 키를 등록하면 관심 설정(`/interests`)으로 보낸다. 구독 주제가 없으면 홈과 관심 설정 화면에 안내한다([user-R43](user.md#관심-설정))

### 다이제스트

판단의 이유는 [ADR-0237](../adr/0237-the-digest-home-shows-the-latest-date-and-every-date-has-its-own-page-and-a-list.md) · [ADR-0238](../adr/0238-a-digest-date-shows-the-visible-digest-with-the-latest-attempt-and-the-list-holds-every-date-with-a-state.md) · [ADR-0239](../adr/0239-digest-screens-show-tokens-of-the-visible-digest-and-the-sum-of-every-attempt-of-the-date-without-prices.md) · [ADR-0241](../adr/0241-selection-reasons-mark-only-citations-outside-subscribed-topics-named-interest-and-big-topic.md) · [ADR-0249](../adr/0249-topic-suggestions-show-as-a-card-on-the-digest-home-and-accepting-uses-the-tag-preference-save.md) · [ADR-0262](../adr/0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md) · [ADR-0265](../adr/0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md) · [ADR-0284](../adr/0284-a-digest-item-keeps-the-top-that-grouped-it-on-its-section-and-items-with-a-flow-carry-a-flow-tracking-button.md)에 있다.

- **web-R10** — 날짜의 다이제스트([reader-R9](reader.md#다이제스트)):
  - 수동 생성 상태의 시각은 사용자 시간대로 적는다
  - 주요 뉴스 · 헤드라인 항목마다 머리 · 본문 · 출처 기사 목록을 그린다. 머리는 제목, 선정 이유 배지, 좋아요 / 싫어요 버튼 한 벌이다. 구독 주제(`SUBSCRIBED`)와 개요에는 배지가 없다. 출처 기사 목록은 인용마다 제목 링크 · 출처 · 발행 시각이다
  - 반응 버튼은 항목 키(`eventIds` 의 첫 사건)로 누른다. 그 항목에 누른 값은 보이는 다이제스트 ID 로 `feedback` 의 항목 기록 조회([feedback-R7](feedback.md#화면의-항목-기록-조회))에서 읽는다. 같은 사건이 든 다른 날짜의 항목에 누른 값은 보이지 않는다
  - 개요에는 버튼이 없다. 반응 버튼 한 벌의 접근 이름은 항목 제목을 담는다
  - 스토리라인이 있는 항목(`storylineId` 가 있는 주요 뉴스 · 헤드라인)의 머리에는 흐름 추적 버튼을 둔다. 사건 흐름 탭 · 개인 흐름 화면 · 타임라인의 버튼과 같은 버튼이고, 누르는 대상은 `storylineId` 다. 추적 상태는 그 다이제스트의 `storylineId` 전부를 `user` 의 추적 상태 조회([user-R67](user.md#사용자-스토리라인))에 한 번 물어 읽는다. 버튼의 접근 이름은 항목 제목을 담는다
  - 정리가 있는 항목(`userStorylines` 가 비지 않은 것)은 제목 아래 한 줄에 정리의 제목들을 받은 순서대로 개인 흐름 화면 링크로 둔다. 이 줄은 제목 요소 밖이다
  - 헤드라인 항목은 머리글 하나 아래에 모은다. 사용량 줄의 후보 수는 사건 수다
- **web-R11** — 구독 제안([reader-R12](reader.md#구독-제안)): 다이제스트 홈은 제안이 있을 때만 카드를 그린다.
  - 수락은 지금 태그 선호에 그 중분류를 더해 `user` 의 태그 선호 저장(`PUT /api/interests/tags`)으로 보낸다. 체계 버전은 제안 응답의 `tagSetId` 다
  - 거절은 `user` 의 `POST /api/interests/declined-suggestions`([user-R64](user.md#구독-제안-거절))로 보낸다
  - 선호와 제안의 체계 버전이 다르면 수락을 보내지 않고 둘을 다시 읽는다

### 사건 흐름

판단의 이유는 [ADR-0242](../adr/0242-the-storyline-subscription-list-lives-on-the-key-gated-storyline-screen.md) · [ADR-0290](../adr/0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md) · [ADR-0291](../adr/0291-tokens-spent-on-a-summary-are-shown-on-the-personal-flow-screen-as-the-last-summary-and-the-running-total.md) · [ADR-0292](../adr/0292-reader-screens-call-a-storyline-an-event-flow.md)에 있다.

- **web-R12** — 사건 흐름 탭과 개인 흐름 화면([reader-R13](reader.md#정리된-사용자-스토리라인)):
  - 사건 흐름 탭은 카드마다 아래를 그린다. 정리된 사용자 스토리라인이 없으면 어떻게 생기는지 안내한다
    - 제목(개인 흐름 화면 링크)
    - 흐름 요약 · 최근 전개 · 정리 시각
    - 꼭대기 수(둘 이상일 때, 화면 문구 "갈래 N개")
    - 흐름 추적 버튼
  - 개인 흐름 화면은 사용자 스토리라인 전체의 정리, 꼭대기마다의 정리와 공용 타임라인 링크, 지난 다이제스트 항목을 그린다. 꼭대기가 하나면 링크만 두고, 글 없는 꼭대기는 대체 제목과 링크만 둔다
  - 개인 흐름 화면의 맨 아래에는 작은 줄 둘을 둔다. 마지막 정리의 모델 · 토큰과, 호출이 둘 이상이면 모든 호출의 합이다
  - 흐름 추적 버튼은 헤드(`storylineId`)로 추적 · 해제하고 추적 상태를 읽는다. 헤드는 그 사용자 스토리라인에만 들어 해제가 다른 사용자 스토리라인을 끄지 않는다([user-R52 · R67](user.md#사용자-스토리라인))
- **web-R13** — 개인 흐름 화면의 지난 다이제스트 항목은 그 날짜의 다이제스트 화면으로 잇는다([reader-R14](reader.md#정리된-사용자-스토리라인))
- **web-R14** — 흐름 추적 목록과 추적 이력은 제목을 카드 조회로 붙인다. 추적 이력은 한 쪽의 제목을 카드 한 번으로 붙인다. 흐름 추적 목록의 줄 수가 카드 처리기 한 번의 상한(`StorylineDirectory.MAX_PAGE_SIZE`)보다 많을 수 있어 카드를 상한씩 나눠 묻는다([user-R63](user.md#스토리라인-구독))

### 관심사 히스토리

판단의 이유는 [ADR-0293](../adr/0293-the-interest-history-also-shows-the-interest-of-summarized-flows.md)에 있다.

- **web-R15** — 관심사 히스토리 화면은 주제 표와 사용자 스토리라인 표를 따로 그리고, 사용자 스토리라인의 제목은 개인 흐름 화면으로 잇는다. 주제 행이 없어도 사용자 스토리라인이 있으면 그 표를 보인다([reader-R8](reader.md#관심사-히스토리))

### 변화 리포트

판단의 이유는 [ADR-0319](../adr/0319-the-change-report-screen-has-a-list-per-kind-and-a-report-page-with-four-states-and-the-failure-cause-only.md) · [ADR-0325](../adr/0325-a-failed-report-is-regenerated-from-the-screen-on-the-same-row-up-to-three-times.md) · [ADR-0337](../adr/0337-the-change-report-screen-shows-no-numbers-in-text-interest-as-history-cells-and-judgements-as-coloured-words.md) · [ADR-0338](../adr/0338-a-change-report-writes-only-kind-sections-without-an-overview.md) · [ADR-0339](../adr/0339-change-report-kinds-are-tracked-flows-and-interest-changes-and-it-leaves-out-subscribed-topic-coverage.md) · [ADR-0341](../adr/0341-a-monthly-report-does-not-compare-flow-coverage-with-the-previous-monthly-and-shows-the-flow-state-kept-at-generation.md)에 있다.

- **web-R16** — 목록 화면(`/reports`, [reader-R15](reader.md#변화-리포트))은 종류 탭 둘(주간 · 월간)을 둔다. 탭은 주소의 `kind` 이고, 없거나 모르는 값이면 주간이다.
  - 머리의 설명은 리포트가 추적한 흐름과 관심의 변화를 다룬다고 말한다
  - 탭마다 최근 순으로 줄마다 기간 링크와 상태를 그린다. 상태는 만드는 중 · 보일 변화 없음 · 만들지 못함이고, 내용 있음은 적지 않는다
  - 리포트가 없으면 관심 설정에서 켜면 만들어진다고 안내한다
- **web-R17** — 한 편 화면(`/reports/:id`, [reader-R16](reader.md#변화-리포트))은 상태 넷을 가른다.
  - 만드는 중(`SCHEDULED` · `RUNNING`)과 비어 있음(`EMPTY`, 담을 변화가 없었다). 비어 있음의 안내는 추적한 흐름도, 새로 생기거나 사라진 관심 · 바꾼 구독도 없었다고 말한다
  - 실패(`FAILED`)는 실패 코드마다 무슨 일 문구를 보이고 메시지는 보이지 않는다. 남은 다시 만들기 수가 있으면 그 수와 다시 만들기 버튼을 둔다. 다 썼으면 버튼 없이 다 썼다고 안내한다
  - 다시 만들기 버튼은 요청하는 동안 막는다. 받으면 리포트를 다시 읽어 만드는 중이 되고, 받지 않으면 까닭을 보인다. 까닭은 다시 읽은 리포트가 실패가 아니어도(다른 곳에서 먼저 다시 만들었다) 한 편 화면에 남는다
  - 내용(`READY`)은 종류 섹션마다 섹션 종류의 고정 제목(추적한 흐름 · 관심의 변화)을 머리로 두고, 그 아래 모델이 쓴 제목을 요지 한 줄로 둔다. 이어 본문 · 표(web-R26 ~ R29) · 인용 목록(접힘)을 그린다. 고정 제목의 정본은 `reportTexts.ts` 의 `sectionHeadings` 다
  - 숨긴 섹션이 있으면 그 수를 보이고, 섹션이 하나도 없으면(종류 섹션이 모두 숨음) 글 대신 안내를 보인다
  - 스토리라인의 이름은 정리된 사용자 스토리라인의 제목(개인 흐름 화면 링크)이고, 없으면 대표 기사 제목(공용 타임라인 링크), 그것도 없으면 이름 없는 스토리라인(공용 타임라인 링크)이다
  - 사건은 원문 링크(새 창) · 출처 · 발행 시각이고, 대표 기사가 없으면 링크 없는 안내다
  - 직전 리포트 줄은 생성 때 직전 리포트를 이은 리포트에만 그린다. 그런 리포트는 내용 리포트와 시작한 실패 리포트(사용량이 있다)다. 문구는 리포트 종류마다 다르고, 정본은 `reportTexts.ts` 의 `previousLink` · `noPreviousTexts` 다
    - 주간은 직전 리포트가 있으면 "비교한 직전 주간 리포트 · 기간" 링크를, 없으면 비교가 빈 까닭의 안내를 둔다
    - 월간은 직전 값과 견주지 않아([report-R174](report.md#직전-리포트와-스냅숏)) "비교한" 없이 "직전 월간 리포트 · 기간" 링크를, 없으면 직전 월간 리포트가 없다는 안내를 둔다
  - 맨 아래에 모델 · 토큰 · 끝낸 시각을 적는다
  - 진입은 머리줄의 "변화 리포트"와 관심 설정 리포트 절의 링크다
- **web-R26** — 한 편 화면의 표는 수를 글자로 보이지 않는다. 수는 아래 칸의 `title` 과 화면 낭독에만 둔다. 응답은 수를 그대로 싣는다([reader-R61](reader.md#변화-리포트))
  - 주간 흐름 표의 판정 칸 — "이번 기간 N건 · 직전 기간 M건". 직전이 없으면 "없음"이다
  - 월간 흐름 표의 상태 칸 — "이번 기간 N건"
  - 관심 표의 기간 시작 · 끝 칸 — 관심 값(소수 둘째 자리). 값이 없으면 "신호 없음"이다
- **web-R27** — 흐름 표(`STORYLINES` 의 숫자, [report-R74](report.md#리포트-조회))의 이름과 칸은 리포트 종류로 고른다
  - 주간 — 표 이름은 "보도"이고 칸은 흐름 · 판정이다
  - 월간 — 표 이름은 "상태"이고 칸은 흐름 · 상태다. 상태는 생성 때 헤드의 상태이고, 낱말은 다른 화면과 같다(`shared/lib/storylineState.ts` 의 `storylineStateLabels`). 상태를 남기지 않은 옛 리포트의 줄은 상태 칸이 비어 있다
- **web-R28** — 관심 표는 "주제" · "흐름" 둘이다. 기간 시작 · 끝 칸은 관심사 히스토리(web-R15)의 칸과 같은 색 네모이고, 색 판단의 정본은 `shared/lib/interestLevel.ts` 다
  - 관심 쪽은 주 색, 싫어요 쪽은 경고 색, 신호 없음과 0 은 바탕이다
  - 진하기는 관심의 크기다
- **web-R29** — 판정 · 상태 · 변화 칸은 색 낱말이다. 색의 정본은 `reportTexts.ts` 의 `trendTones` · `stateTones` · `shiftTones` 다

  | 칸 | 주 색 | 경고 색 | 바탕 |
  |---|---|---|---|
  | 판정 | 늘었음 · 새로 생김 | 줄었음 | 비슷함 · 비교 없음 |
  | 상태 | 진행 | — | 잠잠 · 끝남 |
  | 변화 | 새로 생긴 관심 | 사라진 관심 | 변화 없음 |

### 알림

판단의 이유는 [ADR-0233](../adr/0233-digest-failure-notices-come-through-an-event-declared-by-the-receiving-user-module-for-every-failed-digest.md) · [ADR-0240](../adr/0240-digest-failures-show-the-cause-action-and-the-code-and-never-the-failure-message.md)에 있다.

- **web-R18** — 안 읽은 수([user-R27](user.md#화면-내-알림))는 로그인한 화면에 들어올 때 · 경로를 옮길 때 · 창으로 돌아올 때 · 다시 연결될 때 다시 읽고, 주기 조회는 하지 않는다. 알림 페이지에서 읽음을 보내면 안 읽은 수를 다시 읽는다
- **web-R19** — 다이제스트 실패 알림([user-R62](user.md#화면-내-알림)):
  - 실패 원인마다 할 일 문구(다이제스트 화면과 같은 문구)를 보이고 그 날짜의 다이제스트 화면으로 잇는다
  - 실패 원인마다 가 볼 화면으로도 잇는다. `KEY` 면 LLM 키 화면, `OUTPUT` 이면 관심 설정의 모델 절이다
  - 다시 만들기가 할 일인 실패 원인(`TEMPORARY` · `OUTPUT`)의 알림이 해결되지 않은 마지막 실패면 다시 만들기 버튼이 있는 홈으로도 잇는다. 해결되지 않은 마지막 실패는 `digest` 의 수동 생성 상태([digest-R40](digest.md#다이제스트-조회))가 정한다. 화면은 실패 알림 본문이 홈 응답을 읽어 날짜와 실패 시각이 같은 알림에만 그 링크를 단다. 실패 알림이 없으면 홈 응답을 읽지 않고, 읽지 못하면 그 링크만 달지 않는다
- **web-R20** — 리포트 실패 알림([user-R75](user.md#화면-내-알림))은 종류 · 기간 날짜 · 실패 원인마다 할 일 문구 · 실패 시각을 보이고 그 리포트 화면으로 잇는다.
  - 할 일 문구는 리포트용 표이고 다이제스트의 문구와 따로 둔다. 할 일이 다시 만들기인 실패 원인은 리포트 화면의 다시 만들기(web-R17)를 말한다
  - 실패 원인마다 가 볼 화면으로도 잇는다. `KEY` 면 LLM 키 화면, `OUTPUT` 이면 관심 설정의 모델 절이다

### 관심 설정

- **web-R21** — 일정 · 리포트 설정([user-R50](user.md#관심-설정)):
  - 주간 리포트를 끄면 월간 설정을 막고 그렇게 안내한다
  - 리포트를 만들지 않는 경우(생성 조건)는 `reader` 의 조건 처리기([reader-R17](reader.md#변화-리포트))가 낸 수로 안내한다. 일정 · 변화 리포트 절의 안내는 "그 주에 다이제스트를 받은 날이 {수}일보다 적으면 주간 리포트를, 그 기간 안의 주간 리포트가 {수}편보다 적으면 월간 리포트를 만들지 않습니다"다
  - 생성 조건을 읽지 못하면 수 없이 안내한다
  - 화면의 시각 표기는 브라우저 시간대다. 사용자 시간대는 일정의 기준이고(user-R50), 수동 생성 상태의 시각만 사용자 시간대로 적는다(web-R10)
- **web-R22** — 다이제스트 분량의 안내 문구는 큰 슬롯 사건의 상한(`SelectionRules.BIG_SHARE`, [digest-R19](digest.md#후보-선별))을 말로 적는다

### 운영자 화면

판단의 이유는 [ADR-0051](../adr/0051-operator-hides-or-deletes-sources-and-feeds-and-deletion-removes-exclusive-articles.md)(숨김과 삭제)에 있다.

- **web-R23** — 소스 · 피드의 숨김과 삭제는 두 기능의 차이([source Spec](source.md#숨김과-삭제))를 확인 대화상자로 밝힌다
- **web-R24** — 큰 화제 기준 미리보기([admin-R7](admin.md#5-규칙과-불변식))는 지금 기준과 제안 기준의 두 목록을 스토리라인 ID 로 견줘 들어오고 빠지는 스토리라인을 가린다
- **web-R25** — 가입 정책 토글 옆에 지금 봇 차단 상태를 알린다([account-R25](account.md#가입-정책))
- **web-R30** — 처리 현황([admin-R69](admin.md#처리-현황))은 회차 하나를 표의 한 행으로, 받은 순서대로 보인다([ADR-0348](../adr/0348-each-round-leaves-a-round-record-in-platform-and-the-operator-screen-shows-backlog-and-recent-throughput-per-round.md)).
  - 칸: 회차 · 대기 · 미룸 · 최근 1시간 처리 · 최근 1시간 회차 · 마지막 회차(끝난 시각 · 끝난 까닭). 미룸 머리 칸에 "가져올 시각이 아직 오지 않은 대기(일시 실패 · 정착 시간)" 설명을 단다
  - 회차 이름: 기사 표현 · 사건 배정 · 사건 재판정 · 사건 병합 · 흐름 잇기 · 흐름 관계 · 태그 평가. 모르는 이름은 이름 그대로 보인다
  - 끝난 까닭: 가져올 대기 없음 · 시간을 다 씀 · 일시 실패로 쉼 · 멈춤 · 오류. 회차 기록이 없으면 "기록 없음"
  - 화면을 연 동안 30초마다 다시 읽는다. 표 아래에 "대기가 밀리면 회차가 쉬지 않고 처리합니다. 같은 일의 Jev 비용은 같고 시간당 지출이 커집니다." 를 단다

## 6. 데이터

해당 없음 — 화면은 브라우저 저장소(`localStorage` · `sessionStorage`)를 쓰지 않는다.

## 7. 설정

해당 없음 — 화면은 빌드 · 실행 환경 변수를 읽지 않는다. 화면용 API 는 같은 오리진의 `/api` 로 부른다([ADR-0005](../adr/0005-frontend-react-spa.md)).

## 8. 결정

| ADR | 한 줄 |
|---|---|
| [0005](../adr/0005-frontend-react-spa.md) | 프론트엔드는 React SPA 이고 단일 오리진으로 제공한다 |
| [0020](../adr/0020-screen-api-contract-from-committed-openapi-spec.md) | 화면용 API 계약은 커밋된 OpenAPI 명세가 정본이고 타입은 그 명세에서 만든다 |
| [0044](../adr/0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md) | 판정 결과는 판별자 하나의 200, 요청의 문제는 RFC 9457 오류 레코드 |
| [0068](../adr/0068-rss-summaries-shown-only-on-operator-screens-and-users-get-processed-information.md) | RSS 요약문은 운영자 화면에만 보인다 |
| [0141](../adr/0141-invitation-links-are-seven-day-single-use-links-not-bound-to-an-email-with-the-token-in-the-fragment-stored-only-as-a-digest.md) | 초대 링크의 토큰은 링크의 조각에 담는다 |
| [0192](../adr/0192-reader-screens-are-composed-by-the-reader-module-over-module-public-queries.md) | 독자 화면의 응답은 `reader` 가 엮는다 |
| [0233](../adr/0233-digest-failure-notices-come-through-an-event-declared-by-the-receiving-user-module-for-every-failed-digest.md) | 다이제스트 실패 알림은 실패한 다이제스트마다 남는다 |
| [0237](../adr/0237-the-digest-home-shows-the-latest-date-and-every-date-has-its-own-page-and-a-list.md) | 다이제스트 홈 · 날짜 화면 · 날짜 목록 |
| [0238](../adr/0238-a-digest-date-shows-the-visible-digest-with-the-latest-attempt-and-the-list-holds-every-date-with-a-state.md) | 날짜 보기는 보이는 다이제스트와 가장 최근 시도를 함께 보인다 |
| [0239](../adr/0239-digest-screens-show-tokens-of-the-visible-digest-and-the-sum-of-every-attempt-of-the-date-without-prices.md) | 다이제스트 화면의 토큰은 금액으로 바꾸지 않는다 |
| [0240](../adr/0240-digest-failures-show-the-cause-action-and-the-code-and-never-the-failure-message.md) | 다이제스트 실패는 할 일과 무슨 일로 보이고 실패 메시지는 내지 않는다 |
| [0241](../adr/0241-selection-reasons-mark-only-citations-outside-subscribed-topics-named-interest-and-big-topic.md) | 선정 이유의 화면 이름은 구독 주제 · 관심 반영 · 큰 화제다 |
| [0242](../adr/0242-the-storyline-subscription-list-lives-on-the-key-gated-storyline-screen.md) | 흐름 추적 목록은 키가 있어야 가는 화면에 둔다 |
| [0243](../adr/0243-the-subscription-history-on-the-subscription-screen-shows-only-the-users-own-subscribe-and-unsubscribe.md) | 구독 이력은 사용자가 직접 한 구독 · 해제만 보인다 |
| [0249](../adr/0249-topic-suggestions-show-as-a-card-on-the-digest-home-and-accepting-uses-the-tag-preference-save.md) | 구독 제안은 다이제스트 홈의 카드이고 수락은 태그 선호 저장이다 |
| [0262](../adr/0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md) | 다이제스트 항목의 배지는 하나다 |
| [0265](../adr/0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md) | 반응은 항목에 한 번 누르고 화면은 항목에 누른 값을 보인다 |
| [0284](../adr/0284-a-digest-item-keeps-the-top-that-grouped-it-on-its-section-and-items-with-a-flow-carry-a-flow-tracking-button.md) | 스토리라인이 있는 항목에 흐름 추적 버튼을 둔다 |
| [0290](../adr/0290-the-readers-storyline-tab-shows-the-users-summarized-flows-and-the-tracking-list-has-one-row-per-user-storyline.md) | 사건 흐름 탭은 정리된 사용자 스토리라인을 보인다 |
| [0291](../adr/0291-tokens-spent-on-a-summary-are-shown-on-the-personal-flow-screen-as-the-last-summary-and-the-running-total.md) | 정리에 쓴 토큰은 개인 흐름 화면에 보인다 |
| [0292](../adr/0292-reader-screens-call-a-storyline-an-event-flow.md) | 독자 화면은 스토리라인을 "사건 흐름"으로 부른다 |
| [0293](../adr/0293-the-interest-history-also-shows-the-interest-of-summarized-flows.md) | 관심사 히스토리는 정리된 사용자 스토리라인의 관심도 보인다 |
| [0301](../adr/0301-the-operator-article-detail-is-one-page-and-one-detail-api-and-article-titles-on-operator-screens-link-to-it.md) | 운영자 기사 상세는 한 페이지다 |
| [0307](../adr/0307-the-tag-set-screen-edits-the-whole-text-activates-only-what-was-reviewed-and-reports-interference-by-comparing-results.md) | 태그 체계 화면은 원문 전체를 고치는 한 페이지다 |
| [0319](../adr/0319-the-change-report-screen-has-a-list-per-kind-and-a-report-page-with-four-states-and-the-failure-cause-only.md) | 변화 리포트 화면은 종류마다 목록과 한 편 화면이다 |
| [0320](../adr/0320-operator-screens-operations-docs-and-the-evaluation-tool-also-call-a-storyline-an-event-flow-and-a-big-one-a-big-topic.md) | 운영자 화면도 사건 흐름 · 흐름 · 큰 화제로 부른다 |
| [0325](../adr/0325-a-failed-report-is-regenerated-from-the-screen-on-the-same-row-up-to-three-times.md) | 실패한 리포트는 화면에서 세 번까지 다시 만든다 |
| [0328](../adr/0328-the-frontend-has-one-screen-spec-web-holding-screen-names-routes-and-screen-behavior.md) | 프론트엔드는 화면 Spec 하나를 갖고 화면 이름 · 경로 · 동작을 담는다 |
| [0337](../adr/0337-the-change-report-screen-shows-no-numbers-in-text-interest-as-history-cells-and-judgements-as-coloured-words.md) | 변화 리포트 화면은 표의 수를 글자로 보이지 않고, 관심은 색 칸, 판정 · 상태 · 변화는 색 낱말로 보인다 |
| [0338](../adr/0338-a-change-report-writes-only-kind-sections-without-an-overview.md) | 변화 리포트는 개요 없이 종류 섹션만 쓴다 |
| [0339](../adr/0339-change-report-kinds-are-tracked-flows-and-interest-changes-and-it-leaves-out-subscribed-topic-coverage.md) | 변화 리포트는 구독 주제 보도량을 다루지 않는다 |
| [0341](../adr/0341-a-monthly-report-does-not-compare-flow-coverage-with-the-previous-monthly-and-shows-the-flow-state-kept-at-generation.md) | 월간 리포트는 흐름 보도량을 견주지 않고 생성 때의 흐름 상태를 보인다 |
| [0348](../adr/0348-each-round-leaves-a-round-record-in-platform-and-the-operator-screen-shows-backlog-and-recent-throughput-per-round.md) | 운영자 화면 처리 현황이 회차마다 밀린 수와 최근 1시간 처리량을 보인다 |
