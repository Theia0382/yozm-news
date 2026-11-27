# ADR-0204: OpenRouter 키 검증은 모델 목록 조회가 아니라 키 조회 요청이다

- 상태: 승인
- 날짜: 2026-11-17
- 근거: [이슈 #192](https://github.com/Theia0382/yozm-news/issues/192) — 실측(2026-11-17, 과금 없음): `GET /api/v1/models` 는 키 없이도 가짜 키로도 200(본문 852,895 바이트 · 모델 470개), `GET /api/v1/key` 는 키 없이 · 가짜 키로 401. 구현 `2669322`
- 관련: [ADR-0154](0154-key-validation-lists-models-and-failure-kinds-are-sorted-by-one-rule-per-provider-reading-the-error-kind.md)(부분 대체 — "키 검증의 요청은 모델 목록 조회 하나다"), [ADR-0150](0150-user-keys-travel-over-an-own-jdk-transport-for-the-sdks-without-redirects-or-proxy-and-with-a-response-body-cap.md)(키 조회도 같은 전송으로)

## 맥락

- 키 검증은 과금되지 않는 가벼운 요청 하나로 키가 쓸 수 있는지 본다(ADR-0154). OpenAI · Anthropic 은 모델 목록 조회가 인증을 요구해 틀린 키를 가린다.
- OpenRouter 의 모델 목록은 인증 없이 열려 있다 — 아무 문자열이나 정상 응답을 받는다. 본문도 1 MiB 응답 상한의 81% 로 크고 모델 수에 따라 자란다.
- OpenRouter 의 키 조회(`GET /key`)는 인증이 필수이고 과금되지 않는다. 응답 본문은 그 키의 한도 · 사용량이다.

## 결정

- **OpenRouter 의 키 검증 요청은 키 조회 하나다.** 정상 응답이면 유효다. 응답 본문(한도 · 사용량)은 읽지 않는다 — 잔액 · 한도는 다른 공급자처럼 검증에서 보지 않는다.
- 키 조회는 SDK 에 없는 요청이라 SDK 밖에서 보내지만, 전송은 같은 전송 하나다(리다이렉트 · 프록시 없음 · 본문 상한 · 마감).
- 키 검증의 요청은 공급자마다 정한다 — OpenAI · Anthropic 은 모델 목록 조회 그대로다.

규칙은 [llm Spec](../specs/llm.md#키-검증) llm-R6 에 있다.

## 결과

- 틀린 OpenRouter 키가 저장되지 않는다.
- 검증 응답이 작아 본문 상한과 무관하다.
- 한도가 소진된 키도 등록 때는 유효로 나온다 — 첫 생성에서 한도 실패로 드러난다(다른 공급자와 같다).

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 모델 목록 조회 | 인증 없이 열려 있어 틀린 키를 가리지 못한다 |
| 최소 생성 요청 | 검증마다 사용자 비용이 들고 모델을 골라야 한다(ADR-0154 의 기각 사유와 같다) |
| 키 조회 응답의 한도 · 사용량까지 읽어 소진을 미리 알린다 | 다른 공급자의 검증과 뜻이 달라지고, 소진은 생성에서 드러나는 규칙(ADR-0154)과 겹친다 |

## 후속 기록

- 2026-11-27: 결정 절 "전송은 같은 전송 하나다(리다이렉트 · 프록시 없음 · 본문 상한 · 마감)"의 리다이렉트 · 프록시 없음 — [ADR-0352](0352-external-api-http-clients-come-from-one-platform-configuration-pinned-to-http-1-1-so-a-connection-past-its-deadline-is-dropped.md) · [`platform` Spec](../specs/platform.md#externalhttpclients)
