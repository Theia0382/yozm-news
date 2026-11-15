# ADR-0166: `CredentialInvalidated` 는 `credential` 이 키가 무효가 되는 전이마다 내고, 키 무효 알림은 전이마다 하나다

- 상태: 승인
- 날짜: 2026-11-14
- 근거: [이슈 #150](https://github.com/Theia0382/yozm-news/issues/150)
- 관련: [ADR-0165](0165-a-key-is-valid-or-invalid-and-moves-by-validation-results.md)(상태 전이), [ADR-0134](0134-in-app-notices-are-one-row-per-recipient-with-a-closed-kind-and-per-kind-jsonb-content-and-read-notices-are-deleted-after-ninety-days.md)(알림의 저장 꼴 — 종류를 하나 더한다), [ADR-0156](0156-user-id-is-a-platform-public-type.md)

## 맥락

- 키가 무효가 되면 사용자에게 알려야 한다. v1 에는 메일이 없어 화면 안 알림으로 알린다.
- 키가 무효임을 알게 되는 계기는 여럿이다 — 다시 검증, 뒤에 생기는 생성 호출.
- 이벤트 리스너는 같은 발행을 두 번 받을 수 있다.

## 결정

- **`credential` 이 키의 상태를 `VALID` 에서 `INVALID` 로 바꾸는 트랜잭션에서 `CredentialInvalidated` 를 한 번 낸다.** 계기와 무관하고, 이미 무효인 키의 거듭된 실패에는 내지 않는다.
- **내용은 사용자 · 공급자 · 키 ID · 무효 시각이다.** 키 ID 와 무효 시각의 짝이 전이 하나를 가리킨다. 키와 힌트는 담지 않는다.
- **`user` 가 이벤트를 받아 키 무효 알림을 전이마다 하나 남긴다.** 모으지 않고, 같은 전이의 발행을 거듭 받아도 하나다.

계약은 [credential Spec](../specs/credential.md#credentialinvalidated), 알림의 규칙은 [user Spec](../specs/user.md#화면-내-알림) user-R36 에 있다.

## 결과

- 사용자가 화면에서 다시 검증해 무효를 본 경우에도 알림이 남는다 — 규칙이 계기를 가르지 않는다.
- 무효와 복귀를 되풀이하는 키는 무효가 될 때마다 알림이 는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 실패할 때마다 낸다 | 무효 키로 되풀이되는 호출마다 알림이 쌓인다 |
| 사용자가 직접 한 다시 검증에는 내지 않는다 | 계기마다 규칙이 갈리고, 저장한 키의 길이 누가 불렀는지를 알아야 한다 |
| 안 읽은 알림 하나에 모은다 | 공급자마다 · 전이마다 다른 일이고 드물다 |
