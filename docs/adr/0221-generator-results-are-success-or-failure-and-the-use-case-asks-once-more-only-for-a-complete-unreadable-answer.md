# ADR-0221: 다이제스트 생성기의 결과는 성공 · 실패 갈래이고, 끝까지 쓴 응답을 읽지 못하면 유스케이스가 한 번 다시 묻는다

- 상태: 승인. 부분 대체 — 다시 묻는 조건과 다시 물은 응답을 읽지 못할 때의 처분([ADR-0270](0270-an-item-missing-from-the-output-is-asked-once-more-and-then-kept-as-a-headline-and-the-counts-are-recorded.md))
- 날짜: 2026-11-18
- 근거: [이슈 #177](https://github.com/Theia0382/yozm-news/issues/177) — 수용 기준 "파싱 실패 한 번 재요청 · 그 밖의 실패는 오류 처리 표" · #8 이 넘긴 "다시 묻기 위해 `DigestGeneration.run` 을 나눌지". 구현 커밋 `9e1012a`(결과 갈래 · 판정 · 다시 묻기) · `7094e6d`(실제 생성기 · 결선)
- 관련: [ADR-0187](0187-the-digest-generators-local-substitute-always-stands-in-the-local-profile-and-real-generators-register-only-outside-it.md)(보충 — 결과의 "실제 생성기가 생기기 전에는 …"), [ADR-0200](0200-generation-results-carry-the-stop-reason-and-schema-enforcement-and-failures-use-the-five-key-check-kinds.md)(관련 — 관문 결과의 종료 사유 · 실패 다섯 종류), [ADR-0201](0201-the-gateway-resends-only-transient-generation-failures-and-sets-the-request-deadline-per-call-kind.md)(관련 — 일시 장애의 다시 보내기는 관문)

## 맥락

- 생성기 포트의 결과는 출력 원문과 토큰뿐이었다. 관문은 예상한 실패(키 없음 · 실패 다섯 종류)와 잘림 · 거절을 예외 없이 낸다 — 받을 자리가 없으면 다이제스트가 생성 중에 남는다.
- 생성기는 출력을 파싱하지 않고 모든 구현의 출력이 같은 읽기 · 검증을 지난다는 것이 포트의 계약이다.
- 잘린 응답 · 거절은 과금된 정상 응답이고, 같은 요청을 다시 보내도 같을 가능성이 크다.

## 결정

- **생성기 결과는 성공(본문 · 토큰 · 종료 사유) · 실패(다이제스트 실패 코드 · 문구) 갈래다.** 관문의 실패 종류는 종류마다 다이제스트 실패 코드 하나로 옮기고(`GenerationFailures`), 키 없음은 `KEY_MISSING` 이다. 예외는 예상 밖의 것만이다.
> 부분 대체(다시 묻는 조건 · 다시 물은 응답을 읽지 못할 때의 처분) — [ADR-0270](0270-an-item-missing-from-the-output-is-asked-once-more-and-then-kept-as-a-headline-and-the-counts-are-recorded.md). 지금 상태: [digest Spec](../specs/digest.md#생성-판정과-다시-묻기) digest-R27
- **다시 묻기는 유스케이스가 한다** — 끝까지 쓴 응답을 읽지 못하면 읽기의 진단을 지시에 덧붙여 같은 요청을 한 번 더 보낸다. 다시 물은 응답을 읽지 못하면 실패다. 판단은 `domain` 의 `GenerationVerdict` 다.
- **잘림 · 거절은 다시 묻지 않고 그 사유의 코드로 끝낸다.**
- **토큰은 공급자가 답한 호출의 합이고, 소요 시간은 첫 호출 시작부터 마지막 호출 끝까지다** — 관문 실패 · 키 없음은 토큰 없이 소요 시간만 있다.
- 실제 생성기는 `local` 밖에서 언제나 등록되므로 생성기가 없는 갈래 · 코드(`NO_GENERATOR`)를 두지 않는다.

## 결과

- 사용자에게 보일 안내(키 다시 등록 · 결제 · 나중에)를 실패 코드로 고를 수 있다(#178).
- 끝까지 쓴 응답을 읽지 못하면 사용자 비용이 두 배다 — 다시 묻기를 한 번으로 묶는다.
- 출력 읽기가 저장 트랜잭션 밖으로 나왔다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 생성기 안에서 읽어 보고 다시 묻는다 | "생성기는 파싱하지 않는다" 계약이 깨지고 읽기가 두 번이며, 규칙이 실제 생성기에만 있다 |
| 읽지 못하면 종료 사유와 무관하게 다시 묻는다 | 잘림 · 거절은 다시 물어도 같을 가능성이 커 비용만 두 배다 |
| 인용이 남지 않은 응답도 다시 묻는다 | 인용 검증은 저장 트랜잭션 안(후보 행 잠금 뒤)이라 다시 묻기가 그 밖으로 나오려면 검증을 두 번 해야 한다 |
