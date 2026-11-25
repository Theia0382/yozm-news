# ADR-0233: 다이제스트 실패 알림은 받는 쪽 user 가 선언한 이벤트로 받고, 실패한 다이제스트마다 까닭 갈래와 함께 남긴다

- 상태: 승인
- 날짜: 2026-11-18
- 근거: [이슈 #178](https://github.com/Theia0382/yozm-news/issues/178) — 수용 기준 "다이제스트 실패 알림 — `user` 의 화면 내 알림에 종류를 더한다". 구현 커밋 `60b5eed`(이벤트 · 알림 종류 · 화면) · `d4381dc`(끝내기 정본의 발행)
- 관련: [ADR-0134](0134-in-app-notices-are-one-row-per-recipient-with-a-closed-kind-and-per-kind-jsonb-content-and-read-notices-are-deleted-after-ninety-days.md)(관련 — 알림 저장 꼴에 종류 하나), [ADR-0196](0196-feedback-is-accepted-only-on-articles-cited-by-the-users-digest-and-digest-answers-through-an-interface-declared-by-feedback.md) · [ADR-0157](0157-credential-depends-on-llm-and-stored-keys-reach-llm-through-a-supply-interface-that-llm-declares-and-credential-implements.md)(관련 — 받는 쪽이 선언한 인터페이스의 꼴)

## 맥락

- 다른 모듈의 일로 생기는 알림은 발행 모듈이 자기 이벤트를 선언하고 `user` 리스너가 받는 꼴이다.
- `digest` 는 이미 `user` 의 공개 조회를 읽어 `user` 에 기댄다 — `digest` 가 선언한 이벤트를 `user` 가 받으면 두 모듈이 서로 기댄다.
- 실패 코드는 열둘이고 사용자의 할 일(키 · 공급자 계정 · 잠시 뒤 · 모델 출력)로 묶인다.

## 결정

- **실패 이벤트(`DigestFailed`)와 까닭 갈래(`DigestFailureCause` — 넷)는 받는 쪽 `user` 가 선언하고, `digest` 가 실패를 쓰는 트랜잭션에서 낸다** — 의존은 이미 있는 `digest → user` 그대로이고, 받는 쪽은 다른 알림과 같은 리스너(새 트랜잭션 · 발행 기록의 재처리)다.
- **실패 코드를 갈래로 옮기는 정본은 `digest` 에 있다(`FailureCauses` — 망라 분기)** — `user` 는 화면 문구의 갈래만 갖는다.
- **실패한 다이제스트마다 알림 하나를 남긴다(계기 무관)** — 같은 다이제스트를 거듭 받아도 하나(부분 고유 인덱스), 계정이 없으면 남기지 않는다. 끝낸 행 저장의 정본(`DigestEndings`) 하나가 실패를 쓰는 모든 길(생성 · 시작 전 끝내기 · 거두기)에서 낸다.
- 알림은 날짜 · 갈래 문구 · 시각을 보이고, 키 갈래면 LLM 키 화면으로 잇는다. 다이제스트 ID 는 화면에 내지 않는다.

## 결과

- 생성 중 키가 무효가 되면 키 무효 알림과 다이제스트 실패 알림(키 갈래)이 함께 남는다 — 앞은 키를, 뒤는 그날 다이제스트를 말한다.
- 알림 쓰기가 실패해도 실패 상태의 커밋은 되돌아가지 않고 발행 기록이 다시 처리한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `user` 의 공개 쓰기 API 를 실패를 쓰는 트랜잭션에서 부른다 | 알림 쓰기가 실패하면 실패 상태도 되돌아가 다이제스트가 끝나지 않는다 |
| `digest` 이벤트 + `digest` 리스너가 `user` 쓰기 API 를 부른다 | 같은 결과에 쓰기 API 와 리스너가 더해진다 |
| 이벤트 타입을 `platform` 에 둔다 | 횡단 모듈이 다이제스트라는 업무 지식을 갖는다 |
| 일정 다이제스트의 실패만 알린다 | 수동 생성 뒤 자리를 떠난 사용자가 결과를 모른다 |
| 알림에 날짜만 · 실패 코드 문자열 | 할 일을 알 수 없다 · 명세에 값 목록이 없어 화면 분기가 닫히지 않는다 |

## 후속 기록

- 2026-11-18: 결정 "알림은 날짜 · 갈래 문구 · 시각을 보이고, 키 갈래면 LLM 키 화면으로 잇는다" — 알림이 잇는 다이제스트 화면 · 홈과 다이제스트 화면과 함께 쓰는 갈래 문구([ADR-0240](0240-digest-failures-show-the-cause-action-and-the-code-and-never-the-failure-message.md)), 지금 상태는 [user Spec "화면 내 알림"](../specs/user.md#화면-내-알림) user-R62
- 2026-11-25: 결정 "까닭 갈래(`DigestFailureCause` — 넷)" — 리포트 실패 알림과 함께 쓰려고 갈래 타입 이름을 `GenerationFailureCause` 로 바꿨다(값 넷 그대로 — [ADR-0324](0324-report-failures-become-notices-through-an-event-declared-by-the-receiving-user-module-like-digest-failures.md)), 지금 상태는 [user Spec "DigestFailed"](../specs/user.md#digestfailed)
