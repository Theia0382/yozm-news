# ADR-0205: OpenRouter 의 403 은 권한 표시가 있을 때만 키를 무효로 하고, 200 응답 안의 오류는 예상 밖의 답이다

- 상태: 승인
- 날짜: 2026-11-17
- 근거: [이슈 #192](https://github.com/Theia0382/yozm-news/issues/192) — OpenRouter 문서(오류 코드 · Typed error codes · 200 안의 오류), openai-java SDK 가 숫자 `code` 를 읽는 실험, 실측(2026-11-17): 틀린 키의 401 본문에 `metadata` 없음, 모델을 막는 가드레일은 403 이 아니라 404(라우팅 단계 실패 · `error_type` 없음 · 과금 없음). 리드 결정(403 분류). 구현 `2669322`
- 관련: [ADR-0154](0154-key-validation-lists-models-and-failure-kinds-are-sorted-by-one-rule-per-provider-reading-the-error-kind.md)(보충 — 분류 입력 "오류의 종류"를 OpenRouter 에서는 `error.metadata.error_type` 에서 읽는다), [ADR-0165](0165-a-key-is-valid-or-invalid-and-moves-by-validation-results.md)(무효 · 권한 없음이면 키가 무효로 바뀐다 — 그대로), [ADR-0201](0201-the-gateway-resends-only-transient-generation-failures-and-sets-the-request-deadline-per-call-kind.md)(일시 장애만 다시 보낸다 — 그대로)

## 맥락

- 실패 종류는 공급자마다 정본 하나가 상태 코드와 오류의 종류로 가른다(ADR-0154). 권한 없음(`PERMISSION_DENIED`)이면 저장한 키가 무효가 되고 알림이 남는다(ADR-0165).
- OpenRouter 의 오류 본문은 `code` 가 숫자이고 `type` 이 없다. 오류의 종류 이름은 `error.metadata.error_type` 에 실릴 때가 있다 — 틀린 키의 401 처럼 `metadata` 가 없는 본문도 있다. OpenAI SDK 는 숫자 `code` 를 읽다 예외를 낸다.
- OpenRouter 의 403 에는 키 권한 · 가드레일 문제(`permission_denied`)와 입력 내용 차단(모델 거절 `refusal` · 내용 필터 `content_policy_violation` · 검열)이 섞여 있다. 생성 전 403 에 `error_type` 이 실린다는 문서 근거는 `refusal` 하나다. 모델을 막는 가드레일은 실측에서 403 이 아니라 404 였다.
- 생성이 시작된 뒤 공급자 쪽에서 난 오류는 200 으로 답하며 본문에 싣는다(`finish_reason: "error"`, 또는 `choices` 없이 `error` 만).

## 결정

- **OpenRouter 의 실패 분류는 상태 코드와 `error.metadata.error_type` 만 읽는다** — 오류의 문구 · SDK 의 `code` · `type` 은 읽지 않는다.
- **403 은 `error_type` 이 `permission_denied` 일 때만 권한 없음이다.** 표시가 없거나 다른 값(내용 차단 등)이면 예상 밖의 답이다 — 키 상태를 바꾸지 않는다.
- 401 은 무효, 402 는 한도 소진, 429 · 408 · 409 · 5xx 는 일시 장애, 그 밖(404 포함)은 예상 밖의 답이다.
- **200 응답 안에 실린 오류는 예상 밖의 답이고 다시 보내지 않는다.**

규칙은 [llm Spec](../specs/llm.md#키-검증) llm-R8 · [생성 호출](../specs/llm.md#생성-호출) llm-R22 에 있다.

## 결과

- 입력 내용이 어느 모델의 안전 정책에 걸려도 사용자의 키가 무효가 되지 않는다.
- 표시 없는 가드레일 · 권한 403 은 키 무효 알림 없이 생성 실패로만 드러난다. 모델을 막는 가드레일(404)도 같다 — 다시 검증은 키 자체가 멀쩡하므로 유효로 답한다.
- 생성이 시작된 뒤 끊긴 요청은 과금됐을 수 있어 다시 보내지 않는다(ADR-0201 이 마감 초과를 다시 보내지 않는 까닭과 같다).

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 403 을 모두 권한 없음으로(다른 공급자와 같은 상태 코드 규칙) | 입력 내용 차단 한 번에 키가 무효가 되고 무효 알림이 사실과 다르다 |
| 내용 차단 표시(`refusal` · `content_policy_violation` · 검열 metadata)가 있을 때만 빼고 나머지 403 은 권한 없음 | 문서와 다른 꼴의 내용 차단이 오면 멀쩡한 키가 무효가 되고, 다시 검증과 다음 생성이 번갈아 상태를 뒤집는다 — 알림 하나를 놓치는 것보다 비용이 크다 |
| 403 을 모두 예상 밖의 답으로 | 표시가 있는 권한 문제도 알리지 못한다 |
| 200 안의 오류를 `error_type` 으로 갈라 일시 장애면 다시 보낸다 | 생성이 시작된 뒤라 과금이 겹칠 수 있고, SDK 밖에서 응답을 읽어야 한다 |
