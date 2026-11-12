# ADR-0072: 사건 소속 질문 문구는 코드 상수 하나로 두고 판정 원본에 문구 판을 남긴다

- 상태: 승인
- 날짜: 2026-11-05
- 근거: [이슈 #96](https://github.com/Theia0382/yozm-news/issues/96) — 질문 문구를 정하고 실제 Jev 로 표본을 확인. 구현 `e5e8091`
- 관련: [ADR-0054](0054-tag-set-seeded-from-default-yaml-on-first-start-with-immutable-versions-and-question-templates.md)(태그 질문은 체계 버전에서 읽는다 — 그 범위 밖을 이 ADR 이 정한다), [ADR-0071](0071-event-membership-judged-from-article-ids-with-questions-built-in-evaluation.md)

## 맥락

- 태그 질문은 코드에 두지 않고 체계 버전에서 읽는다(ADR-0054). 체계를 바꾸려면 새 버전을 만들어 활성화한다.
- 사건 소속 판정의 질문은 후보마다 같은 문구 하나에 서술 제목을 붙인 것이다. 태그 체계와 생명주기가 다르다 — 문구를 바꿔도 태그를 다시 평가할 일이 없다.
- 체계 파일의 최상위 키는 모두 필수다. 필수 키로 더하면 운영자가 가진 원문이 활성화되지 않고, 선택 키로 더하면 빠졌을 때의 기본 문구가 코드에 다시 생긴다.
- 문구를 고칠 근거(평가 세트 측정)는 아직 없다.
- 실제 Jev 표본(공개 피드 기사 5건 × 후보 3 — 같은 사건 1 · 다른 사건 2)에서 같은 사건 확률의 최솟값과 다른 사건 확률의 최댓값의 차가 한국어 문구 0.29, 영어 문구 0.38 이었다. 품질 측정이 아니다.

## 결정

- **질문 문구는 `evaluation` 코드의 상수 하나로 둔다.** 포트 구현 밖의 값이고 포트 입력으로 넘긴다 — 포트 구현은 문구를 갖지 않는다.
- **문구 판(버전 문자열)을 판정 원본마다 남긴다.** 문구나 질문 조립 모양을 바꾸면 판을 올린다.
- 첫 문구는 영어 문장이다(판 `membership-1`) — 표본에서 같은 사건과 다른 사건의 확률이 더 벌어졌다.

## 결과

- 문구를 바꾸려면 새 릴리스가 필요하다. 운영자는 문구를 고치지 못한다.
- 태그 체계 · 재평가와 엮이지 않는다. 판을 남겨 두어 문구를 바꾼 전후의 판정을 원본으로 가르고, 나중에 체계 파일이나 설정으로 옮겨도 이전 판정과 구별된다.
- 문구의 품질은 평가 세트로 잰다(open-questions #28).

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 태그 체계 파일에 사건 소속 질문 키를 더한다 | 문구 하나에 새 체계 버전이 필요하고, 필수 키면 운영자 원문이 활성화되지 않으며 선택 키면 기본 문구가 코드에 다시 생긴다 |
| 설정(환경 변수)으로 둔다 | 여러 줄 문구를 다루기 어렵고, 기록이 없으면 언제 바뀌었는지 모른다 |
| 한국어 문구 | 표본에서 같은 사건과 다른 사건의 분리가 영어보다 작았다 |

## 후속 기록

- 2026-11-09: 결정 절 "첫 문구는 영어 문장이다(판 `membership-1`)"의 다음 판 — [ADR-0102](0102-an-event-is-the-reports-of-one-happening-narrowed-by-the-event-membership-question-wording.md) · [evaluation Spec 사건 소속 판정](../specs/evaluation.md#사건-소속-판정) evaluation-R37
- 2026-11-13: 결정 절의 "문구는 코드 상수 하나이고 판을 판정 원본에 남긴다"를 스토리라인 관계 판정의 문구에도 쓴다 — [ADR-0132](0132-storyline-relations-are-judged-by-jev-on-candidate-pairs-and-linked-only-above-a-probability-threshold.md) · [evaluation Spec 관계 판정](../specs/evaluation.md#관계-판정)
