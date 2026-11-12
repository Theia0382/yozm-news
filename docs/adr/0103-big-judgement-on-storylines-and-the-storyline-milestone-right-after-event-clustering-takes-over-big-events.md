# ADR-0103: 큰 판정은 스토리라인 단위로 하고, 사건 묶기 바로 뒤 스토리라인 마일스톤이 사건의 큰 사건 판정을 거둔다

- 상태: 승인
- 날짜: 2026-11-09
- 근거: [이슈 #116](https://github.com/Theia0382/yozm-news/issues/116) — 큰 사건 판정의 코드 · 소비자 · 평가 도구의 위치를 조사해 거둘 시점과 마일스톤 순서를 리드와 정함
- 관련: [ADR-0082](0082-big-events-judged-at-query-time-by-distinct-sources-and-mean-importance-both-reaching-criteria.md)(판정의 단위 — 부분 대체), [ADR-0083](0083-big-event-criteria-as-operator-settings-with-append-only-history-and-preview.md)(기준을 둘 곳 — 부분 대체), [ADR-0026](0026-event-clustering-with-local-embeddings-and-jev.md)(큰 사건 판정과 그 정밀도의 단위 — 부분 대체), [ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)(후보 선별 순서와 구독의 큰 사건 슬롯 — 부분 대체), [ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md) · [ADR-0098](0098-label-rule-2-samples-hold-no-big-event-gold-and-big-event-precision-is-not-measured-without-marks.md)(큰 판정 정밀도와 정답의 단위 — 보충), [ADR-0040](0040-feed-status-stored-in-poll-state-and-rejudged-hourly-by-health-check.md) · [ADR-0048](0048-sign-up-closed-by-default-with-invitations-and-operator-bootstrap.md)(마일스톤 번호 — 보충), [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(스토리라인)

## 맥락

- 큰 사건 판정은 사건마다 기간 안의 서로 다른 소스 수와 중요도 평균을 기준과 견준다([ADR-0082](0082-big-events-judged-at-query-time-by-distinct-sources-and-mean-importance-both-reaching-criteria.md)). 기준은 `event` 의 운영자 설정이다([ADR-0083](0083-big-event-criteria-as-operator-settings-with-append-only-history-and-preview.md)). 판정 · 기준 · 운영자 API · 화면에 코드가 있다.
- 사건을 한 번 일어난 일로 좁히면([ADR-0102](0102-an-event-is-the-reports-of-one-happening-narrowed-by-the-event-membership-question-wording.md)) 큰 일 하나가 사건 여럿으로 나뉘어, 사건마다 센 크기는 그 일의 크기를 적게 센다. 갈래를 잇는 단위는 스토리라인이다([ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)).
- 큰 사건을 쓰는 곳(다이제스트의 큰 사건 슬롯 · 선정 이유 · 구독 제안, 변화 리포트의 보도량)은 아직 코드가 없다. 운영자 화면은 사건 목록에 큰 사건 여부를 보인다.
- 라벨 기준 2판 표본에는 큰 사건 정답이 없다([ADR-0098](0098-label-rule-2-samples-hold-no-big-event-gold-and-big-event-precision-is-not-measured-without-marks.md)). 로드맵의 사건 묶기 완료 기준에 큰 사건 판정 정밀도가 있다.
- 사건 중요도를 큰 사건 여부보다 잘게 나눌지, 사건에 주제 · 지역 태그를 줄지가 미결 사항으로 남아 있다.

## 결정

- **큰 판정의 단위는 스토리라인이다.** 큰 판정은 구독과 무관하게 알릴 만한 흐름을 고른다. 다이제스트의 큰 슬롯 · 선정 이유 · 구독 제안과 변화 리포트의 보도량이 쓰는 "큰" 것도 스토리라인이다. 슬롯에 어떤 기사를 넣는지, 보도량을 어떻게 세는지는 그 설계에서 정한다.
- **스토리라인의 큰 판정을 만드는 작업이 `event` 의 큰 사건 판정 · 기준 · 운영자 API · 화면을 함께 거둔다.** 그때까지 `event` 의 큰 사건 판정은 지금대로 동작한다.
- **스토리라인은 사건 묶기 바로 뒤 마일스톤이다(M4).** 사용자와 키 이하가 하나씩 밀린다 — M5 사용자와 키 · M6 다이제스트 · M7 피드백과 관심사 히스토리 · M8 변화 리포트 · M9 릴리스 준비. 사건 묶기(M3)의 완료 기준에서 큰 사건 판정 정밀도를 빼고 스토리라인 마일스톤으로 옮긴다.
- **사건 중요도 세분화와 사건 태그는 스토리라인 쪽 질문이 된다.** 스토리라인을 구현할 때 정한다.

## 결과

- 스토리라인 마일스톤까지 `event` 가 곧 옮길 판정을 갖는다. 개요와 Spec 은 판정이 옮겨 갈 자리를 함께 적는다.
- 운영자 API 를 거둘 때 평가 도구의 큰 사건 측정([ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md) · [ADR-0098](0098-label-rule-2-samples-hold-no-big-event-gold-and-big-event-precision-is-not-measured-without-marks.md))도 그 작업이 맞춘다. 큰 판정 정답의 단위는 스토리라인이다.
- 사건 묶기 마일스톤은 묶음 정확도와 기준값으로 닫는다.
- 첫 소비자인 다이제스트(M6)보다 스토리라인이 먼저 생긴다. 스토리라인의 운영자 화면 · 평가가 그 사이의 쓰임이다.
- 마일스톤 번호를 인용한 문서와 GitHub 마일스톤 제목이 바뀐다. 옛 ADR 이 인용한 번호는 후속 기록으로 알린다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 큰 사건 판정을 사건 단위로 둔다 | 좁힌 사건은 큰 일의 크기를 갈래마다 나눠 적게 센다 |
| 이 작업에서 `event` 의 큰 사건 판정 · 기준 · API · 화면 · 평가 도구의 큰 사건 부분을 거둔다 | 소비자가 없어 거둘 이득이 없고, 스토리라인 판정이 생길 때까지 운영자가 큰 일을 볼 수단이 빈다. 평가 도구의 결정([ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md) · [ADR-0098](0098-label-rule-2-samples-hold-no-big-event-gold-and-big-event-precision-is-not-measured-without-marks.md))을 바꾼다 |
| 큰 사건 여부 · 기준만 거두고 사건 집계는 운영자 화면에 남긴다 | 거두는 비용 대부분이 그대로이고, 집계도 스토리라인으로 옮겨질 수 있다 |
| 사건 묶기 마일스톤에 스토리라인을 더한다 | 사건 묶기 마일스톤이 크게 늘어 기준값 측정으로 닫으려던 마일스톤이 멀어진다 |
| 다이제스트 마일스톤의 이슈 일을 공유 스토리라인으로 바꾼다 | 다이제스트 마일스톤이 커지고, 공유 · 운영자 비용 처리인 스토리라인이 사용자 마일스톤 뒤로 밀린다 |

## 후속 기록

- 2026-11-11: 결과 "큰 판정 정답의 단위는 스토리라인이다"의 표시와 정밀도 — [ADR-0110](0110-storyline-evaluation-labels-system-events-of-a-fixed-dump-and-measures-overlapping-clusters-from-random-seed-events.md), 지금 상태는 [스토리라인 평가하기 "재는 것"](../operations/storyline-evaluation.md#재는-것)
- 2026-11-12: 결정 "스토리라인의 큰 판정을 만드는 작업이 `event` 의 큰 사건 판정 · 기준 · 운영자 API · 화면을 함께 거둔다"와 결과 · 대안 표의 "집계도 스토리라인으로 옮겨질 수 있다" — 큰 판정의 임시 산식([ADR-0116](0116-storyline-big-judgement-starts-with-a-provisional-rule-of-distinct-sources-and-mean-importance-judged-at-query-time.md)), 재료를 세는 곳([ADR-0117](0117-big-judgement-materials-are-tallied-by-event-per-group-of-events-and-storyline-passes-only-event-ids.md)), 기준([ADR-0118](0118-big-judgement-criteria-are-storyline-operator-settings-and-do-not-inherit-event-criteria-values-or-history.md)), 지금 상태는 [storyline Spec "큰 판정"](../specs/storyline.md#큰-판정)(storyline-R19 · R20)
