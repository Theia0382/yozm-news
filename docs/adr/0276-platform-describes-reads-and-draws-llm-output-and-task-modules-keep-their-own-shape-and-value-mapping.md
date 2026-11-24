# ADR-0276: LLM 출력의 서술 · 읽기 · JSON Schema 그리기는 `platform` 이 맡고, 과제 모듈은 자기 서술과 값 옮기기만 한다

- 상태: 승인
- 날짜: 2026-11-22
- 근거: [이슈 #237](https://github.com/Theia0382/yozm-news/issues/237) — 기점 `d25590c` 의 다이제스트 출력 서술 · 읽기 코드에서 다이제스트를 모르는 부분을 가려 확인하고, `digest` 공개와 견준 뒤 `platform` 을 골랐다
- 관련: [ADR-0056](0056-platform-reads-yaml-text-and-applies-common-rejection.md)(같은 꼴 — YAML 원문 읽기), [ADR-0199](0199-generation-requests-carry-only-the-tasks-own-inputs-and-the-gateway-maps-them-to-each-provider.md)(부르는 쪽이 읽기 · 검증을 맡는다 — 바뀌지 않는다), [ADR-0269](0269-the-digest-output-keys-top-stories-by-item-alias-as-required-fields-and-lists-headlines-as-elements.md)(다이제스트 서술 — 바뀌지 않는다)

## 맥락

- 다이제스트는 출력 구조를 서술 하나로 적고, 그 서술에서 읽기 규칙과 생성에 넘길 JSON Schema 문서가 함께 나온다. 서술 마디 · 처분(비었을 때 · 상한을 넘을 때), JSON Schema 그리기, 엄격한 JSON 읽기(코드 펜스 한 겹 · 객체 하나 · 겹친 키 거부)와 서술 걷기는 다이제스트 타입을 쓰지 않는다. 이 기구가 다이제스트의 하위 패키지에 있어 다른 모듈은 쓸 수 없다.
- 변화 리포트도 같은 방식으로 출력을 검증해야 한다.
- `llm` 은 출력을 파싱 · 검증하지 않고 부르는 쪽이 맡는다(ADR-0199).
- 수집 프로필의 YAML 읽기가 `source` 안에 있을 때 기본 태그 체계가 두 번째 소비자가 되자, 문서 종류와 무관한 읽기 · 공통 거부를 `platform` 으로 옮겼다(ADR-0056).

## 결정

- **`platform` 이 LLM 출력 읽기의 범용 기구를 낸다** — 서술 마디(`OutputShape` — 처분 enum 을 안에 품는다), 걸은 값(`OutputValue`), 읽기와 그 결과(`StructuredOutputReader` · `StructuredRead`), 서술을 JSON Schema 문서로 그리기(`StructuredOutputSchema`).
- **과제 모듈(다이제스트 · 변화 리포트)은 자기 서술(칸 구성 · 상한 · 속성 이름)과, 걸은 값을 자기 값으로 옮기기 · 입력과 대조하기만 갖는다.**

규칙은 [platform Spec](../specs/platform.md#llm-출력-읽기) platform-R31 · R32 에 있다.

## 결과

- 출력 읽기 규칙의 정본이 하나다 — 다이제스트와 리포트의 읽기가 어긋나지 않는다.
- 기구를 고치는 변경은 다이제스트 · 리포트를 함께 본다.
- 다이제스트 코드가 기구를 `platform` 에서 쓰도록 옮겨진다 — 동작은 바뀌지 않는다.
- 관문(`llm`)은 과제 출력의 상한 · 처분을 모른 채로 남는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `report` 에 사본 | 같은 기구가 둘이 되어 한쪽만 고치면 어긋난다 |
| `llm` 공개 타입으로 | `llm` 의 "출력을 파싱 · 검증하지 않는다" 경계를 뒤집고, 관문이 과제 출력의 상한 · 처분까지 알게 된다 |
| `digest` 공개 타입으로 | 새 의존은 없지만 `digest` 가 다이제스트가 아닌 범용 기구를 공개하고, 리포트의 읽기 규칙이 다이제스트 Spec 에 있게 된다. 다음 소비자가 생기면 다시 옮겨야 한다 |
