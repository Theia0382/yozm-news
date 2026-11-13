# ADR-0071: 사건 소속 판정은 판정할 기사와 후보 사건의 서술 기사를 ID 로 받아 `evaluation` 이 질문을 만들고, 판정 원본에는 ID 만 남긴다

- 상태: 승인. 부분 대체 — 공개 값의 범위([ADR-0094](0094-event-membership-judgements-identified-by-id-read-by-operators-and-referenced-by-the-records-that-apply-them.md))
- 날짜: 2026-11-05
- 근거: [이슈 #96](https://github.com/Theia0382/yozm-news/issues/96) — 사건 소속 판정을 `ArticleEvaluator` 연산과 `evaluation` 공개 API 로 만들며 정한 계약. 구현 `a84cc1a`
- 관련: [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)(사건 묶기 — 미뤄 둔 인자 · 반환 타입의 모양을 이 ADR 이 정한다), [ADR-0060](0060-article-deletion-announced-by-removed-article-ids-and-followed-by-referencing-modules.md)(지운 기사 ID 로 따라 지우기)

## 맥락

- ADR-0026 은 애매한 구간만 Jev 에 "이 기사는 사건 X를 다루는가"를 후보 사건 여럿을 묶어 묻고, 그 인자 · 반환 타입은 `evaluation` 이 소유한다고 정했다. 무엇을 넘기고 무엇을 받는지는 정하지 않았다.
- 판정을 부를 `event` 모듈은 아직 없다. 공개 계약이 소비자보다 먼저 정해진다.
- 후보 사건은 사건 중심에 가까운 기사 몇 개의 제목으로 서술한다(사건 묶기 파이프라인). 그 기사는 소스 삭제로 지워질 수 있고, 지운 기사의 원문은 다른 모듈에 남지 않아야 한다(ADR-0060).
- 판정 원본(Jev 응답)은 기록한다(ADR-0026). 원본 응답 · 모델 · 토큰은 판정을 받아들이는 쪽이 쓰지 않는다.

## 결정

- **판정할 기사와 서술 기사를 기사 ID 로 받는다.** `evaluation` 이 `article` 의 공개 조회로 제목 · 요약을 읽어 질문 · state 를 만든다. 서술 기사를 고르는 일은 부르는 쪽(사건 중심을 아는 쪽)이 한다.
- **판정 원본에는 서술 기사 ID 와 확률만 남긴다.** 서술 제목 텍스트는 남기지 않는다. 판정 원본은 판정할 기사 ID 로만 지운다 — 서술로만 쓰인 기사가 지워져도 다른 기사의 판정 원본은 남는다.
- **질문은 후보 사건마다 Yes/No 하나다.** ADR-0026 의 문면 그대로다 — 확률은 후보마다 독립이고, 받아들일지(기준 · 최댓값)는 부르는 쪽이 정한다.
> 부분 대체(둘째 · 셋째 문장) — [ADR-0094](0094-event-membership-judgements-identified-by-id-read-by-operators-and-referenced-by-the-records-that-apply-them.md). 지금 상태: [`evaluation` §3](../specs/evaluation.md#3-공개-인터페이스)(evaluation-R38 · R39)
- **공개 타입과 포트 타입을 나눈다.** 공개 API 는 사건 ID · 기사 ID · 확률 · 실패 갈래만 주고받는다. 원본 응답 · 모델 버전 · 입력 토큰은 포트 결과에만 있고 `evaluation` 이 기록으로만 쓴다. 실패 갈래 enum 은 공개 패키지에 하나다.

## 결과

- 질문 · state 의 모양이 `evaluation` 한 곳에 있다. 판정 원본의 기사 ID 와 실제로 물은 입력이 어긋나지 않는다.
- 소스를 지워도 판정 원본에 그 기사의 제목이 남지 않는다. 원본의 서술 기사 ID 로 무엇을 물었는지 되짚을 수 있다(그 기사가 남아 있는 동안).
- 판정마다 `article` 조회가 한 번 늘어난다. 판정 중에 지워진 기사 · 서술 기사를 다루는 규칙이 생긴다 — 판정할 기사가 없으면 판정하지 않고, 서술이 모두 지워진 후보는 묻지 않는다.
- 서술을 제목 말고 다른 것(고유명사 등)으로 바꾸려면 공개 계약이 바뀐다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 판정할 기사의 제목 · 요약 텍스트를 받는다 | 판정 원본의 기사 ID 와 텍스트가 어긋날 수 있고, state 를 무엇으로 만드는지가 부르는 쪽으로 샌다 |
| 서술을 제목 텍스트로 받는다 | 원본에 텍스트를 남기면 지운 기사의 제목이 남고, 남기지 않으면 무엇을 물었는지 원본으로 알 수 없다 |
| 후보 전체를 선택 질문 하나로 묻는다(후보들 + "어느 것도 아니다") | 분포 합이 1 이라 확률이 후보 수에 따라 묽어져 고정 기준과 맞지 않는다. ADR-0026 의 Yes/No 문면과 다르다 |
| 포트 결과를 그대로 공개한다 | 원본 응답 · 토큰이 부르는 쪽으로 새고, 판정 원본을 누가 남기는지가 흐려진다 |

## 후속 기록

- 2026-11-11: 결정 절 "판정할 기사와 서술 기사를 기사 ID 로 받는다"의 꼴을 태그 판정으로 넓힘 — 기사 ID 를 받아 `evaluation` 이 기사 · 피드를 읽는다 — [ADR-0112](0112-tags-split-into-a-tag-module-and-evaluation-is-the-model-gateway-that-assembles-calls-and-records-each-judgement.md) · [evaluation Spec "태그 판정"](../specs/evaluation.md#태그-판정) evaluation-R40
- 2026-11-13: 결정 절 "판정할 기사와 서술 기사를 기사 ID 로 받는다"의 꼴을 스토리라인 관계 판정으로 넓힘 — 흐름 ID 와 서술 사건의 대표 기사 ID 를 받아 `evaluation` 이 제목 · 요약문을 읽는다 — [ADR-0132](0132-storyline-relations-are-judged-by-jev-on-candidate-pairs-and-linked-only-above-a-probability-threshold.md) · [evaluation Spec 관계 판정](../specs/evaluation.md#관계-판정)
