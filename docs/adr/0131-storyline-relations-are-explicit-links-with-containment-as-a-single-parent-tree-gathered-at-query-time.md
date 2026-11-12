# ADR-0131: 스토리라인 사이 관계는 명시 고리로 두고, 포함은 상위 하나의 나무이며 조회 때 하위를 따라 모은다

- 상태: 승인
- 날짜: 2026-11-13
- 근거: [이슈 #122](https://github.com/Theia0382/yozm-news/issues/122) — 지금 잇기가 낸 스토리라인에서 소속의 겹침과 스토리라인 쌍을 조사하고, 시스템 스토리라인 쌍을 직접 판정해 리드와 정함
- 관련: [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(결정 "관계를 소속에서 끌어낼지 명시 고리로 둘지, 판정 방법, 데이터는 스토리라인을 구현할 때 정한다"와 검토한 대안 "포함을 상위 스토리라인(깊이 2)으로 지금 정한다" — 보충), [ADR-0106](0106-provisional-storyline-linking-opens-only-with-a-partner-event-and-adds-the-single-best-storyline-without-removal.md)(결정의 "관계를 소속에서 끌어내기로 하면 다시 본다" — 보충), [ADR-0108](0108-storylines-with-the-same-events-merge-and-a-storyline-left-with-one-event-merges-or-dissolves.md)(결과 "표현만 비슷한 두 스토리라인과 사건 둘 이상의 포함은 남는다" — 보충), [ADR-0132](0132-storyline-relations-are-judged-by-jev-on-candidate-pairs-and-linked-only-above-a-probability-threshold.md)(관계를 판정하는 방법), [ADR-0111](0111-storyline-gold-links-by-matter-with-mixed-events-on-their-central-happening-and-events-without-a-happening-following-their-matter.md)(정답 쪽의 포함 — 위로 닫는다)

## 맥락

- 스토리라인 사이에는 포함(큰 흐름과 그 갈래)과 이어짐(앞 흐름이 뒤 흐름을 낳음)이 있다([ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)). 그것을 나타내는 방법은 정해지지 않았다.
- 잇기는 사건을 기준 이상인 스토리라인 가운데 가장 높은 하나에만 붙인다([ADR-0106](0106-provisional-storyline-linking-opens-only-with-a-partner-event-and-adds-the-single-best-storyline-without-removal.md) · [ADR-0121](0121-storyline-linking-scores-centroid-cosine-plus-idf-weighted-key-term-overlap-without-time-decay-jev-or-topic-tags.md)). 그래서 스토리라인 여럿에 든 사건은 드물고, 한 스토리라인의 사건이 다른 스토리라인에 모두 드는 일은 거의 없다. 사건을 함께 가진 스토리라인 쌍은 대부분 한 사안이 둘로 열린 것이었다.
- 이어짐에는 방향과 인과가 있다. 구성 사건의 집합에는 그것이 없다.
- 정답 쪽의 포함은 위로 닫는다 — 하위 스토리라인의 구성 사건은 상위에도 든다([ADR-0111](0111-storyline-gold-links-by-matter-with-mixed-events-on-their-central-happening-and-events-without-a-happening-following-their-matter.md)).
- 스토리라인의 표현(중심 · 핵심 말)은 구성 사건에서 다시 정해 저장하고, 잇기는 그 표현과 새 사건을 견준다. 큰 스토리라인일수록 흔한 말이 표현을 채워 사건을 끌어들이기 쉽다([ADR-0121](0121-storyline-linking-scores-centroid-cosine-plus-idf-weighted-key-term-overlap-without-time-decay-jev-or-topic-tags.md)).
- 스토리라인 ID 는 구독 · 타임라인 · 리포트 인용이 가리킨다. 스토리라인은 합쳐지거나 지워진다([ADR-0108](0108-storylines-with-the-same-events-merge-and-a-storyline-left-with-one-event-merges-or-dissolves.md)).

## 결정

- **관계는 명시 고리다.** 포함과 이어짐 모두 스토리라인 둘과 종류 · 방향을 가진 한 행이다. 소속에서 끌어내지 않는다. 사건을 함께 가졌는지는 관계의 정의가 아니라 판정할 쌍을 고르는 계기다([ADR-0132](0132-storyline-relations-are-judged-by-jev-on-candidate-pairs-and-linked-only-above-a-probability-threshold.md)).
- **관계의 종류는 포함과 이어짐 둘이다.** 같은 원인에서 나온 나란한 흐름은 종류를 더하지 않고, 같은 상위 아래의 하위들로 나타낸다.
- **포함은 나무다.** 스토리라인은 상위를 하나까지 갖고, 깊이는 제한하지 않는다. 이어짐은 앞 · 뒤가 여럿일 수 있다.
- **구조 제약.** 스토리라인은 자기 자신과 관계를 갖지 않는다. 한 쌍의 관계는 하나다. 포함끼리 · 이어짐끼리 순환을 이루지 않는다. 방향은 관계를 넣는 쪽이 정하고, 순환을 이루거나 하위에 둘째 상위를 주는 고리는 넣을 때 거부한다.
- **소속은 직접 소속만 저장하고, 포함은 조회 때 따라 모은다.** 공개 조회에서 스토리라인의 사건 · 사건 수 · 기간 집계 · 큰 판정은 하위의 사건까지 세고, 사건이 든 스토리라인은 직접 든 것과 그 상위를 함께 낸다 — 직접인지 물려받은 것인지 표시한다. 잇기가 견주는 스토리라인의 표현은 직접 소속으로 정한다.
- **상위와 하위를 가리지 않고 낸다.** 목록과 큰 판정은 하위가 크면 상위도 함께 내고, 행에 상위를 싣는다. 무엇을 고를지는 쓰는 쪽이 정한다.
- **관계는 스토리라인의 병합과 삭제를 따른다.** 스토리라인이 합쳐지면 그 관계를 남는 쪽으로 옮기고, 지워지면 함께 지운다. 옮긴 관계가 구조 제약을 어기면 남는 쪽이 이미 가진 관계를 남긴다.

## 결과

- 사건이 여러 스토리라인에 드는 길이 하나 는다 — 갈래에 든 사건은 그 큰 흐름에도 든 것으로 읽힌다. 소속 행은 늘지 않는다.
- 공개 조회의 스토리라인 값(사건 수 · 기사 수 · 시각 · 상태)은 포함이 있으면 저장된 표현과 다르다. 목록의 순서는 저장된 마지막 기사 시각이라 행의 값과 어긋날 수 있다.
- 조회마다 포함을 따라 내려간다. 비용은 재지 않았다.
- 큰 흐름과 그 갈래가 큰 판정에 함께 나온다. 다이제스트 · 리포트 · 화면이 중복을 다뤄야 한다.
- 나란한 흐름은 그것들을 묶는 상위가 스토리라인으로 있을 때만 드러난다. 그런 상위를 여는 방법은 정하지 않았다 — [미결 사항](../open-questions.md).
- 한 사안이 둘로 열린 스토리라인은 그대로 남는다 — 표현 유사도로 합치지 않는다는 결정([ADR-0108](0108-storylines-with-the-same-events-merge-and-a-storyline-left-with-one-event-merges-or-dissolves.md))은 그대로다.
- 규칙과 데이터의 정본은 [storyline Spec](../specs/storyline.md)에 있다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 포함 · 이어짐을 소속에서 끌어낸다 | 잇기가 사건을 한 스토리라인에만 붙여 재료가 없고, 겹침은 한 사안이 갈라진 표지였다. 이어짐의 방향을 담지 못한다. 살리려면 사건을 여러 스토리라인에 붙이도록 잇기를 바꿔야 하는데 그 꼴은 정밀도를 내렸다([ADR-0121](0121-storyline-linking-scores-centroid-cosine-plus-idf-weighted-key-term-overlap-without-time-decay-jev-or-topic-tags.md)) |
| 포함만 소속에서 끌어내고 이어짐은 명시 고리로 둔다 | 포함 쪽 문제가 그대로이고 두 종류의 길이 갈린다 |
| 포함을 두 층으로 제한한다 | 갈래의 갈래를 담지 못한다. 정답 쪽은 깊이를 제한하지 않는다 |
| 포함의 상위를 여럿 허용한다 | 합계 · 화면이 복잡해지고, 필요한 예가 없었다 |
| 하위의 사건을 상위의 소속 행으로도 저장한다 | 직접 소속과 포함에서 계산되는 값의 사본이라 관계 · 병합 · 삭제 때마다 맞춰야 하고 어긋날 수 있다. 상위의 표현이 큰 흐름 전체가 되어 새 사건이 갈래보다 상위에 붙기 쉬워진다 |
| 포함을 조회에 반영하지 않는다(고리로만 보인다) | 큰 흐름이 갈래의 보도량을 세지 못해 큰 판정 · 합계가 정답의 뜻과 어긋난다 |
| 나란한 흐름을 세 번째 종류로 둔다 | 관계의 종류를 둘로 둔 결정([ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md))을 바꾸고, 그런 쌍을 찾을 방법이 없다 |
| 목록과 큰 판정에서 최상위만 낸다 | 큰 갈래가 큰 상위에 가려진다. 쓰는 쪽이 생기기 전에 고르는 규칙을 정하게 된다 |
| 방향을 첫 기사 시각으로 늘 다시 정한다 | 앞선 사건이 붙으면 저장한 고리의 방향이 뒤집힌다 |
| 순환을 DB 제약으로 막는다 | 재귀 제약이라 트리거가 필요하다. 관계를 넣는 판정이 한 번에 하나라 넣을 때의 확인으로 충분하다 |
