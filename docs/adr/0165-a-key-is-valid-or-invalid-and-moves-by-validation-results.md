# ADR-0165: 키의 상태는 `VALID` · `INVALID` 둘이고 검증 결과로 전이한다

- 상태: 승인
- 날짜: 2026-11-14
- 근거: [이슈 #150](https://github.com/Theia0382/yozm-news/issues/150) — 키 상태의 초안(`VALID` · `INVALID` · `UNVERIFIED`)과 등록 규칙(검증 성공 때만 저장)을 대조
- 관련: [ADR-0154](0154-key-validation-lists-models-and-failure-kinds-are-sorted-by-one-rule-per-provider-reading-the-error-kind.md)(실패 종류), [ADR-0159](0159-having-a-key-means-having-any-stored-key-regardless-of-its-status.md)(키가 있다는 것의 뜻)

## 맥락

- 등록은 검증이 성공할 때만 저장한다. 저장된 키는 모두 한 번은 검증을 통과했다.
- 저장한 키로 공급자에 물으면 실패 종류가 나온다 — 무효 · 권한 없음 · 한도 소진 · 일시 장애 · 예상 밖.
- 다이제스트 · 리포트의 일정은 키가 없거나 무효인 사용자를 뺀다.

## 결정

- **상태는 `VALID` · `INVALID` 둘이다.** 등록된 키는 `VALID` 로 시작한다.
- **저장한 키로 물은 결과가 성공이면 `VALID`, 무효 · 권한 없음이면 `INVALID` 이고, 한도 소진 · 일시 장애 · 예상 밖이면 상태를 바꾸지 않는다.** 무효였던 키가 성공하면 `VALID` 로 돌아온다.
- 결과는 물은 그 키에만 쓴다 — 그 사이 지워졌거나 교체된 키의 결과는 버린다.

규칙은 [credential Spec](../specs/credential.md#저장한-키와-상태) credential-R14 에 있다.

## 결과

- 일시 장애나 한도 소진이 키를 무효로 만들지 않는다 — 사용자의 다이제스트가 그 때문에 멈추지 않는다.
- 검증 전 상태가 없어, 저장된 키는 언제나 쓸 수 있거나 무효다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `UNVERIFIED` 를 둔다 | 만드는 경로가 없다. 일시 실패한 키를 이 상태로 내리면 일시 장애가 일정에서 사용자를 뺀다 |
| 모든 실패를 `INVALID` 로 | 공급자의 일시 장애가 모든 사용자의 키를 무효로 만든다 |
