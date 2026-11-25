# ADR-0198: 피드백 기록에 세션 사용자 축 요청 제한을 건다

- 상태: 승인. 부분 대체 — 한도의 근거([ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md))
- 날짜: 2026-11-17
- 근거: [이슈 #183](https://github.com/Theia0382/yozm-news/issues/183) — 기점 `156d2a5` 의 요청 제한 정책 표와 표시가 달린 처리기, 신호 이력 테이블의 보존을 대조
- 관련: [ADR-0170](0170-request-limits-count-fixed-windows-in-a-user-postgresql-table-with-a-code-constant-policy-table.md)(보충 — 정책 표에 동작 하나를 더한다), [ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)(신호 이력은 추가 전용이다)

## 맥락

- 피드백 이력은 값이 바뀔 때마다 한 줄이 늘고 사용자가 탈퇴할 때까지 보존한다. 같은 값을 다시 누르는 요청은 이력을 남기지 않지만, 좋아요와 없음을 번갈아 누르면 누를 때마다 한 줄이 는다.
- 받을 수 있는 기사를 다이제스트가 인용한 기사로 좁혀도 기사 수만 묶이고 되풀이는 묶이지 않는다.
- 요청 제한은 처리기의 표시로 걸고 한도는 코드 상수의 정책 표에 있다. 지금 표시가 달린 처리기는 로그인 · 복구 · 가입 · 초대 · 키 검증이다. 관심 설정 저장과 스토리라인 구독에는 표시가 없다.
- 피드백은 다이제스트 화면에서 기사마다 누르는 값이라 화면에서 가장 자주 일어나는 쓰기다.

## 결정

> 부분 대체(둘째 문장의 한도 근거) — [ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md). 지금 상태: [account Spec](../specs/account.md#요청-방어) account-R30
- **누르기 · 되돌리기 처리기에 요청 제한을 건다.** 세션 사용자로 세고, 한도는 다이제스트 하나가 인용한 기사를 하나씩 누르는 사용이 창 하나에 들어가는 값으로 둔다(측정 전 목표값 — 정책 표가 정본).
- **현재 상태 조회에는 걸지 않는다.** 읽기이고 행을 늘리지 않는다.
- **봇 차단은 걸지 않는다.** 세션 사용자의 요청이다.

## 결과

- 한 사용자가 한 창에 늘릴 수 있는 이력 행에 상한이 생긴다.
- 요청은 결과와 무관하게 센다 — 값이 바뀌지 않는 요청과 받을 수 없는 기사의 요청도 센다.
- 한도를 넘으면 화면이 429 를 받는다 — 버튼 화면이 그 응답을 다뤄야 한다.
- 다이제스트 하나가 인용하는 서로 다른 기사는 그 다이제스트의 후보 수를 넘지 않는다 — 출력은 후보의 별칭으로만 인용하고 후보끼리는 기사가 겹치지 않는다. 후보 수의 상한(기본 60)은 후보 선별을 만드는 작업이 코드에 둔다. 그 상한을 한도보다 크게 하거나 사용자 설정으로 열면 한도를 다시 본다.
- 관심 설정 · 스토리라인 구독의 이력에도 같은 성질(번갈아 바꾸면 행이 는다)이 있고 요청 제한은 없다. 그 저장은 설정 화면에서 드물게 일어난다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 표시를 달지 않는다(관심 설정 · 구독과 같은 꼴) | 되풀이 누르기로 보존하는 이력이 한없이 는다 |
| (사용자, 기사)마다 이력 행 수에 상한을 둔다 | 상한에 닿은 뒤의 정상적인 변경을 거부하거나 옛 이력을 지워야 한다 |
| 클라이언트 주소로도 센다 | 세션 사용자가 정해진 요청이라 사용자 축 하나로 묶인다 |

## 후속 기록

- 2026-11-17: 결과 "그 상한을 한도보다 크게 하거나 사용자 설정으로 열면 한도를 다시 본다" — 최대 후보 수를 사용자 설정(직접 입력 상한 150)으로 열었고 한도(300 / 10분)의 창 하나에 든다([ADR-0209](0209-maximum-candidates-and-big-slots-are-user-settings-and-the-big-slot-takes-at-most-half.md)), 지금 상태는 [user Spec "요청 방어"](../specs/user.md#폐지한-규칙)(user-R38) · [user Spec "관심 설정"](../specs/user.md#관심-설정)(user-R56)
- 2026-11-19: 결과 "다이제스트 하나가 인용하는 서로 다른 기사는 그 다이제스트의 후보 수를 넘지 않는다" — 후보가 사건이고 인용은 사건마다 표본(상한 5)이라 서로 다른 인용 기사는 많아야 750 이고, 한도를 750 / 10분으로 두었다([ADR-0263](0263-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md)), 지금 상태는 [user Spec "요청 방어"](../specs/user.md#폐지한-규칙) user-R38
