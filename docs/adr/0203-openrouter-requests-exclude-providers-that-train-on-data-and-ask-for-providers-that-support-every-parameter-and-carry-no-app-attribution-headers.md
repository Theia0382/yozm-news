# ADR-0203: OpenRouter 요청은 학습에 쓰는 공급자를 빼고, 요청 인자를 모두 지원하는 공급자로만 보내며, 앱 표시 헤더를 싣지 않는다

- 상태: 승인
- 날짜: 2026-11-17
- 근거: [이슈 #192](https://github.com/Theia0382/yozm-news/issues/192) — OpenRouter 문서(Provider Routing · 구조화 출력), 공개 엔드포인트 목록(2026-11-17 — `anthropic/claude-sonnet-5.5` 의 엔드포인트 10개 중 4개는 구조화 출력이 없다), 대체 서버로 요청 본문 확인, 실제 호출(학습 제외 라우팅을 실은 요청이 두 계열 모두 라우팅됨). 구현 `2669322`
- 관련: [ADR-0202](0202-openrouter-is-added-as-a-user-llm-provider-and-its-default-models-are-the-two-providers-models-under-openrouter-ids.md)(OpenRouter 공급자), [ADR-0199](0199-generation-requests-carry-only-the-tasks-own-inputs-and-the-gateway-maps-them-to-each-provider.md)(추론 강도 · 출력 상한 · 스키마를 공급자의 꼴로 옮기는 일은 관문)

## 맥락

- OpenRouter 는 모델마다 여러 실제 공급자로 보낸다. 요청의 `provider` 칸으로 라우팅을 고른다 — `data_collection: "deny"` 는 학습에 쓰지 않는 공급자만(보관은 할 수 있다), `zdr: true` 는 보관하지 않는 엔드포인트만, `require_parameters: true` 는 요청의 인자를 모두 지원하는 공급자만. 기본값은 학습에 쓸 수 있는 공급자까지 포함한다.
- 같은 모델의 엔드포인트 가운데 구조화 출력을 지원하지 않는 것이 있다. 라우팅이 그쪽으로 가면 스키마 강제가 무시될 수 있다.
- 다이제스트 프롬프트에는 사용자 프로필 · 관심사가 든다.
- OpenRouter 문서의 예시는 앱 표시 헤더(`HTTP-Referer` · `X-OpenRouter-Title`)를 보내지만 선택이다.

## 결정

- **생성 요청마다 `provider: {data_collection: "deny", require_parameters: true}` 를 싣는다.** 사용자의 OpenRouter 계정 설정에 맡기지 않는다.
- **앱 표시 헤더를 싣지 않는다.**

규칙은 [llm Spec](../specs/llm.md#생성-호출) llm-R23 에 있다.

## 결과

- 사용자 프로필 · 관심사가 학습에 쓰는 공급자로 가지 않는다. 보관은 공급자마다 다를 수 있어 외부 전송 고지는 보관 가능성을 함께 적는다.
- 출력 상한 · 추론 강도 · 출력 스키마를 무시하는 공급자로 가지 않는다.
- 맞는 공급자가 없는 모델(운영자가 더한 모델 등)은 생성이 실패한다. 라우팅 단계의 실패는 OpenRouter 가 404 로 답했다(가드레일이 모델을 막은 경우의 실측) — 다시 보내지 않는 예상 밖의 답이다. 학습 제외 요건 때문에 실패하는 경우는 재지 않았다.
- 인스턴스 주소가 OpenRouter 에 남지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 라우팅 칸을 싣지 않고 사용자의 OpenRouter 계정 설정을 따른다 | 기본 상태의 계정이면 프로필 · 관심사가 학습에 쓰는 공급자로 갈 수 있다 |
| 언제나 `zdr: true` | 맞는 엔드포인트가 더 적어 생성 실패가 는다 |
| `require_parameters` 를 스키마를 강제할 때만 | 출력 상한 · 추론 강도도 무시될 수 있어 언제나 싣는다 |
| 앱 표시 헤더를 싣는다 | 인스턴스 주소가 OpenRouter 에 남는다. 이 서비스가 얻는 것이 없다 |
