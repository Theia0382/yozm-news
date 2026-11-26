# ADR-0333: 용어의 정의는 그 개념을 소유한 모듈의 Spec 이 갖고, 주인을 한 모듈로 정할 수 없는 횡단 용어만 `overview` 가 갖는다

- 상태: 승인
- 날짜: 2026-11-25
- 근거: [이슈 #281](https://github.com/Theia0382/yozm-news/issues/281) — 모듈 Spec 을 다시 쓰면서 "여러 모듈이 쓰는 용어는 `overview` §8" 을 글자대로 적용하니, evaluation §2 의 용어 16개처럼 한 모듈이 소유하고 다른 Spec 이 같은 뜻으로 쓰는 말까지 모두 `overview` 로 가야 했다. 구현 커밋 `c804fd3`
- 관련: [ADR-0034](0034-module-spec-as-public-interface-specification-and-docs-by-kind.md)(부분 대체 — 결정의 용어 자리 항목), [ADR-0331](0331-a-development-term-has-only-the-meaning-of-one-code-name-and-words-with-several-meanings-are-renamed-per-meaning.md)(한 말에 한 뜻 — 이 ADR 은 그 말의 정의가 있는 자리를 정한다)

## 맥락

- 용어의 자리는 쓰는 범위가 정했다. 한 모듈 안에서만 쓰는 말은 그 Spec 의 §2, 여러 모듈이 쓰는 말은 `overview` §8 이었다(ADR-0034).
- 태그 판정 · 실패 종류 · 초대 · 큰 슬롯처럼 한 모듈이 개념을 소유한 말도 다른 Spec 이 한 번이라도 쓰면 `overview` 로 가야 했다. 그러면 모듈 Spec §2 대부분이 비고 `overview` §8 이 수백 행으로 불어나, 정의가 그 규칙과 떨어진 문서에 있게 된다.
- 꼭대기 · 헤드 · 사건 · 스토리라인처럼 여러 모듈이 판단의 열쇠로 쓰는 말은 한 모듈의 것이라 하기 어렵다.

## 결정

- **용어의 정의는 그 개념을 소유한 모듈의 Spec §2 가 갖는다.** 다른 모듈이 같은 말을 써도 정의는 소유 모듈에 하나이고, 다른 Spec 은 그 자리를 가리킨다.
- **주인을 한 모듈로 정할 수 없는 횡단 용어만 `overview` §8 이 갖는다.** 사건과 스토리라인, 사용자 스토리라인, 꼭대기와 헤드가 그렇다.
- 화면 이름은 화면 Spec §2 가 갖는다([ADR-0328](0328-the-frontend-has-one-screen-spec-web-holding-screen-names-routes-and-screen-behavior.md)).
- 정본은 [`docs/README.md` §4](../README.md#4-작성-규칙) "한 말에 한 뜻"과 [`specs/README` §2](../specs/README.md#2-공통-구조)다.

## 결과

- 모듈 Spec §2 는 그 모듈이 소유한 용어를 그대로 갖는다. `overview` §8 은 횡단 용어만 갖는다.
- 소유 모듈이 흐린 말은 판정이 필요하다. 여러 모듈이 그 말로 판단을 가르면 횡단 용어로 본다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 여러 모듈이 쓰는 용어는 모두 `overview` §8 에 둔다(쓰는 범위 기준) | 모듈 Spec §2 대부분이 비고 `overview` §8 이 수백 행으로 불어나, 정의가 그 규칙과 떨어진 문서에 있게 된다 |
| 용어집 문서를 따로 둔다 | `overview` §8 · Spec §2 와 자리가 셋이 된다 |
