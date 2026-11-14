# ADR-0117: 큰 판정의 재료는 `event` 가 사건 묶음 단위로 세고 `storyline` 은 사건 ID 만 넘긴다

- 상태: 승인
- 날짜: 2026-11-12
- 근거: [이슈 #124](https://github.com/Theia0382/yozm-news/issues/124) — 큰 판정 재료(기사의 소스 · 중요도)의 위치와 모듈 의존을 조사해 리드와 정함
- 관련: [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)(결과 절 · 대안 표의 "집계도 스토리라인으로 옮겨질 수 있다" — 보충), [ADR-0105](0105-storylines-link-incrementally-from-event-change-notifications-and-events-publish-changed-event-ids.md)(스토리라인은 사건 ID 만 가리키고 기사를 모른다 — 보충), [ADR-0116](0116-storyline-big-judgement-starts-with-a-provisional-rule-of-distinct-sources-and-mean-importance-judged-at-query-time.md)(큰 판정의 임시 산식), [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(스토리라인)

## 맥락

- 큰 판정의 재료는 조회 기간 안 기사의 서로 다른 소스 수와 중요도 평균이다([ADR-0116](0116-storyline-big-judgement-starts-with-a-provisional-rule-of-distinct-sources-and-mean-importance-judged-at-query-time.md)). 소스는 기사의 엔트리(`article`), 중요도는 기사 평가(`tag`)에 있고, 사건의 기간 안 기사는 `event` 의 배정에 있다.
- `storyline` 은 사건 ID 만 저장하고 기사를 모른다([ADR-0105](0105-storylines-link-incrementally-from-event-change-notifications-and-events-publish-changed-event-ids.md)). 의존은 `event` · `platform` 이다.
- 서로 다른 소스 수는 합집합의 크기라 사건별 집계를 더해 얻을 수 없다.
- 기사는 사건 하나에만 배정된다. 사건은 스토리라인 여럿에 들 수 있다.
- 운영자의 사건 목록은 사건마다 기간 안 기사 수 · 소스 수 · 중요도 평균을 보인다.

## 결정

- **기간 집계는 `event` 가 센다.** `event` 의 공개 조회가 사건 묶음(묶음 키 → 사건 집합)마다 그 사건들의 기간 안 기사를 한데 모아 기사 수 · 서로 다른 소스 수 · 중요도 평균을 낸다. `storyline` 은 스토리라인마다 구성 사건 ID 를 넘기고, 판정 규칙 · 기준 · 순서를 갖는다.
- **한 묶음 안에서 같은 사건은 한 번만 세고, 한 사건이 여러 묶음에 들면 묶음마다 센다.**
- **여러 스토리라인의 합계는 구성 사건 ID 의 합집합으로 센다.** 기사가 사건 하나에만 배정되므로 기사도 한 번이다. 합계를 내는 공개 조회는 그것을 쓰는 기능을 만들 때 둔다.
- **사건 목록의 기간 집계는 같은 집계의 사건 하나짜리 경우다.** 산식의 정본은 하나다.

## 결과

- `storyline` 에 `article` · `tag` · `source` 의존이 생기지 않고, 스토리라인이 기사를 모르는 경계가 남는다.
- `event` 는 큰 판정을 하지 않지만 집계 재료를 읽는 `article` · `tag` · `source` 의존은 남는다.
- 판정(`storyline`)과 재료(`event`)가 두 모듈에 걸친다 — 식이 기사 단위의 다른 재료를 쓰게 되면 `event` 의 공개 조회를 함께 고친다.
- 포함 관계에서 상위 스토리라인이 하위를 세는 법과 스토리라인의 보도량을 세는 법은 큰 판정의 식과 함께 정한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 집계를 `storyline` 으로 옮긴다 — `event` 는 기간 안 배정을 내고 `storyline` 이 소스 · 중요도를 읽어 센다 | `storyline` → `article` · `tag` · `source` 의존이 생기고 기사를 모르는 경계가 깨진다. 정본을 하나로 두려면 사건 목록의 집계 칸을 지워야 한다 |
| 사건 목록의 집계는 `event` 에, 스토리라인의 집계는 `storyline` 에 둔다 | 같은 산식이 두 모듈에 생긴다 |
| 사건별 집계를 `storyline` 이 합친다 | 소스 수는 합집합의 크기라 사건별 수를 더하면 겹치는 소스를 두 번 센다 |
| 여러 스토리라인의 합계 조회를 지금 둔다 | 쓰는 곳이 없고, 보도량을 세는 법에 따라 조회의 모양이 달라진다 |

## 후속 기록

- 2026-11-14: 결정 "기사 수 · 서로 다른 소스 수 · 중요도 평균을 낸다" — 중요도 합과 기간 전체 집계를 더했다([ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md)), 지금 상태는 [event Spec "대표 기사 · 기간 집계"](../specs/event.md#대표-기사--기간-집계)
- 2026-11-14: 결과 "포함 관계에서 상위 스토리라인이 하위를 세는 법과 스토리라인의 보도량을 세는 법은 큰 판정의 식과 함께 정한다" — [ADR-0147](0147-storyline-big-judgement-compares-importance-weighted-coverage-per-source-and-day-with-one-criterion-at-query-time.md) · [ADR-0148](0148-storyline-coverage-is-the-article-count-in-the-period-over-the-closed-event-set.md), 지금 상태는 [storyline Spec "큰 판정"](../specs/storyline.md#큰-판정) · ["공개 조회"](../specs/storyline.md#공개-조회)
