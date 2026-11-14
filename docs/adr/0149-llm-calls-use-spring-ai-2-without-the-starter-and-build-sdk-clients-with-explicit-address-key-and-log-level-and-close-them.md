# ADR-0149: LLM 호출은 Spring AI 2.0 을 스타터 없이 쓰고, SDK 클라이언트를 직접 만들어 주소 · 키 · 로그 수준을 명시하고 닫는다

- 상태: 승인
- 날짜: 2026-11-14
- 근거: [이슈 #148](https://github.com/Theia0382/yozm-news/issues/148) — `spring-ai-openai` · `spring-ai-anthropic` 2.0.2 와 두 공급자 SDK 의 jar 를 열어 클라이언트를 만드는 경로 · 환경 변수 읽기 · 로그 · 닫기를 확인하고, 두 공급자의 모델 목록 엔드포인트에 가짜 키로 요청해 실제 오류 응답을 받음
- 관련: [ADR-0008](0008-v1-llm-providers.md)("두 공급자 모두 Spring AI 2.x 구현을 쓴다" — 쓰는 방법을 보충), [ADR-0003](0003-byok-personalized-digest.md)(사용자 키로 요청마다 클라이언트를 만든다 — 이 결정의 전제), [ADR-0031](0031-feed-request-blocks-internal-addresses-with-apache-httpclient-deadline.md)(결과 절이 `llm` 에 넘긴 `RestClient` 팩토리 선택 — 해당 없음), [ADR-0150](0150-user-keys-travel-over-an-own-jdk-transport-for-the-sdks-without-redirects-or-proxy-and-with-a-response-body-cap.md)(전송), [ADR-0151](0151-spring-ai-and-sdk-types-stay-inside-llm-and-the-gateway-exposes-only-its-own-types.md)(타입의 범위)

## 맥락

- 사용자 키(BYOK)로 요청마다 클라이언트를 만든다([ADR-0003](0003-byok-personalized-digest.md)). 두 공급자는 Spring AI 2.x 로 부르기로 했다([ADR-0008](0008-v1-llm-providers.md)).
- Spring AI 2.0 의 OpenAI · Anthropic 모듈은 공급자의 공식 Java SDK 위에 얹은 층이다. HTTP 는 Spring 의 `RestClient` 가 아니라 SDK 가 한다.
- Spring AI 의 설정 도우미와 SDK 의 `fromEnv()` 는 주소 · 키가 비어 있으면 프로세스 환경 변수(`OPENAI_BASE_URL` · `OPENAI_API_KEY` · `ANTHROPIC_BASE_URL` · `ANTHROPIC_API_KEY` · `ANTHROPIC_AUTH_TOKEN` 등)에서 대신 읽는다. 주소가 그렇게 정해지면 사용자 키가 운영자 호스트의 설정이 가리키는 곳으로 가고, 키가 그렇게 정해지면 운영자의 키로 호출된다.
- SDK 는 로그 수준을 환경 변수(`OPENAI_LOG` · `ANTHROPIC_LOG`)에서 읽는다. 켜지면 인증 헤더는 가리지만 요청 · 응답 본문을 찍는다. OpenAI 는 틀린 키의 오류 본문에 키의 접두와 끝 4자리를 되돌려주고, SDK 예외의 문구가 그 본문을 담는다.
- Spring AI 의 채팅 모델은 SDK 클라이언트를 받지 않으면 동기 · 비동기 클라이언트를 스스로 만들고, OpenAI 쪽은 그것을 닫는 길을 내놓지 않는다. 클라이언트마다 연결 풀과 스레드가 있다.

## 결정

- **Spring AI 2.0 의 공급자 모듈(`spring-ai-openai` · `spring-ai-anthropic`)을 스타터 없이 쓴다.** 자동 구성이 없어 전역 채팅 모델 빈과 `spring.ai.*` 설정이 없다.
- **공급자 SDK 의 클라이언트는 `llm` 이 직접 조립한다.** Spring AI 의 설정 도우미와 SDK 의 `fromEnv()` 를 부르지 않는다.
  - 주소와 키를 언제나 명시한다. 공급자의 기본 주소는 코드 상수이고 운영 설정으로 바꿀 수 없다 — 사용자 키와 프롬프트는 공급자에만 간다.
  - SDK 의 로그 수준을 꺼짐으로 명시한다.
  - SDK 의 자동 재시도를 쓸지와 마감은 호출마다 이 모듈이 정해 넘긴다.
- **만든 클라이언트는 쓰고 나서 닫는다.** Spring AI 의 채팅 모델에는 이 모듈이 만든 SDK 클라이언트를 동기 · 비동기 둘 다 넘긴다.
- **라이브러리 예외의 문구 · 응답 본문을 실패 문구와 로그에 담지 않는다.** 실패 문구는 고정 문구 · 상태 코드 · 예외 클래스 이름뿐이다.

규칙은 [llm Spec](../specs/llm.md#공급자-클라이언트) llm-R9 · R11 · R12 · R14 에 있다.

## 결과

- 운영자 호스트 · 개발자 셸에 있는 `OPENAI_*` · `ANTHROPIC_*` 환경 변수가 사용자 키의 행선지 · 호출 키 · 로그에 영향을 주지 않는다. 시험 JVM 이 그 변수들을 일부러 둔 채 돈다.
- 호환 엔드포인트(다른 주소의 같은 API)를 운영자가 붙일 수 없다. 열려면 사용자 키가 가는 곳에 대한 결정을 다시 해야 한다.
- 공급자 SDK 둘이 앱 이미지에 든다 — SDK 의 jar 만 약 108 MB 다(`openai-java-core` 4.80.0 · `anthropic-java-core` 2.72.0 의 jar 크기 합).
- `RestClient` 를 쓰지 않으므로 [ADR-0031](0031-feed-request-blocks-internal-addresses-with-apache-httpclient-deadline.md) 이 넘긴 팩토리 선택은 생기지 않는다.
- SDK 의 판이 오르면 클라이언트 조립 코드가 따라간다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| Spring AI 의 스타터 · 설정 도우미로 클라이언트를 만든다 | 로그 수준을 넘길 수 없고, 값이 비면 환경 변수를 읽는다. 전역 빈은 요청마다 다른 키와 맞지 않는다 |
| 공식 SDK 만 직접 쓴다(Spring AI 없음) | 크기는 그대로다. 구조화 출력 · 토큰 사용량 · 프롬프트 추상을 공급자마다 직접 맞춰야 한다 |
| JDK `HttpClient` 로 두 공급자의 REST API 를 직접 부른다 | 채팅 · 구조화 출력 · 오류 형식을 공급자마다 구현하고 API 변화를 직접 따라간다 |
| 공급자의 기본 주소를 운영 설정으로 연다 | 사용자 키와 프롬프트가 운영자가 정한 제3자를 거칠 수 있다([ADR-0008](0008-v1-llm-providers.md) 의 중개 서비스 보류와 같은 이유) |
