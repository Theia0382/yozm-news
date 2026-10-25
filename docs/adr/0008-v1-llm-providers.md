# ADR-0008: v1 지원 LLM 공급자는 OpenAI(ChatGPT)와 Anthropic(Claude)

- 상태: 승인
- 날짜: 2026-10-25

## 맥락

[ADR-0003](0003-byok-personalized-digest.md)에 따라 사용자가 LLM 키를 가져온다. 지원 공급자가 많을수록 키 검증, 오류 처리, 구조화 출력 차이, 모델 목록 관리 부담이 커진다.

## 결정

- v1은 **OpenAI API**(ChatGPT 계열 모델)와 **Anthropic API**(Claude 계열 모델)만 지원한다.
- 운영자가 공급자별 허용 모델 목록을 관리하고, 사용자는 그중 하나를 고른다. 모델마다 입력 토큰 예산과 구조화 출력 지원 여부를 함께 관리한다.
- 두 공급자 모두 Spring AI 2.x 구현을 쓴다.

## 결과

- 화면 문구에서 **구독과 API 키가 다르다는 점**을 분명히 안내해야 한다. ChatGPT Plus나 Claude Pro 같은 구독으로는 API를 호출할 수 없고, 각 공급자의 개발자 콘솔(OpenAI Platform, Claude Console)에서 API 키를 발급하고 결제 수단을 등록해야 한다. 키 등록 페이지에 발급 방법 안내를 넣는다.
- 공급자 추가(예: Google Gemini)는 `LlmClientFactory` 구현과 모델 목록 추가로 끝나야 한다. 공급자별 코드는 `llm` 모듈 밖으로 나가지 않는다.

## 검토한 대안

| 대안 | 보류 이유 |
|---|---|
| Google Gemini 포함 | 사용자 결정으로 v1 제외 |
| OpenRouter 같은 중개 서비스 키 지원 | 한 키로 여러 모델을 쓸 수 있지만, 사용자의 프롬프트가 거치는 곳이 하나 더 생긴다. 이후 검토 |
