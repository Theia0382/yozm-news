# ADR-0151: Spring AI · SDK 의 타입은 `llm` 밖으로 나가지 않고, 관문은 자기 타입만 낸다

- 상태: 승인
- 날짜: 2026-11-14
- 근거: [이슈 #148](https://github.com/Theia0382/yozm-news/issues/148) — `llm` 모듈을 만들며 관문이 내놓는 타입의 범위를 정함
- 관련: [ADR-0008](0008-v1-llm-providers.md)(공급자별 코드는 `llm` 모듈 밖으로 나가지 않는다 — 라이브러리 타입까지로 보충), [ADR-0149](0149-llm-calls-use-spring-ai-2-without-the-starter-and-build-sdk-clients-with-explicit-address-key-and-log-level-and-close-them.md)(클라이언트 조립), [ADR-0112](0112-tags-split-into-a-tag-module-and-evaluation-is-the-model-gateway-that-assembles-calls-and-records-each-judgement.md)(`evaluation` 관문 — 같은 꼴)

## 맥락

- 다이제스트 · 변화 리포트를 만드는 모듈이 LLM 을 부른다. 초안은 관문이 Spring AI 의 채팅 모델을 담은 핸들을 내주고 생성기가 그것으로 직접 호출하는 꼴이었다.
- 그 꼴에서는 호출과 예외 처리가 부르는 모듈에서 일어난다. 라이브러리 예외의 문구에는 공급자가 되돌려준 키 조각이 실리고([ADR-0149](0149-llm-calls-use-spring-ai-2-without-the-starter-and-build-sdk-clients-with-explicit-address-key-and-log-level-and-close-them.md)), 실패를 종류로 가르는 일이 부르는 모듈마다 놓인다.
- `evaluation` 관문은 부르는 쪽이 질문을 넘기고 요청 조립 · 호출 · 실패 분류를 관문이 한다.

## 결정

- **Spring AI 와 공급자 SDK 의 타입은 `llm` 의 어댑터 안에만 둔다.** 모듈의 공개 타입과 `domain` 의 포트에 나타나지 않는다.
- **관문은 자기 타입만 낸다** — 공급자, 허용 모델, 검증 결과, 실패 종류, 그리고 호출 API 가 생기면 그 요청 · 결과 타입.
- 호출 · 닫기 · 실패 분류 · 실패 문구는 관문이 한다. 부르는 모듈은 라이브러리 예외를 보지 않는다.

규칙은 [llm Spec](../specs/llm.md#공급자-클라이언트) llm-R15 에 있다.

## 결과

- 부르는 모듈은 공급자 · 라이브러리를 모른다. 라이브러리를 바꾸거나 판을 올려도 `llm` 밖은 바뀌지 않는다.
- 키 보호(실패 문구 · 로그)와 실패 분류가 생성 호출에도 관문 한 곳에서 성립한다.
- 호출 API(프롬프트 · 스키마를 넘기고 결과 · 토큰 사용량 · 실패 종류를 받는 꼴)를 생성 호출을 만드는 작업이 관문에 설계해야 한다. 그때까지 `llm` 안의 채팅 모델을 부르는 길이 없다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 핸들이 Spring AI 의 채팅 모델을 내놓고 생성기가 직접 부른다 | 라이브러리 예외와 실패 분류가 부르는 모듈마다 놓이고, Spring AI 가 여러 모듈의 의존이 된다 |
