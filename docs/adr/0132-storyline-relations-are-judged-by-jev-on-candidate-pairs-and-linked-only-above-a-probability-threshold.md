# ADR-0132: 스토리라인 관계는 후보 쌍을 Jev 에 물어 종류와 방향을 정하고 확률이 높은 것만 맺는다

- 상태: 승인. 부분 대체 — 운영자가 관계를 고치는 길([ADR-0137](0137-storyline-relations-are-not-edited-one-by-one-by-operators-and-are-tuned-through-relation-criteria.md))
- 날짜: 2026-11-13
- 근거: [이슈 #122](https://github.com/Theia0382/yozm-news/issues/122) — 시스템 스토리라인 쌍을 직접 판정한 세트로 구조 규칙 · Jev(문구와 입력의 변형) · 생성형 LLM(웹 검색 포함)을 견주고 리드와 정함
- 관련: [ADR-0121](0121-storyline-linking-scores-centroid-cosine-plus-idf-weighted-key-term-overlap-without-time-decay-jev-or-topic-tags.md)(결정 "Jev 판정과 주제 태그를 쓰지 않는다. `storyline` 은 `evaluation` · `tag` 에 의존하지 않는다" — 관계 판정에 한해 부분 대체), [ADR-0071](0071-event-membership-judged-from-article-ids-with-questions-built-in-evaluation.md) · [ADR-0072](0072-event-membership-question-wording-as-one-code-constant-with-its-version-in-raw-records.md)(판정 관문의 꼴 — 관계 판정에 같은 꼴을 쓴다, 보충), [ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)(관계의 표현과 구조), [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(생성형 LLM 없이 운영자 비용으로 잇는다), [ADR-0133](0133-storyline-evaluation-closes-system-containment-per-layer-view-and-measures-relations-by-direct-pair-judgement.md)(견준 방법), [ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)(근거 수치를 공개 문서에 적지 않는다)

## 맥락

- 스토리라인 관계는 명시 고리이고 넣을 때 구조 제약을 확인한다([ADR-0131](0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)). 어느 쌍에 어떤 관계를 넣을지 정하는 방법이 필요하다.
- 스토리라인은 생성형 LLM 없이 운영자 비용으로 잇는다([ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)). Jev 는 생성형이 아닌 판정 모델이고 호출마다 운영자 비용이다.
- 시스템 스토리라인 쌍을 직접 판정한 세트로 견줬다.
  - 스토리라인 표현끼리의 점수는 관계가 있는 쌍과 없는 쌍을 가르지 못했다. 사건을 함께 가졌는지는 무관한 쌍을 걸렀지만, 함께 가진 쌍에는 한 사안이 둘로 열린 것과 주제 묶음이 섞여 있었다.
  - Jev 는 관계가 있는지와 그 방향, 한 스토리라인이 한 사안인지를 잘 갈랐다. 포함과 이어짐, 포함과 한 사안이 갈라진 것의 경계는 흐렸다.
  - 생성형 LLM 둘과 웹 검색을 붙인 것은 Jev 보다 낫지 않았고, 같은 쌍을 같은 쪽으로 틀렸다. 그 쌍들은 사람의 판정도 갈린 경계였다.
  - 입력의 꼴(보일 사건 · 요약문의 자리 · 선택지 문구)을 바꿔도 차이는 순서나 날짜 표기를 바꿀 때의 흔들림 안이었다. 요약문을 빼면 나빠졌다.
- 판정은 외부 호출이라 DB 트랜잭션 밖에서 한다. 모든 Jev 판정은 `evaluation` 의 판정 공개 API 를 거친다([ADR-0071](0071-event-membership-judged-from-article-ids-with-questions-built-in-evaluation.md)).
- 잇기는 판정마다 모듈 잠금을 잡는다. 스토리라인은 사건이 붙을 때마다 자란다.

## 결정

- **후보 쌍을 Jev 에 물어 관계의 종류와 방향을 정한다.** 가장 높은 답이 포함이나 이어짐이고 그 확률이 기준 이상이며 두 스토리라인이 각각 한 사안이면 그 종류 · 방향의 관계를 맺는다. 확률이 같으면 맺지 않는 쪽이다. 포함의 상위와 이어짐의 앞은 Jev 가 답한 방향이다.
- **후보는 사건을 함께 가졌거나 표현 점수가 기준 이상인 쌍이다.** 점수는 잇기의 산식 그대로이고, 사건 수가 적은 스토리라인을 판정할 쪽으로 둔다. 두 스토리라인 모두 사건 수가 하한 이상이어야 한다. 사건 수 · 서술 · 순서는 직접 소속으로 본다.
- **한 사안이 둘로 열린 쌍은 관계로 맺지 않는다.** 선택지에 "같은 사안"을 두지 않고, 그런 쌍과 주제 묶음은 "해당 없음"에 든다.
- **포함과 이어짐의 경계.** 큰 흐름을 중심으로 도는 반응 · 알아볼 수 있는 부분(회차 · 단계) · 한 갈래는 포함이다. 자기 주체와 절차로 따로 굴러가는 다른 사안은 이어짐이다. 선택지 문구가 이 경계를 적는다.
- **판정은 `evaluation` 의 공개 API 하나다.** `storyline` 은 흐름 ID 와 서술 사건의 대표 기사 ID · 시각을 넘기고, `evaluation` 이 제목 · 요약문을 읽어 요청을 조립하고 판정 원본을 남긴다. `evaluation` 은 스토리라인을 모른다. 문구는 코드 상수 하나이고 판을 판정 원본에 남긴다.
- **`storyline` 은 기사 ID 를 넘기기만 한다.** `event` 에서 받은 대표 기사 ID 를 `evaluation` 에 넘기고 저장하지 않는다. `storyline` → `article` 의 의존은 그 ID 타입 하나다.
- **요청의 순서를 고정한다.** 먼저 시작한 스토리라인이 앞이다.
- **판정은 잇기와 따로 돈다.** 잇기가 소속이 바뀐 스토리라인을 관계 대기에 넣고, 따로 도는 반복 작업이 후보를 골라 트랜잭션 밖에서 묻고, 모듈 잠금을 잡은 짧은 트랜잭션에서 다시 확인해 반영한다. 물은 쌍은 기록한다.
- **다시 묻는다.** 물은 쌍은 작은 쪽의 사건 수가 물었을 때의 두 배 이상이 되면 다시 묻고 마지막 판정을 따른다 — 관계를 바꾸거나 지울 수 있다.
- **기준은 설정이고 값은 쌍 판정 세트로 골랐다.** 확률 기준 · 한 사안 기준 · 사건 수 하한 · 후보 점수 기준 · 시간 창이다. 측정 전 목표이고 운영하면서 다시 본다. 값의 정본은 [storyline Spec §7](../specs/storyline.md#7-설정)이고 근거 수치는 공개 문서에 적지 않는다([ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md)).

## 결과

- `storyline` 이 판정을 위해 `evaluation` 에 의존하고 `article` 의 ID 타입을 다룬다. 잇기는 여전히 Jev 를 쓰지 않는다.
- 관계 판정에 운영자 비용이 든다. 후보는 사건 수 하한 · 점수 기준 · 다시 묻기 규칙으로 묶인다. 실제 토큰은 운영에서 잰다.
> 부분 대체(둘째 문장) — [ADR-0137](0137-storyline-relations-are-not-edited-one-by-one-by-operators-and-are-tuned-through-relation-criteria.md). 지금 상태: [storyline Spec §1](../specs/storyline.md#1-책임과-범위)
- 이어짐의 일부는 포함으로 맺어지고, 관계의 적지 않은 수를 찾지 못한다. 운영자가 관계를 고치는 길이 생기기 전까지 틀린 종류로 남는다.
- 관계는 다시 물을 때 바뀌거나 사라질 수 있다. 관계를 가리키는 쪽이 생기면 다시 본다.
- 시간 창보다 멀리 떨어진 두 흐름은 사건을 함께 갖지 않는 한 묻지 않는다. 몇 주 뒤에 이어진 흐름을 찾는지는 재지 못했다.
- 문구 · 입력 · 기준을 같은 쌍으로 고르고 쟀다. 다른 기간의 쌍에서의 품질은 재지 않았다.
- 주제 묶음인 스토리라인은 "한 사안" 기준으로 관계에서 거른다. 잇기가 그런 스토리라인을 만드는 것은 그대로다.
- 운영하면서 볼 것은 [미결 사항](../open-questions.md)에 있다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 사건을 함께 가진 쌍을 구조 규칙(기간 · 사건 수)으로만 판정한다 | 고른 쌍의 다수가 주제 묶음이나 한 사안이 갈라진 것이었고, 이어짐을 찾지 못한다 |
| 스토리라인 표현끼리의 점수가 기준 이상이면 관계로 본다 | 점수가 관계의 종류도, 관계가 있는지도 가르지 못했다 |
| 생성형 LLM 에 묻는다 | 품질이 Jev 와 같거나 낮았고 순서에 더 흔들렸다. 비용이 크고, 생성형 LLM 없이 잇는다는 결정([ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md))을 바꿔야 한다 |
| 생성형 LLM 이 웹을 검색해 맥락을 알아오게 한다 | 답이 바뀌기만 하고 낫지 않았다. 틀린 쌍은 지식이 아니라 경계가 갈리는 쌍이었다. 검색 결과는 신뢰할 수 없는 입력이고 비용이 한 자릿수 더 든다 |
| 시스템은 후보만 내고 운영자가 모두 정한다 | 관계를 판정한다는 목적을 이루지 못하고 운영자 손이 든다 |
| 종류마다 예 / 아니오로 따로 묻는다 | 답이 서로 겹쳐 이어짐과 한 사안이 갈라진 것을 고르지 못했다 |
| 종류와 방향을 두 질문으로 나눈다 | 나빠졌다 |
| 선택지에 "같은 사안"을 둔다 | 한 사안이 갈라진 쌍의 적지 않은 수가 포함으로 답해졌다. 그런 쌍은 관계로 다루지 않는다 |
| 후보를 점수 상위 몇 개로 고른다 | 사건을 함께 가졌거나 점수가 기준 이상인 쌍이 이미 적었다 |
| 쌍마다 한 번만 묻는다 | 스토리라인이 작을 때의 판정이 굳는다 |
| 다시 묻되 맺은 관계는 지우지 않는다 | 틀린 관계가 남는다 |
| `event` 가 서술을 만들어 `storyline` 이 열어 보지 않고 넘긴다 | `event` 가 관계 판정의 서술 꼴을 알게 된다 |
| 관계 판정을 잇기 회차 뒤에 같은 반복 작업에서 돈다 | 판정이 느릴 때 잇기가 밀린다 |
