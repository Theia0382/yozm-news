# ADR-0326: 개발 이름은 공유 스토리라인을 `Storyline`, 사용자 스토리라인을 `UserStoryline` 으로 부르고, 종류를 모르는 판정 후보는 `Candidate` 로 부른다

- 상태: 승인
- 날짜: 2026-11-25
- 근거: [이슈 #274](https://github.com/Theia0382/yozm-news/issues/274) — 개발 이름은 스토리라인 = `storyline` 인데 코드 · 개발 문서 여러 곳이 공유 스토리라인과 사용자 스토리라인을 `Flow` · "흐름"으로도 불러, 한 개체를 개발 이름 둘로 불렀다. 구현 커밋 `c639c02` · `0f665f2` · `30d9416` · `6895591`
- 관련: [ADR-0292](0292-reader-screens-call-a-storyline-an-event-flow.md)(개발 이름을 "흐름"으로 하는 안을 기각 — 같은 방향, 대체 없음), [ADR-0320](0320-operator-screens-operations-docs-and-the-evaluation-tool-also-call-a-storyline-an-event-flow-and-a-big-one-a-big-topic.md)(화면 · 운영 쪽 이름 — 대체 없음), [ADR-0304](0304-a-change-report-takes-previous-values-from-the-previous-reports-snapshot-matched-one-to-one-and-keeps-them-in-its-own-snapshot.md)(스냅숏 판 규칙을 따름 — 대체 없음), [ADR-0194](0194-storyline-linking-asks-jev-only-for-band-candidates-and-reuses-recorded-probabilities-until-either-side-doubles.md)(보충 — 판정 API 의 이름), [ADR-0132](0132-storyline-relations-are-judged-by-jev-on-candidate-pairs-and-linked-only-above-a-probability-threshold.md)(보충 — 관계 판정 API 의 ID 이름)

## 맥락

- 개발 이름은 한국어와 코드가 같은 말이다 — 스토리라인 = `storyline`. "흐름"은 독자 화면 · 운영자 화면 · 운영 문서의 이름이다(ADR-0292 · ADR-0320).
- `evaluation` 의 판정 API 둘, 판정 원본 표 둘, `digest` · `user` · `report` 의 사용자 스토리라인 묶음 · 재료 · 스냅숏, `reader` 의 리포트 응답과 화면용 API 칸이 `Flow` 이름을 썼다. 개발 문서와 주석 · 로그는 스토리라인을 "흐름"이라 썼다.
- "흐름"이 가리키는 것은 둘이다 — 공유 스토리라인 하나(판정 API · 리포트 인용의 표시)와 사용자 스토리라인(헤드 · 꼭대기 묶음 · 정리 · 스냅숏의 줄). 한 모듈 안에서도 섞인다.
- 스토리라인 소속 판정의 후보는 언제나 스토리라인이 아니다 — 잇기의 짝 띠는 붙일 스토리라인이 없는 사건과 함께 새 스토리라인을 열 이웃 사건을 후보로 묻는다.
- 사건 소속 판정과 같은 꼴(`EventMembership`)로 이름을 지으면 `StorylineMembership` 이 되는데, `storyline` 에 같은 이름의 공개 레코드(소속 하나와 판정 근거)가 있었다.

## 결정

- **개발 이름은 가리키는 것으로 고른다** — 공유 스토리라인 하나와 그 표시는 `Storyline` · "스토리라인", 사용자 스토리라인(헤드 · 꼭대기 묶음 · 묶음 번호 · 정리)은 `UserStoryline` · "사용자 스토리라인"이다. 모듈 단위로 일괄하지 않는다.
- **판정 API 는 `{개체}{판정 대상}` 의 맨 이름을 갖는다** — 스토리라인 소속 판정 `StorylineMembership`, 관계 판정 `StorylineRelation`(사건 소속 판정 `EventMembership` 과 같은 꼴). `storyline` 의 소속 레코드는 이 모듈의 공개 조회 값 꼴로 `StorylineMembershipView` 다.
- **스토리라인 소속 판정의 후보 ID 는 `candidateId` 다** — 공개 값 `StorylineCandidate`, 판정 원본의 물은 후보 jsonb 키, `storyline` 의 잇기 안 후보가 같은 이름을 쓴다. 관계 판정의 ID 는 언제나 스토리라인이라 `storylineId` · `firstStorylineId` · `secondStorylineId` 다.
- **판정 원본 표는 Java 이름과 같은 말이다** — `storyline_membership_judgements` · `storyline_relation_judgements`(칸 `first_storyline_id` · `second_storyline_id`). 사건 소속 판정 원본 `article_event_judgements` 는 그대로다.
- **화면용 API 의 리포트 칸은 `storyline` · `userStorylines` 다** — 스키마 `ReportStorylineResponse` · `ReportUserStorylineCountResponse` · `ReportUserStorylineLevelResponse`.
- **모델이 읽는 글은 바꾸지 않는다** — Jev 문면(`RelationWording` 의 "flow" · 자리표시 `{flow}`), 사용자 LLM 지시 문면과 구획 키 `"flows"`, 그 값을 담은 상수 이름(`FLOWS` · `FLOW_NAME`). 화면 글과 처리 과정이라는 뜻의 "흐름"도 그대로다.

## 결과

- 한 개체를 개발 이름 하나로 부른다. 화면 쪽 이름("흐름")과 개발 쪽 이름의 대응은 [overview §8](../overview.md#8-용어)이 갖는다.
- 판정 원본 표 · 칸 · 제약 · 인덱스 · 시퀀스 이름은 V86 이 바꾼다. PostgreSQL 이 표 · 칸 이름으로 지은 제약(기본 키 · NOT NULL · CHECK)까지 표의 제약 목록에서 찾아 바꾼다. 저장된 물은 후보의 키 `flowId` 는 `candidateId` 가 된다.
- 리포트 스냅숏의 사용자 스토리라인 줄은 키 `userStorylines` 로 쓰고 꼴의 판이 2 다. 판이 같을 때만 읽는 규칙(ADR-0304 · [report Spec](../specs/report.md#5-규칙과-불변식) report-R19)대로 V87 이 저장된 1판을 2판으로 옮기고, 읽기는 2판 하나다.
- 화면용 API 의 칸 이름이 바뀌어 프론트엔드가 생성 타입을 따라간다. 운영자 API 의 칸 · 스키마는 바뀌지 않는다.
- 사건 소속 판정의 요청 검사 문구도 "후보가 없다" · "후보 ID 가 겹친다" 꼴이 된다 — 두 판정이 같은 검사를 쓴다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 스토리라인 소속 판정의 후보 ID 를 `storylineId` 로 | 짝 띠에서 이웃 사건의 ID 를 스토리라인 ID 라 부르고, 그 이름이 판정 원본에 남는다 |
| 판정 API 를 다른 꼴(`StorylineMembershipJudge` 등)로 | 사건 소속 판정 `EventMembership` 과 판정 API 의 꼴이 갈린다 |
| 판정 API 와 `storyline` 의 소속 레코드를 같은 이름으로 두고 패키지로 가른다 | 의존 관계에 있는 두 모듈(`storyline` → `evaluation`)에서 한 이름이 두 개체를 가리킨다 |
| 소속 레코드를 `StorylineMember` 로 | 표 `storyline_members` 와 말이 맞지만, 운영자 응답 `StorylineMembershipResponse` · 조회 `memberships` 와 말이 갈린다 |
| 판정 원본 표를 `article_storyline_judgements` 로(사건 소속 원본과 같은 꼴) | 짝 띠의 후보가 이웃 사건이라 이름이 틀린다 |
| 스냅숏 판을 2 로 올리고 1판 · 2판을 모두 읽는다 | 옛 키를 읽는 길이 코드에 남는다 |
| 스냅숏 판은 그대로 두고 키만 고친다 | 꼴이 바뀌었는데 판이 같아, 옮기기 전에 쓴 1판을 새 코드가 빈 줄로 잘못 읽는다 |
