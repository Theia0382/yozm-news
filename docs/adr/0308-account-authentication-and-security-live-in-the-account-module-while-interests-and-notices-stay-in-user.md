# ADR-0308: 계정 · 인증 · 보안은 `account` 모듈이 갖고, 관심 · 화면 내 알림은 `user` 에 남는다

- 상태: 승인
- 날짜: 2026-11-24
- 근거: [이슈 #250](https://github.com/Theia0382/yozm-news/issues/250) — 기점 `77769fb` 의 `user` main 파일 367개를 계정 쪽 · 관심 쪽 · 화면 내 알림으로 나눠 묶음 사이의 직접 참조(17곳) · `users` 를 읽는 SQL · `users` FK 를 전수 대조
- 관련: [ADR-0124](0124-security-configuration-lives-in-the-user-module-and-platform-keeps-only-the-screen-api-path-convention.md)(보안 구성 · 운영자 토큰 체인의 모듈 — 부분 대체), [ADR-0140](0140-sign-up-policy-is-a-user-module-operator-setting-with-append-only-history-and-starts-closed.md)(가입 정책의 모듈 — 부분 대체), [ADR-0156](0156-user-id-is-a-platform-public-type.md)(세션 사용자 인자의 모듈 — 부분 대체), [ADR-0169](0169-request-limits-and-bot-checks-are-platform-marks-applied-by-the-user-session-chain-before-the-handler.md)(세션 체인의 모듈 — 부분 대체), [ADR-0170](0170-request-limits-count-fixed-windows-in-a-user-postgresql-table-with-a-code-constant-policy-table.md)(요청 제한 표의 모듈 — 부분 대체), [ADR-0134](0134-in-app-notices-are-one-row-per-recipient-with-a-closed-kind-and-per-kind-jsonb-content-and-read-notices-are-deleted-after-ninety-days.md) · [ADR-0247](0247-topic-suggestion-declines-are-kept-by-user-with-the-recorded-version-and-digest-answers-the-interest-at-decline-through-an-interface-declared-by-user.md)(계정을 지우면 함께 지워진다 — 부분 대체), [ADR-0228](0228-consumer-queries-scoped-by-a-user-argument-may-be-called-on-screen-paths-with-the-session-user.md)(화면 경로에서 부르는 조회의 목록 — 부분 대체), [ADR-0309](0309-account-facts-are-published-after-commit-and-user-writes-interest-defaults-and-password-notices-from-them.md)(계정의 사실을 알리는 길)

## 맥락

- `user` 는 계정 · 자격 · 세션 · 보안 구성(체인 셋 · 운영자 토큰 · CSRF) · 키 관문 · 요청 방어 · 계정을 만드는 길과, 관심 설정 · 사용자 스토리라인 · 흐름 추적 · 흐름의 정리 · 구독 제안 거절 · 화면 내 알림을 함께 갖는다. 백엔드에서 가장 큰 모듈이고, 두 묶음은 바뀌는 까닭과 읽는 쪽이 다르다 — 계정 쪽은 `admin` 이 운영 API 로, 관심 쪽은 `digest` · `reader` · `report` 가 공개 조회로 읽는다.
- 계정 쪽(main 145 파일)은 다른 모듈 가운데 `platform` 과 `credential` 의 키 유무만 읽는다. 관심 쪽(177)은 `tag` · `storyline` · `llm` · `feedback` 등을 읽는다.
- 두 묶음 사이의 직접 참조는 17곳이다. 계정 쪽이 관심 · 알림 쪽을 부르는 것은 계정을 만들 때의 관심 설정 기본값 쓰기와 비밀번호 알림 쓰기뿐이고, 나머지는 관심 · 알림 쪽이 계정의 있음 · 상태 · 운영자 목록을 읽는 것이다(`AccountRepository` · SQL 의 `users`).
- 관심 · 알림 표 10개가 `users` 에 FK(`ON DELETE CASCADE`)를 건다. 다른 모듈의 `user_id` 는 FK 없이 1 이상 검사만 두고, 데이터 모델은 모듈 사이 참조에 FK 를 두지 않는다. 계정을 지우는 코드 경로는 없다.
- 세션에는 세션 사용자 인증(`AccountAuthentication`)이 Java 직렬화로 담긴다. 클래스의 패키지가 바뀌면 배포 전 세션은 읽지 못한다. 릴리스는 아직 없다.

## 결정

- **새 모듈 `account` 가 계정과 그 인증을 갖는다** — 계정 · 자격 · 복구 코드 · 세션 · 보안 구성 · 운영자 접근 통제 · 키 관문 · 세션 사용자 범위 · 자체 로그인 · 요청 방어 · 계정을 만드는 길 · 가입 정책 · 초대와 그 운영 API. 앱의 보안 구성 전체가 이 모듈에 있다. 의존은 `platform` · `credential` 뿐이다.
- **`user` 는 이름 그대로 남아 사용자 한 사람의 관심과 그 사람에게 보이는 기록을 갖는다** — 관심 설정 · 사용자 스토리라인 · 흐름 추적 · 흐름의 정리 · 구독 제안 거절 · 화면 내 알림. 화면 내 알림은 따로 떼지 않는다 — 알림함은 사용자 개인에게 보이는 공간이고, 알림 종류 대부분이 이벤트로 들어온다.
- **의존 방향은 `user` → `account` 다.** `account` 는 `user` 를 모른다. `admin` 은 `account` 의 운영 API 만 읽는다.
- **다른 모듈은 계정을 `account` 의 공개 조회 `AccountDirectory` 로 읽는다** — 있음 · 쓰는 계정은 사용자를 인자로 받아 화면 경로에서 세션 사용자로 부를 수 있고, 쓰는 운영자 목록 · 계정 쪽은 범위가 없어 백그라운드 작업 · 리스너만 부른다. 다른 모듈이 `users` 를 SQL 로 읽지 않는다.
- **모듈 사이 FK 를 두지 않는다** — `user` 의 관심 · 알림 표 10개의 `users` FK 를 지우고 다른 모듈처럼 `user_id` 에 1 이상 검사를 둔다. `account` 안의 FK(자격 · 복구 코드 · 초대의 가입한 계정)는 둔다. 표 이름은 그대로다.
- **업그레이드 때 세션은 한 번 끊긴다** — `AccountAuthentication` 의 클래스 이름이 바뀌어 배포 전 세션은 이미 있는 규칙대로(읽지 못한 세션은 지우고 익명) 지워진다. 옛 이름을 이어 읽는 호환 코드를 두지 않는다.

## 결과

- 계정 · 보안의 변경은 `account` 와 그 운영 API 를 읽는 `admin` 에만 닿고, 관심 · 다이제스트 쪽 변경은 `account` 에 닿지 않는다.
- 다이제스트 일정 목록 · 기존 계정 채우기 · 피드 상태 알림의 받는 사람 · 다이제스트 실패 알림의 계정 확인은 `AccountDirectory` 로 읽는다. 일정 목록은 쓰는 계정 쪽을 기준으로 읽고, 설정 행이 없는 계정은 기본 일정으로 낸다.
- 계정을 지우는 길이 생기면 다른 모듈은 FK 의 연쇄 삭제가 아니라 계정 쪽이 알리는 사실로 따른다.
- 업그레이드한 인스턴스의 사용자는 한 번 다시 로그인한다.
- 옮긴 규칙은 `account` Spec 에서 새 번호를 받고, `user` Spec 에는 옛 번호가 폐지 표시로 남아 새 번호를 가리킨다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 새 모듈 이름 `auth` | 계정 데이터 · 가입 정책 · 초대처럼 인증이 아닌 것을 갖고, 한국어 개발 이름과 짝이 없다 |
| 남는 `user` 를 `interest` 로 바꾼다 | 남는 규칙의 식별자 · 참조까지 바뀌고, 흐름 추적 · 정리 · 알림을 "관심"이 덮지 못하며, 표 이름 `user_*` 와 어긋난다 |
| 화면 내 알림을 새 모듈 `notice` 로 뗀다 | 알림마다 바뀌는 까닭이 나뉘지만, 이 분리의 목적은 계정 · 보안 덩어리를 떼는 것이고 알림함은 사용자 개인 공간으로 `user` 에 들어맞는다. 리스너 · 이벤트 클래스 이동의 마이그레이션도 생긴다 |
| 화면 내 알림을 `account` 로 | 관심 쪽 알림 내용(선호 조정 · 사라진 구독)과 다이제스트 실패 이벤트를 계정 모듈이 갖게 되어 떼려는 까닭이 다시 섞인다 |
| 표 · FK 를 그대로 둔다 | 저장소에서 유일한 모듈 사이 FK 가 되고, 계정을 지우는 길이 생길 때 다른 모듈의 행이 DB 에서 조용히 지워진다 |
| 계정 쪽 표 이름도 `account_*` 로 바꾼다 | 이름 바꾸기 마이그레이션과 SQL · 문서의 흔들림이 크고, 저장소에 모듈 접두사 규칙이 없다 |
| 옛 세션 클래스 이름을 새 이름으로 잇는 역직렬화 변환기 | 보안 구성에 영구한 호환 코드가 생기고, 릴리스 전이라 지킬 세션이 사실상 없다 |
