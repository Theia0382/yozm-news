# ADR-0056: YAML 원문 읽기와 공통 거부는 `platform` 이 맡고, 문서별 형식 · 검증은 각 모듈의 `domain` 이 한다

- 상태: 승인. 부분 대체 — YAML 읽기를 함께 쓰는 모듈([ADR-0112](0112-tags-split-into-a-tag-module-and-evaluation-is-the-model-gateway-that-assembles-calls-and-records-each-judgement.md))
- 날짜: 2026-11-02
- 근거: [이슈 #68](https://github.com/Theia0382/yozm-news/issues/68) — 기점 `385aaef` 의 `source` 수집 프로필 읽기기와 바인더(`ProfileBinder`)에서 문서 종류와 무관한 판정을 가려 확인
- 관련: [ADR-0024](0024-collection-profile-yaml-and-preset-reference-or-own-profile.md)(수집 프로필 YAML — "거부는 `source` 모듈의 `domain` 코드가 한다" 가운데 공통 거부의 자리를 이 ADR 이 대체한다), [ADR-0054](0054-tag-set-seeded-from-default-yaml-on-first-start-with-immutable-versions-and-question-templates.md)(기본 태그 체계 파일)

## 맥락

- 수집 프로필은 YAML 을 snakeyaml-engine 의 이벤트 API 로 읽고, 앵커 · 별칭 · 명시 태그 · 같은 매핑 안 중복 키 · 알 수 없는 키 · 빈 값 · 형 오류를 경로 · 줄:열과 함께 한 번에 모두 거부한다(ADR-0024). 이 읽기기와 판정이 `source` 의 `domain` 안에 수집 프로필 판정과 섞여 있다.
- 기본 태그 체계도 YAML 파일이다(ADR-0054). 다른 모듈은 `source` 의 하위 패키지를 쓸 수 없고, YAML 읽기는 `source` 의 책임이 아니다.
- `evaluation` 에 두 번째 읽기기를 두면 같은 거부 규칙이 두 곳이 된다.

## 결정

- **YAML 원문 읽기와 공통 거부는 `platform` 의 공개 타입이다** — 원문을 트리로 옮기는 읽기기(`YamlTextReader`), 트리, 경로 · 위치, 공통 문제 종류(`YamlProblemKind`), 트리를 엄격하게 읽는 도구(`StrictYamlBinding`).
- **공통 거부는 문서 종류와 무관한 것만이다** — 길이 상한 초과 · 문법 오류 · 최상위가 매핑이 아님 · 여러 문서 · 알 수 없는 키 · 같은 매핑 안 중복 키 · 별칭 · 앵커 · 명시 태그 · 빈 값 · 형 오류. 해석은 YAML 1.2 JSON 스키마 규칙이다.
- **문서별 형식(키 목록) · 값 규칙 · 길이 상한의 값은 각 모듈의 `domain` 이 정본이다.** 상한은 읽기기를 만들 때 그 모듈이 넘긴다.
- **각 모듈의 문제 코드는 공통 종류와 같은 이름을 담은 그 모듈의 닫힌 어휘다.** 공통 종류를 모듈 코드로 옮기는 변환은 `default` 없는 망라 `switch` 라, 공통 종류가 늘면 컴파일이 깨진다. 공통 도구는 문제를 찾은 순서대로 호출자에게 넘기고, 호출자가 자기 문제와 같은 목록에 넣는다 — 문제 순서가 원문 순서 그대로다.

## 결과

> 부분 대체(첫 문장) — [ADR-0112](0112-tags-split-into-a-tag-module-and-evaluation-is-the-model-gateway-that-assembles-calls-and-records-each-judgement.md). 지금 상태: [overview.md §5](../overview.md#5-모듈-지도)
- `source` 와 `evaluation` 이 같은 읽기 · 거부를 쓴다. 다른 YAML 문서가 생겨도 같다.
- 수집 프로필의 운영자 화면 결과(문제 종류 이름 · 문구 · 위치 · 순서)는 그대로다. 길이 상한 초과 문구만 문서 종류를 모르는 문구로 바뀐다.
- `platform` 은 다른 모듈의 타입에 의존하지 않는다 — 공통 타입은 문서 종류를 모른다.
- snakeyaml-engine 은 `platform` 이 쓴다. 판이 바뀌면 읽기기 시험이 이벤트 동작의 변화를 드러낸다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `evaluation` 에 자기 YAML 읽기기를 둔다 | 같은 거부 규칙의 사본이다 |
| `source` 가 읽기기를 공개 API 로 낸다 | YAML 읽기는 소스의 책임이 아니고, `evaluation` 이 `source` 에 기대게 된다 |
| `platform` 의 하위 패키지를 named interface 로 연다 | 저장소에 선례가 없고 다른 모듈의 허용 의존 표기를 바꿔야 한다. 최상위는 기존 공개 타입과 같은 자리다 |
| 모듈 문제가 공통 종류를 그대로 품는 두 갈래 코드 | 수집 프로필의 공개 코드 문자열이 바뀐다 |
| 공통 도구가 자기 문제 목록을 따로 모은다 | 호출자 목록과 이어 붙일 때 문제 순서가 원문 순서와 달라진다 |
