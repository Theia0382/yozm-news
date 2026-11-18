# ADR-0202: 사용자 LLM 공급자에 OpenRouter 를 더하고, 기본 허용 모델은 두 공급자의 지금 모델을 OpenRouter ID 로 둔다

- 상태: 승인
- 날짜: 2026-11-17
- 근거: [이슈 #192](https://github.com/Theia0382/yozm-news/issues/192) — OpenRouter 공개 모델 목록(2026-11-17)의 모델 ID · 단가 · 지원 인자, 리드 결정(기본 목록 · 첫 모델). 구현 `ab18052` · `2669322` · `a42b38f`
- 관련: [ADR-0008](0008-v1-llm-providers.md)(부분 대체 — "v1은 OpenAI API 와 Anthropic API 만 지원한다"와 검토한 대안 표의 OpenRouter 행. 보충 — 결과 절 "공급자 추가는 `LlmClientFactory` 구현과 모델 목록 추가로 끝나야 한다"), [ADR-0153](0153-allowed-llm-models-come-from-a-bundled-file-that-an-operator-file-replaces-as-a-whole.md)(기본 파일에 공급자를 더한다), [ADR-0178](0178-the-llm-model-to-use-is-decided-at-query-time-from-the-chosen-model-the-allowed-list-and-key-status.md)(대체 순서 — `Provider` 의 순서)

## 맥락

- 사용자 LLM 공급자는 OpenAI · Anthropic 둘이었다. 사용자는 공급자마다 키를 따로 발급해 등록한다.
- OpenRouter 는 여러 회사의 모델을 한 키 · OpenAI 꼴 API 로 쓰게 하는 중계 서비스다. 모델마다 그 모델의 회사나 그 모델을 호스팅하는 공급자(Azure · Bedrock · Vertex 등)로 보낸다. ADR-0008 은 "사용자의 프롬프트가 거치는 곳이 하나 더 생긴다"며 이후 검토로 미뤘다.
- 운영자 쪽(Jev 호환 경로 · 외부 임베딩)은 이미 OpenRouter 를 쓴다(ADR-0063 · ADR-0090).
- 기본 허용 모델 목록에 공급자의 모델이 없으면, 그 공급자 키만 있는 사용자에게는 쓸 모델이 없다(ADR-0178 — 유효한 키가 있는 공급자의 첫 모델).

## 결정

- **OpenRouter 를 세 번째 사용자 LLM 공급자로 더한다** — `Provider.OPENROUTER`(키 `openrouter`). 대체 순서에서 마지막이다.
- 공급자 주소는 다른 공급자처럼 코드 상수다(`https://openrouter.ai/api/v1`).
- **기본 허용 모델 목록의 OpenRouter 모델은 두 공급자의 지금 모델 여덟을 OpenRouter ID 로 둔다** — 첫 모델(기본)은 `openai/gpt-6.1-terra`, 나머지는 OpenAI 순서 뒤에 Anthropic 순서. 다른 회사의 모델은 운영자 파일로 더한다.
- **OpenRouter 의 모델을 쓰면 프롬프트가 OpenRouter 를 거친다는 사실을 외부 전송 고지에 적는다** — 화면 고지와 [security.md](../security.md#개인정보) 외부 전송 항목.

규칙은 [llm Spec](../specs/llm.md#공급자와-허용-모델-목록) llm-R1 · R2 에 있다.

## 결과

- 사용자는 OpenRouter 키 하나로 GPT · Claude 계열을 모두 쓴다. 기본 설치에서 OpenRouter 키만으로 다이제스트가 돈다.
- 사용자의 프롬프트(프로필 · 관심사 · 후보 기사)가 OpenRouter 와 그 모델의 실제 공급자를 거친다 — 학습 제외 라우팅([ADR-0203](0203-openrouter-requests-exclude-providers-that-train-on-data-and-ask-for-providers-that-support-every-parameter-and-carry-no-app-attribution-headers.md))이 학습은 막지만 보관은 막지 않는다.
- 외부 전송 고지에 새 회사가 늘지 않는다 — 기본 목록은 지금 두 회사의 모델이다. 운영자가 다른 회사의 모델을 더하면 그 운영자가 고지를 판단한다.
- 공급자 하나를 더하는 일은 `llm` 안에서 끝나지 않는다 — 공급자 값을 저장하는 CHECK 제약 셋(키 · 고른 모델 · 다이제스트), 화면용 API 명세의 enum, 키 화면 문구, 외부 전송 고지가 함께 바뀐다. ADR-0008 결과 절의 "`LlmClientFactory` 구현과 모델 목록 추가로 끝나야 한다"는 공급자별 코드가 `llm` 밖으로 나가지 않는다는 뜻으로만 성립한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 기본 목록에 OpenRouter 모델을 두지 않고 운영자 파일로 더한다 | OpenRouter 키만 있는 사용자에게 쓸 모델이 없어 기본 설치에서 기능이 돌지 않는다 |
| 기본 목록에 다른 회사의 모델(Gemini 등)도 둔다 | 그 모델의 구조화 출력을 재지 않았고, 외부 전송 고지에 회사가 늘고, 학습 제외 라우팅에 맞는 공급자를 모델마다 확인해야 한다 |
| 첫 모델을 `anthropic/claude-sonnet-5.5` 로 | 같은 중간 등급이지만 단가가 두 배다(입력 2.00 · 출력 10.00 USD/100만 토큰 대 1.00 · 5.00). 리드가 terra 를 골랐다 |
