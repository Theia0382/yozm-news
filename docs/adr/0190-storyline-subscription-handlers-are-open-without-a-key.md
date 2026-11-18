# ADR-0190: 스토리라인 구독 처리기는 키 없이 연다

- 상태: 승인
- 날짜: 2026-11-16
- 근거: [이슈 #184](https://github.com/Theia0382/yozm-news/issues/184) — 구현 커밋 `a7ed33a`
- 관련: [ADR-0160](0160-users-without-a-key-may-use-key-account-and-notice-handlers.md)(부분 대체 — 결정 첫 항목(키 없는 사용자에게 여는 처리기)의 열거를 넓힌다. 결과 절의 기준 "그 처리기가 내용을 내는지"를 적용한 것이다), [ADR-0179](0179-interest-settings-handlers-are-open-without-a-key.md)(같은 기준을 관심 설정에 적용한 것), [ADR-0158](0158-requests-of-session-users-without-a-key-are-closed-by-default-and-handlers-open-without-a-key-carry-a-platform-annotation.md)(키 관문)

## 맥락

- 키 없는 사용자에게는 표시한 처리기만 연다. 새 처리기를 키 없이 열지는 그 처리기가 내용(다이제스트 · 헤드라인)을 내는지로 가른다.
- 스토리라인 구독의 처리기(내 구독 · 구독 · 해제)는 스토리라인 ID · 구독 시각 · 지금 상태만 내고 기사 내용을 내지 않는다. 스토리라인의 내용(목록 · 타임라인)은 다른 화면이 낸다.
- 내 구독 목록을 관심 설정 화면에 둘지 스토리라인 화면에 둘지는 구독 화면을 만들 때 정한다 — 관심 설정 화면은 키 없이 연다.

## 결정

- **스토리라인 구독 처리기를 키 없이 연다.**

## 결과

- 키를 모두 지운 사용자도 구독을 보고 해제할 수 있다.
- 구독 화면을 관심 설정 화면에 두어도 키 관문에 막히지 않는다.
- 스토리라인의 내용을 내는 처리기는 그대로 닫힌다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 구독 처리기를 키 관문 뒤에 둔다 | 내용을 내지 않는 처리기를 닫아 ADR-0160 의 기준과 어긋난다 |

## 후속 기록

- 2026-11-18: 맥락 "내 구독 목록을 관심 설정 화면에 둘지 스토리라인 화면에 둘지는 구독 화면을 만들 때 정한다" — [ADR-0242](0242-the-storyline-subscription-list-lives-on-the-key-gated-storyline-screen.md)(키가 있어야 가는 스토리라인 화면에 둔다 — 결과 "키를 모두 지운 사용자도 구독을 보고 해제할 수 있다"는 처리기 수준의 결과로 남는다)
- 2026-11-18: 결정 "스토리라인 구독 처리기를 키 없이 연다"의 처리기에 구독 이력 처리기가 든다 — [ADR-0244](0244-the-storyline-subscription-history-handler-is-opened-by-user-without-a-key-and-reads-recent-pages-in-the-module.md), 지금 상태는 [user Spec "스토리라인 구독"](../specs/user.md#스토리라인-구독) user-R63
