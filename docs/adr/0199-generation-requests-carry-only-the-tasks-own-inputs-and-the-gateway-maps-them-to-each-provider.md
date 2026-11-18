# ADR-0199: 생성 호출의 요청은 과제의 것만 받고, 공급자의 꼴로 옮기는 일은 관문이 한다

- 상태: 승인
- 날짜: 2026-11-17
- 근거: [이슈 #175](https://github.com/Theia0382/yozm-news/issues/175) — 두 공급자의 구조화 출력 · 추론 · 출력 토큰 문서와 Spring AI 2.0.2 의 요청 조립(바이트코드)을 대조하고, 실제 공급자에 맞춘 스키마와 추론 강도 셋으로 생성 요청을 보내 확인. 구현 `dba289d` · `9ddad52`, 확인 장치 `45e420a`
- 관련: [ADR-0151](0151-spring-ai-and-sdk-types-stay-inside-llm-and-the-gateway-exposes-only-its-own-types.md)(보충 — "호출 API 가 생기면 그 요청 · 결과 타입"의 요청 쪽), [ADR-0200](0200-generation-results-carry-the-stop-reason-and-schema-enforcement-and-failures-use-the-five-key-check-kinds.md)(결과 쪽), [ADR-0153](0153-allowed-llm-models-come-from-a-bundled-file-that-an-operator-file-replaces-as-a-whole.md)(허용 모델의 구조화 출력 지원 여부)

## 맥락

- 생성 호출을 부르는 모듈(다이제스트 · 변화 리포트)은 공급자 · 라이브러리를 모른다([ADR-0151](0151-spring-ai-and-sdk-types-stay-inside-llm-and-the-gateway-exposes-only-its-own-types.md)). 그 모듈들은 출력 구조를 표준 JSON Schema 하나로 서술한다.
- 두 공급자가 강제하는 JSON Schema 는 서로 다른 부분집합이다. OpenAI 의 엄격 모드는 모든 객체에 `additionalProperties: false` 와 모든 속성의 `required` 를 요구하고(선택 속성은 null 을 받게 해 흉내 낸다), 지원하지 않는 키워드가 있으면 요청을 거부한다. Anthropic 은 문자열 · 수 제약과 복잡한 배열 제약을 받지 않고 선택 속성은 받는다. Spring AI 는 응답 형식의 스키마를 고치지 않고 넘긴다.
- 추론을 하는 모델은 요청에 강도가 없으면 모델마다 다른 기본 강도로 돈다. 강도는 출력 토큰 · 소요 시간 · 사용자 비용(BYOK)을 함께 바꾼다.
- 두 공급자 모두 출력 토큰 상한이 추론과 본문을 합친 것이다. Anthropic 은 상한이 필수 인자이고 Spring AI 의 기본은 4,096 이다. 필요한 본문 크기는 출력 구조를 아는 부르는 쪽만 알고, 추론이 쓰는 몫은 모델과 강도에 달린 공급자 지식이다.

## 결정

- **요청은 과제의 것만 받는다** — 지시 · 사용자 구획, 표준 JSON Schema 원본, 공급자와 무관한 추론 강도 셋(낮음 · 보통 · 높음), 본문 토큰 몫.
- **출력 스키마는 관문이 공급자마다 맞춘다.** 공급자마다 허용 키워드 목록 밖을 지우고 공급자가 요구하는 꼴(모든 객체를 닫기, OpenAI 는 모든 속성을 필수로 하고 선택 속성이 null 을 받게)로 바꾼다. 모델이 구조화 출력을 지원할 때만 강제 인자로 보낸다.
- **추론 강도는 부르는 쪽이 고르고 관문이 공급자의 값으로 옮긴다.** 추론을 끄는 값은 두지 않는다.
- **출력 토큰 상한은 관문이 정해 언제나 명시해 보낸다** — 부르는 쪽의 본문 몫에 추론 강도마다의 여유를 더한 값이다. 여유는 측정 전의 잠정값이다.

규칙은 [llm Spec](../specs/llm.md#생성-호출) llm-R21 · R23 에 있다.

## 결과

- 부르는 쪽은 공급자를 모른 채 스키마 하나 · 강도 하나 · 본문 몫 하나를 넘긴다. 공급자 지식(키워드 부분집합 · 강도 값 · 추론 몫)은 `llm` 안에 남는다.
- 공급자가 강제하는 스키마는 원본보다 느슨하다 — 지운 규칙(문자열 길이 · 개수 상한 등)은 부르는 쪽의 읽기 · 검증이 맡는다. 참조 · 조합 키워드를 쓰는 스키마는 맞춘 뒤 뜻이 바뀐다.
- 공급자의 스키마 부분집합이 바뀌면 관문의 허용 목록이 따라간다. 실제 공급자 확인으로 맞춘 스키마와 강도 셋을 받는지 다시 본다.
- 여유 상수가 작으면 응답이 잘린다 — 잘림은 결과의 종료 사유로 드러난다([ADR-0200](0200-generation-results-carry-the-stop-reason-and-schema-enforcement-and-failures-use-the-five-key-check-kinds.md)).

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 부르는 쪽이 두 공급자의 공통 부분집합으로 스키마를 쓴다 | 공급자의 제약이 부르는 모듈로 샌다. 교집합이라 더 느슨하다 |
| 공급자가 받지 않는 키워드가 있으면 스키마를 강제하지 않는다 | 다이제스트 스키마는 두 공급자 모두에서 강제가 꺼진다 |
| 추론 강도를 공급자 · 모델의 기본에 맡긴다 | 같은 과제가 고른 모델에 따라 다른 강도로 돌아 사용자 비용이 모델 선택에 숨는다 |
| 관문 상수 하나의 추론 강도 | 과제마다(다이제스트 · 변화 리포트) 비용과 품질을 따로 정할 수 없다 |
| 부르는 쪽이 추론을 포함한 출력 상한 전체를 준다 | 부르는 쪽이 모델마다의 추론 몫을 알아야 한다 |
| 관문 상수 하나의 출력 상한 | 과제의 본문 크기에 맞게 멈추지 않는 생성을 막지 못한다 |

## 후속 기록

- 2026-11-17: 결정 절 "공급자의 꼴로 옮기는 일은 관문이 한다"의 OpenRouter — 두 공급자 맞춤을 함께 만족하는 맞춤 하나 — [ADR-0206](0206-schemas-sent-to-openrouter-satisfy-both-providers-rules-and-an-optional-value-list-takes-null-without-a-type.md) · [llm Spec](../specs/llm.md#생성-호출) llm-R21
