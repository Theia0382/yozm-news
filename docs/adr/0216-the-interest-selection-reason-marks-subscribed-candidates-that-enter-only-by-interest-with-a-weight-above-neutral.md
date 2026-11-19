# ADR-0216: 선정 이유 `INTEREST` 는 중립 순위로는 들지 못하고 가중치가 1 보다 큰 구독 후보다

- 상태: 승인. 부분 대체 — 구독 후보 기사로 두 순위를 견주는 단계와 단위([ADR-0260](0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md))
- 날짜: 2026-11-18
- 근거: [이슈 #187](https://github.com/Theia0382/yozm-news/issues/187) — 후보 선별의 순수 단계가 순위 순서로만 기사를 받는 것을 대조하고, 선정 이유의 뜻을 리드와 정함. 구현 커밋 `ff3ba46` · `1487ffe`
- 관련: [ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)(결정 "기사마다 선정 이유 기록 · 표시" — 보충), [ADR-0210](0210-candidate-selection-cuts-to-the-input-token-budget-with-one-estimate-the-prompt-follows.md)(입력 토큰 예산으로 자른다)

## 맥락

- 선정 이유는 다이제스트가 인용한 기사에 남고 화면에 보인다. 로드맵은 선정 이유별 반응(좋아요 / 싫어요)을 잰다.
- 관심 주제의 기사는 관심도가 없어도 구독 후보로 들 수 있다. "가중치가 1 보다 큰 구독 후보"를 모두 관심도로 들어온 기사로 부르면 구독 주제로 들어온 기사와 갈리지 않는다.
- 관심도는 순위만 바꾼다 — 같은 재료로 중립 순위의 결과를 만들 수 있다. 사건과 대표 기사는 기사 묶음으로 정해져 순서와 무관하다.

## 결정

> 부분 대체(아래 두 항목) — [ADR-0260](0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md). 지금 상태: [digest Spec "후보 선별"](../specs/digest.md#후보-선별) digest-R22
- **구독 후보는 관심도 순위와 중립 순위로 같은 단계(사건 단위 중복 제거 · 다양성 몫 · 최대 후보 수 · 입력 토큰 예산)를 각각 지난다.** 관심도 결과의 구독 후보 가운데 중립 결과의 구독 후보에 없고 관심도 가중치가 1 보다 큰 기사가 `INTEREST` 다. 나머지 구독 후보는 `SUBSCRIBED`, 큰 슬롯 기사는 `MAJOR_STORYLINE` 이다.
- 사건 · 대표 기사는 한 번 읽어 두 결과가 함께 쓰고, 토큰 어림은 두 결과의 기사를 함께 읽어 같은 예산을 같은 규칙으로 적용한다.

## 결과

- 선정 이유가 "왜 들어왔는가"와 맞고 이유별 반응을 따로 잴 수 있다.
- 다른 기사가 가중치 1 미만으로 내려가 빈 자리에 든 가중치 1 인 기사는 `SUBSCRIBED` 다.
- 관심도 때문에 밀려난 기사는 이유가 남지 않는다.
- 순수 단계를 두 번 돌고, 중립으로만 든 기사의 내용을 그만큼 더 읽는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 가중치가 1 보다 큰 구독 후보를 모두 `INTEREST` 로 둔다 | 관심도가 없어도 들었을 기사까지 `INTEREST` 라 선정 이유별 반응이 `SUBSCRIBED` 와 갈리지 않는다 |
| 중립 결과에 없는 기사를 모두 `INTEREST` 로 둔다(가중치 조건 없음) | 다른 기사가 내려가 빈 자리에 든 기사는 관심도로 오른 기사가 아니다 |
