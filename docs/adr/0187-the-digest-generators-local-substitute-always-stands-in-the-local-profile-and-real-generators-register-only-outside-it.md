# ADR-0187: 다이제스트 생성기의 로컬 대체물은 `local` 에서 언제나 서고, 실제 생성기는 `local` 밖에서만 등록된다

- 상태: 승인
- 날짜: 2026-11-16
- 근거: [이슈 #8](https://github.com/Theia0382/yozm-news/issues/8) — 다이제스트 생성기 포트와 로컬 대체물을 설계. 구현 `8895f31`(대체물) · `6d0301a`(결선)
- 관련: [ADR-0012](0012-swappable-external-dependencies.md)(부분 대체 — 결정 절 "`local`"의 둘째 문장 가운데 LLM 부분, 보충 — 의존성 표 LLM 행의 고정 응답 생성기), [ADR-0155](0155-llm-local-substitute-takes-only-keys-with-a-fixed-prefix-in-the-local-profile.md)(보충 — 결과 절의 실제 키로 확인하는 길), [ADR-0057](0057-local-substitutes-registered-only-in-local-profile-without-operator-switch.md)(등록 규약을 따름)

## 맥락

- 개발 환경은 외부 계정 · 비밀 값 없이 전체 흐름을 돌릴 수 있어야 한다. LLM 의 로컬 대체물로 고정 응답 다이제스트 생성기를 두기로 했다([ADR-0012](0012-swappable-external-dependencies.md)). 로컬 대체물은 profile `local` 에서만 등록하고 켜는 운영 설정이 없다([ADR-0057](0057-local-substitutes-registered-only-in-local-profile-without-operator-switch.md)).
- `llm` 에도 로컬 대체물이 있다 — `local` 에서 정해진 접두의 가짜 키만 받고, 그 밖의 키는 실제 공급자로 보낸다([ADR-0155](0155-llm-local-substitute-takes-only-keys-with-a-fixed-prefix-in-the-local-profile.md)). 생성 호출의 가짜 키 대체물도 `llm` 에 더해진다. ADR-0155 는 `llm` 에서 "로컬 개발에서는 언제나 대체물"을 실제 공급자를 확인할 수 없다는 이유로 기각했다.
- `llm` 의 대체물은 다이제스트를 모른다 — 프롬프트 안의 후보 별칭을 읽어 인용할 수 없다. 별칭을 인용하는 고정 응답은 후보를 아는 다이제스트 생성기만 만들 수 있다.
- 평가기 · 임베더의 대체물은 운영자 설정(키 · provider)을 조건으로 선다. LLM 키는 운영자가 아니라 사용자가 넣는 값이라 "키가 없을 때"로 가를 수 없다.

## 결정

- **다이제스트 생성기의 로컬 대체물은 profile `local` 에서 조건 없이 생성기로 등록한다.** 사용자의 키가 실제 키든 `llm` 의 가짜 키든 `local` 의 다이제스트는 언제나 대체물이 만든다.
- **실제 생성기(사용자 키로 `llm` 의 호출 API 를 부르는 생성기)는 `local` 밖에서만 등록한다.**
- 대체물의 출력은 실제 생성기의 출력과 같은 읽기 · 검증을 지난다.

규칙은 [digest Spec](../specs/digest.md#로컬-대체물) digest-R12 에 있다.

## 결과

- 화면 개발과 `local` 을 켠 확인용 인스턴스에서 언제나 인용이 있는 다이제스트를 얻는다. 결선은 `@LocalDevelopment` 하나로 끝난다.
- `local` 에서는 실제 키 · 가짜 키로 다이제스트를 만들어 볼 수 없다 — 실제 생성은 시험(외부 HTTP 대체)과 `local` 없는 확인용 인스턴스로 본다. 키 등록 · 검증의 실제 공급자 확인은 ADR-0155 대로 `local` 에서도 된다.
- `llm` 의 생성 호출 가짜 키 대체물은 `local` 의 다이제스트 흐름에서 쓰이지 않는다.
- 실제 생성기가 생기기 전에는 `local` 밖에 생성기가 없다 — 생성할 때 찾고, 없으면 그 다이제스트를 실패로 끝낸다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `local` 에서 요청마다 고름 — 키가 가짜면 대체물, 실제면 실제 생성기 | 다이제스트가 키가 가짜인지 알아야 한다 — 가짜 키의 접두는 `llm` 어댑터 안의 상수라, 공개하면 로컬 대체물의 지식이 모듈 경계를 넘는다. 생성기 고르기가 요청 시점 분기가 된다 |
| 다이제스트에 대체물을 두지 않고 `llm` 의 가짜 키 대체물 하나만 | `llm` 대체물이 별칭을 인용할 수 없어 `local` 의 다이제스트가 비거나 실패한다. 실제 생성기가 생기기 전에는 생성 흐름을 돌릴 수 없다 |

## 후속 기록

- 2026-11-18: 결과 "실제 생성기가 생기기 전에는 `local` 밖에 생성기가 없다 — 생성할 때 찾고, 없으면 그 다이제스트를 실패로 끝낸다" — 실제 생성기가 `local` 밖에서 언제나 등록되어 그 갈래를 걷었다([ADR-0221](0221-generator-results-are-success-or-failure-and-the-use-case-asks-once-more-only-for-a-complete-unreadable-answer.md)), 지금 상태는 [digest Spec "생성 과정"](../specs/digest.md#생성-과정) digest-R8
- 2026-11-22: 결정(대체물은 `local` 에서 언제나, 실제 생성기는 `local` 밖에서만)과 결과 "실제 생성기가 생기기 전에는 `local` 밖에 생성기가 없다 — 생성할 때 찾고, 없으면 그 다이제스트를 실패로 끝낸다"를 변화 리포트 생성기로 넓힘 — [report Spec "로컬 대체물"](../specs/report.md#로컬-대체물) report-R10 · ["생성 과정"](../specs/report.md#생성-과정) report-R13
