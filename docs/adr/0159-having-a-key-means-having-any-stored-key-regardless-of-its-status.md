# ADR-0159: 키 관문과 화면 이동에서 키가 있다는 것은 저장한 키가 하나라도 있다는 것이다(상태 무관)

- 상태: 승인
- 날짜: 2026-11-14
- 근거: [이슈 #150](https://github.com/Theia0382/yozm-news/issues/150)
- 관련: [ADR-0158](0158-requests-of-session-users-without-a-key-are-closed-by-default-and-handlers-open-without-a-key-carry-a-platform-annotation.md)(키 관문), [ADR-0165](0165-a-key-is-valid-or-invalid-and-moves-by-validation-results.md)(키의 상태)

## 맥락

- 키는 공급자 쪽의 폐기 · 권한 변경으로 무효가 될 수 있다. 일시적인 조직 설정 문제로도 인증 실패가 난다.
- 다이제스트 · 리포트의 일정은 키가 없거나 무효인 사용자를 뺀다.

## 결정

- **키 관문과 화면 이동의 기준은 "저장한 키가 하나라도 있다"이다.** 공급자 · 상태를 보지 않는다.
- 무효가 된 키는 알림 · 키 화면의 상태 · 생성 일정에서 다룬다.

규칙은 [credential Spec](../specs/credential.md#저장한-키와-상태) credential-R18 에 있다.

## 결과

- 키가 무효가 돼도 사용자는 지난 다이제스트와 설정을 그대로 쓴다. 키를 지워 하나도 없으면 다시 키 화면만 남는다.
- 무효 키만 가진 사용자는 화면을 쓰지만 새 다이제스트는 받지 못한다 — 알림과 키 화면이 알린다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `VALID` 인 키가 하나라도 있다 | 키가 무효가 되는 순간 지난 다이제스트도 못 본다 |
| 사용자가 고른 공급자의 키가 있다 | 공급자 선택이 없는 사용자에게 기준이 서지 않는다 |
