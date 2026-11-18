# ADR-0219: 프롬프트 재료는 후보 선별이 토큰 어림과 같은 한 번의 읽기로 낸다

- 상태: 승인
- 날짜: 2026-11-18
- 근거: [이슈 #177](https://github.com/Theia0382/yozm-news/issues/177) — 생성기 포트가 기사 ID · 선정 이유만 나르고, 관심사는 길이만 공개 조회로 나오던 것을 조사함. 구현 커밋 `d3b074d`(관심 설정 조회) · `c3d15ed`(선별의 `DigestInput` · `tag` 의 이름 조회) · `011bcbe`(프롬프트 구성)
- 관련: [ADR-0210](0210-candidate-selection-cuts-to-the-input-token-budget-with-one-estimate-the-prompt-follows.md)(부분 대체 — 결정의 예산 산식에 사용자 구획의 고정 틀이 더해짐), [ADR-0211](0211-the-digest-consumer-query-of-interest-settings-writes-nothing-and-returns-followed-preferences-with-expanded-topics.md)(보충 — 미뤄 둔 시간대 · 관심사 본문을 더함, 부분 대체 — 낸 값의 "관심사 길이"), [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(관련 — 묶음은 ADR-0220)

## 맥락

- 프롬프트의 후보 줄은 제목 · 요약문 · 출처 · 발행 시각 · 주제 · 지역 이름이고, 사용자 프로필은 구독 주제 · 제외 지역의 이름 · 관심사다. 생성기 포트는 기사 ID · 선정 이유만 날랐다.
- 후보 선별은 이미 제목 · 요약문을 읽어 토큰을 어림하고 예산까지 자른다(ADR-0210). 같은 글을 생성 때 다시 읽으면 그 사이 바뀐 글(관심사 · 요약문)로 어림이 어긋날 수 있다.
- 태그 이름은 `tag` 의 체계 내용에 있지만, 그 조회(`TagSetView`)는 `evaluation` 의 평가 질문 타입으로 이름을 낸다 — `digest` 는 `evaluation` 에 의존하지 않는다.

## 결정

- **후보 선별이 생성의 입력(`DigestInput`)을 낸다** — 사용자 프로필(구독 주제 · 제외 지역의 이름, 관심사 본문, 시간대, 요약 길이, 추론 강도), 후보 기사의 값, 큰 스토리라인 묶음. 후보의 제목 · 요약문은 토큰 어림에 쓴 읽기의 값이다.
- **재료는 `application` 이 공개 조회로 읽고, 프롬프트 문면은 `domain` 의 순수 구성이 만들고, 생성기 어댑터는 관문 호출과 결과 옮기기만 한다.**
- **프롬프트 구성은 덩어리마다 어림 안으로 자른다** — 후보 줄 · 묶음 줄 · 프로필. 어림에 사용자 구획의 고정 틀(구획 키 · 괄호 · 시간대 ID — `CandidateCost.PROFILE_OVERHEAD`)을 더한다. 값은 측정 전 목표다.
- **다이제스트가 읽는 관심 설정은 관심사 본문 · 시간대 · 요약 길이 · 추론 강도를 낸다** — 관심사 길이는 본문에서 센다.
- **태그 이름은 `tag` 의 이름 조회(`TagSetDirectory.names` → `TagSetNames`)로 읽는다** — `tag` 의 자기 타입으로 내어 `digest` 가 `evaluation` 에 기대지 않는다.

## 결과

- 어림과 보내는 글이 같은 읽기라 예산이 맞는다. 생성(#178 이 부른다)은 선별의 결과를 그대로 넘긴다.
- 발행 시각을 사용자 시간대로 적는다 — 시간대가 공급자로 간다(외부 전송 고지 항목).
- `tag` 의 공개 조회가 하나 는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 생성 때 재료를 다시 읽는다 | 선별과 다른 시각의 글이라 어림이 어긋날 수 있고, 같은 읽기가 두 번이다 |
| 생성기 어댑터가 재료를 읽는다 | 어댑터에 조회가 들어가고, 로컬 대체물과 실제 생성기가 다른 재료를 본다 |
| `digest` 의 허용 의존에 `evaluation` 을 더해 `TagSetView` 를 읽는다 | 이름 하나를 읽으려 판정 관문 모듈의 타입에 기대게 된다 |
