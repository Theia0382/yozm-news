# ADR-0331: 개발 용어는 코드 이름 하나의 뜻만 갖고, 여러 뜻으로 쓰이던 말은 뜻마다 다른 말로 바꾼다

- 상태: 승인
- 날짜: 2026-11-25
- 근거: [이슈 #281](https://github.com/Theia0382/yozm-news/issues/281) — 기점 `1c36617` 의 `docs/`(ADR · 동결 자료 · 조사 자료 제외)에서 "갈래"(299회) · "묶음"(337회) · "정리"(371회) · "덮다" · "판"이 뜻 여럿으로 쓰였고, "꼭대기"(410회) · "헤드"(203회)는 `overview` §8 에 정의가 없었다. 구현 커밋 `c804fd3`
- 관련: [ADR-0326](0326-development-names-call-a-shared-storyline-storyline-a-user-storyline-user-storyline-and-a-judgement-candidate-of-unknown-kind-candidate.md)(보충 — "한 개체를 개발 이름 하나로 부른다"를 한 말에 한 뜻으로 넓힌다)

## 맥락

- 개발 이름은 한국어와 코드가 같은 말이다(ADR-0326). 그런데 한 한국어가 코드 이름 여럿의 뜻을 덮었다. "갈래"는 실패 종류(`EvaluatorFailureKind`) · 리포트 섹션 종류(`ReportSectionKind`) · 포함의 하위 스토리라인을 함께 가리켰고, "정리"는 사용자 스토리라인의 정리(`UserStorylineSummary`)와 지우는 작업(`CandidateCleanup` 등)을 함께 가리켰다.
- 정의된 말이 일반어로도 쓰여, 독자가 문장마다 어느 뜻인지 가려야 했다.

## 결정

- **개발 용어는 코드 이름 하나의 뜻으로만 쓴다.** 같은 말을 다른 뜻으로 쓰지 않고, 정의된 말과 겹치는 일반어도 피한다. 규칙의 정본은 [`docs/README.md` §4](../README.md#4-작성-규칙)다.
- **여러 뜻으로 쓰이던 말은 아래처럼 바꾼다.** 남기는 뜻은 그 말의 코드 이름 하나다.

| 말 | 남기는 뜻(코드 이름) | 다른 뜻 → 바꾼 말 |
|---|---|---|
| 꼭대기 | 포함 나무에서 상위가 없는 스토리라인(`top`) | 다른 뜻 없음. `overview` §8 에 정의를 둔다 |
| 헤드 | 사용자 스토리라인의 끝 꼭대기(`head`) | 다른 뜻 없음. 정의는 `overview` §8 하나다 |
| 묶음 | 사용자 스토리라인 하나의 꼭대기 집합(`group`) | 질문 묶음 → 태그 질문(`TagQuestions`), 텍스트 묶음 → 텍스트 목록 |
| | | 기간 집계의 입력인 사건 묶음 → 사건 집합(`EventDirectory.tallies`), 스토리라인 묶음 → 스토리라인 집합(`StorylineDirectory.tallies`) |
| | | 상수 묶음 → 상수 집합(`InterestConstants`), 사건의 "공유 묶음" → 공유 기사 모음 |
| | | 회차의 뜻 → 회차, 쪽 나누기의 뜻 → 쪽 |
| | | 평가 지표의 묶음(B-cubed 의 군집) → 군집, 여러 일을 모은 기사 → 모음 기사, 쌍 판정의 주제 묶음 → 주제 모음 |
| | | 정규식 묶음(family) → 정규식 집합 |
| 갈래 | 개발 용어로 남기지 않는다 | 실패 갈래 → 실패 종류(`*FailureKind`), 생성 실패의 갈래 → 실패 원인(`GenerationFailureCause`), 리포트의 갈래 → 섹션 종류(`ReportSectionKind`) |
| | | 포함의 갈래 → 하위 스토리라인, 갈라진 사용자 스토리라인 → 사용자 스토리라인, 그 밖의 일반어 → 종류 · 경우 |
| | | 리포트의 갈래 섹션 → 종류 섹션, 출력의 갈래 칸 → 섹션 칸 |
| 정리 | 사용자 스토리라인의 정리(`UserStorylineSummary`) | 지우는 작업(`CandidateCleanup` · `NoticeCleanup` · `LoginFailureCleanup` · `RequestCountCleanup` · `*Retention`) → 지우기, 사건의 정리 작업(`CleanupRound`) → 사건 정비, 기사 표현 정리 → 기사 표현 지우기 |
| | | 피드 텍스트를 다듬는 일(`TextCleaner`) → 보정, 유지 작업 일반을 뜻하는 정리 작업 → 정비 작업 |
| 덮다 | 범위가 포함한다(`covered` · `covers`) | 같은 날짜의 덮인 다이제스트 → 가려진 다이제스트 |
| 판 | 알림의 판(`revision`) | 문구 판 → 문구 버전(`wordingVersion`), 스냅숏 판 → 스냅숏 버전(`ReportSnapshot.VERSION`), 라벨 기준 판 → 라벨 기준 버전(`LABEL_RULE_VERSION`) |
| | | 저장 꼴의 앞 바이트 → 형식 바이트(`EnvelopeCipher.FORMAT`) |
| | | 기사의 판본을 뜻하는 판 → 판본, 앱 릴리스를 뜻하는 판 → 버전 |
| | | 절 제목 "라벨 기준 N판"은 평가 도구 시험(`tools/event-eval/test_label_rule.py`)이 읽는 표지라 그대로 둔다 |
| 회차 | 반복 작업이 한 번 돌 때 하는 일(`*Round`) | 되풀이되는 행사의 한 번(시즌 · 대회) → 차례 |
| 흐름 | 화면 이름. 독자 · 운영자 화면이 스토리라인을 부르는 말이다([화면 Spec §2](../specs/web.md#2-용어)) | 처리 과정이라는 일반어 → 과정 · 순서 |
| 요청 제한 | `account` 의 요청 제한(`RequestLimiter`) | 공급자 · 외부 서비스의 호출 한도 → 호출 한도 |
| | | 피드 요청의 마감 · 크기 상한(`FetchLimits`) → 수집 마감 · 크기 상한 |
| 지우기 | 보존 기한이 지난 행을 반복 작업이 지우는 것(`CandidateCleanup` · `NoticeCleanup` · `LoginFailureCleanup` · `RequestCountCleanup` · `*Retention`) | 운영자가 소스 · 피드를 삭제하고 그것만 가진 기사까지 지우는 것(`SourceRemoval` · `ArticleRemoval`) → 삭제 |
| 지금 사건 | 사건 하나를 병합을 따라 푼 사건(`EventDirectory.currentOf`) | 기사가 지금 든 사건 → 배정의 사건, 사용자 스토리라인의 정리가 재료에서 읽은 사건 → 재료 사건 |

- **화면 문구 인용은 바꾸지 않는다.** 화면 이름은 [화면 Spec §2](../specs/web.md#2-용어)가 갖는다.

## 결과

- 현행 문서는 바꾼 말만 쓴다. 바꾸기 전의 말로 쓰인 ADR 본문은 위 표로 읽는다.
- 코드 이름 `Cleanup` 은 지우기와 사건 정비 두 뜻에 쓰인다. 문서는 뜻에 따라 두 말로 나누고 코드는 고치지 않는다 — 이 두 자리에서는 한국어와 코드 이름이 1:1 이 아니다.
- 화면 문구 "갈래"는 개인 흐름 화면에서는 사용자 스토리라인의 꼭대기를, 공용 타임라인에서는 하위 스토리라인에서 센 사건 표시를 뜻한다. 화면 Spec §2 가 두 행으로 적는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 말은 그대로 두고 용어 표에 뜻마다 행을 둔다 | 문장에서 어느 뜻인지 독자가 가려야 한다 |
| 코드 이름을 한국어에 맞춰 바꾼다 | 이름 정리의 범위가 문서에서 코드 · DB · 화면용 API 로 커진다 |
