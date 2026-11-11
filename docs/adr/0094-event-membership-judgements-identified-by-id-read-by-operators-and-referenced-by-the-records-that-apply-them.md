# ADR-0094: 사건 소속 판정 원본은 ID 로 식별되어 운영자 조회로 읽히고, 판정을 반영한 `event` 기록이 그 ID 를 가리켜 판정의 쓰임과 결과를 잇는다

- 상태: 승인
- 날짜: 2026-11-07
- 근거: [이슈 #101](https://github.com/Theia0382/yozm-news/issues/101) — 운영자 화면의 "애매한 판정 기록(후보 사건 · 확률 · 결과 · 토큰)"을 만들며 리드와 정하고 구현(`3528f8b` · `ae37a1a` · `c5c3c0d`)
- 관련: [ADR-0071](0071-event-membership-judged-from-article-ids-with-questions-built-in-evaluation.md)(결정 절 "공개 타입과 포트 타입을 나눈다"의 둘째 · 셋째 문장을 부분 대체한다 — 넓힘), [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)(판정 원본 기록), [ADR-0045](0045-admin-module-serves-operator-api-over-module-public-operation-apis.md)(운영자 API 는 모듈 공개 API 위에)

## 맥락

- 사건 소속 판정 원본은 `evaluation` 이 성공한 판정마다 한 행을 남긴다 — 판정할 기사 · 물은 후보와 확률 · 모델 버전 · 입력 토큰([ADR-0071](0071-event-membership-judged-from-article-ids-with-questions-built-in-evaluation.md)). 읽는 길은 없었다.
- 사건 묶기는 두 갈래에서 같은 공개 API 로 판정을 묻는다 — 배정(판정할 기사가 배정 대기의 기사)과 병합(판정할 기사가 바뀐 사건의 서술 기사 하나). 원본에는 무엇을 위해 물었는지가 없다. 기사의 지금 배정을 "결과"로 맞붙이면 병합 판정의 결과는 판정과 무관한 값이 된다.
- 판정에 성공한 뒤 쓰기가 되돌려지면(붙일 사건이 그새 닫힘 등) 원본은 남고 그 판정이 정한 배정은 없다 — 다음 회차가 다시 묻는다.
- 운영자는 판정의 분포(확률 기준 근처에 몇 건이 몰렸나)와 비용(입력 토큰)을 보고 사건 배정 기준을 조정한다.

## 결정

- **판정 결과가 남긴 판정 원본의 ID 를 낸다.** `MembershipJudgement.Judged` 가 원본 ID(`EventJudgementId` — `evaluation` 소유 값)를 싣는다. 평가기를 부르지 않은 빈 판정은 ID 가 없다.
- **판정을 반영한 `event` 기록이 원본 ID 를 남긴다.** 배정 회차가 판정을 반영해 쓴 배정 행, 병합 회차가 새로 남긴 병합 판정 기록에 원본 ID 를 둔다. 무엇을 위해 물었는지는 어느 기록이 가리키는지로 `event` 가 안다 — `evaluation` 에 `event` 의 처리 갈래를 들이지 않는다.
- **가리키는 기록이 없는 원본은 "반영 안 됨"이다.** 쓰기를 되돌린 판정, 기록이 함께 지워진 판정, 쌍의 기록이 이미 있던 판정이 여기 든다.
- **재판정 · 병합은 원본 ID 를 바꾸지 않는다.** 결과는 조회 시점의 값이다 — 배정이면 그 기사의 지금 사건, 병합이면 쌍이 지금 같은 사건인지.
- **판정 원본은 운영자 조회로 읽힌다.** `evaluation` 의 공개 조회(`EventJudgementLog`)가 기간의 원본을 모델 버전 · 입력 토큰과 함께 낸다(원본 응답은 내지 않는다). 쓰임과 결과는 `event` 의 공개 조회(`EventReview.judgementUses`)가 원본 ID 로 낸다. `admin` 이 둘을 원본 ID 로 맞붙인다.

## 결과

- 판정 기록 한 행에서 후보 · 확률 · 토큰과 그 판정의 쓰임 · 결과가 정확히 이어진다. 되돌린 판정도 구별된다.
- 의존 방향은 그대로다(`event` → `evaluation`). `evaluation` 은 사건 ID 의 뜻도, 판정의 쓰임도 모른다.
- 사건 배정 쓰기 경로와 `event` 스키마(컬럼 둘)가 바뀌었다. 이 결정 전의 원본은 가리키는 기록이 없어 "반영 안 됨"으로 보인다.
- 판정 원본은 보존 기간(180일)이 있고 배정 · 병합 판정 기록은 무기한이라, 원본이 지워져도 기록의 ID 는 남는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 구별하지 않고 판정할 기사의 지금 배정을 결과로 맞붙인다 | 병합 판정 행의 결과가 판정과 무관하다 — 운영자를 오도한다 |
| 판정 원본에 용도(배정 · 병합)를 남긴다 | `evaluation` 이 `event` 의 처리 갈래를 알게 된다(모듈은 자기 지식만). 되돌린 배정 판정에도 나중 판정의 배정이 결과로 보인다 |
| `event` 의 저장 후보 · 병합 판정 기록을 원천으로 하고 토큰만 판정 원본에서 찾는다 | 한 기사에 원본이 여럿일 때 토큰이 어느 판정의 것인지 정해지지 않는다. 병합 판정 하나가 쌍 여럿으로 흩어진다 |

## 후속 기록

- 2026-11-11: 결정 절 "판정을 반영한 `event` 기록이 원본 ID 를 남긴다"의 꼴을 태그 판정으로 넓힘 — `tag` 의 평가 행이 태그 판정 원본 ID 를 가리킨다 — [ADR-0112](0112-tags-split-into-a-tag-module-and-evaluation-is-the-model-gateway-that-assembles-calls-and-records-each-judgement.md) · [tag Spec §5](../specs/tag.md#5-규칙과-불변식) tag-R15 · [evaluation Spec "태그 판정"](../specs/evaluation.md#태그-판정) evaluation-R41
