# ADR-0265: 반응은 다이제스트 항목에 한 번 누르고 그 항목의 사건들에 기록하며, 화면은 항목에 누른 값을 보이고 신호는 사건 기록이 맡는다

- 상태: 승인
- 날짜: 2026-11-20
- 근거: [이슈 #226](https://github.com/Theia0382/yozm-news/issues/226) — 반응의 단위를 사건으로 정하고(측정 DB 에서 기사 2건 이상인 사건의 68% 가 발행일 하루, 31% 가 이틀 — 같은 일을 다룬 비슷한 기사를 하나씩 누르게 하지 않는다), 화면에는 그 항목에 누른 값만 보이며(사건 하나하나에 대한 평가가 아니다), 되돌리기는 항목의 사건 모두를 없음으로 둔다고 정했다. 구현 커밋 `a948926`(항목 확인 · 항목 기록 · 사건마다 기록 · 싫어요 사건 거름) · `02f2e98`(화면 응답의 항목 사건 ID) · `d34b471`(항목마다 반응 버튼)
- 관련: [ADR-0196](0196-feedback-is-accepted-only-on-articles-cited-by-the-users-digest-and-digest-answers-through-an-interface-declared-by-feedback.md)(부분 대체 — 결정 "인용한 기사에만 받는다"와 "인용했는지는 `DigestCitations` 로 묻는다"), [ADR-0198](0198-feedback-recording-is-request-limited-per-session-user.md)(부분 대체 — 결정 첫째의 한도 근거), [ADR-0028](0028-explicit-signals-and-interest-weighted-candidates.md)(부분 대체 — 결정의 "기사 좋아요 / 싫어요"와 현재 상태의 "기사 피드백"), [ADR-0260](0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md)(부분 대체 — 결정 첫째의 기사 제외 가운데 싫어요), [ADR-0263](0263-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md)(부분 대체 — 결정 다섯째의 현재 상태 조회 상한과 요청 제한의 근거), [ADR-0157](0157-credential-depends-on-llm-and-stored-keys-reach-llm-through-a-supply-interface-that-llm-declares-and-credential-implements.md)(같은 꼴 — 필요한 쪽이 선언하고 지식을 가진 쪽이 구현하는 공급 인터페이스), [ADR-0262](0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md)(부분 대체 — 결정 "선정 이유 배지는 항목에 하나다"의 "반응 버튼은 출처 줄마다 둔다". 항목의 정의는 그대로), [ADR-0102](0102-an-event-is-the-reports-of-one-happening-narrowed-by-the-event-membership-question-wording.md)(사건의 범위)

## 맥락

- 다이제스트의 단위는 항목이다 — 고른 사건을 포함 나무 꼭대기로 묶고, 섹션 하나가 항목 하나이며, 사건마다 표본 기사를 5건까지 출처로 보인다([ADR-0262](0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md) · [ADR-0263](0263-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md)).
- 반응은 출처 기사 줄마다 눌렀고 (사용자, 기사)의 현재 상태 · 이력에 남았다. 사건은 한 번 일어난 일을 다룬 보도들이라([ADR-0102](0102-an-event-is-the-reports-of-one-happening-narrowed-by-the-event-membership-question-wording.md)) 한 사건의 기사들은 서로 비슷하다.
- 싫어요는 그 기사만 사건의 기사에서 뺐다 — 같은 사건의 다른 기사가 남으면 사건은 그대로 후보였다.
- 화면 응답에는 항목을 가리킬 식별자가 없었다. 섹션의 내부 행 ID 는 화면이 쓰지 않는다.
- 같은 사건이 다이제스트 두 날 이상에 나오면 날마다 항목의 구성이 다를 수 있다. 사건 하나에 남긴 값을 항목의 표시로 모으면 그 항목에 누르지 않은 값이 보인다.
- 다이제스트의 항목 구성은 `digest` 만 안다. `digest` 는 후보 선별 · 관심도를 위해 `feedback` 을 읽으므로 `feedback` 이 `digest` 를 부르면 순환이다.

## 결정

- **반응은 다이제스트 항목(주요 뉴스 · 헤드라인 섹션)에 한 번 누른다.** 항목은 (다이제스트, 항목 키)로 가리킨다 — 항목 키는 그 항목의 첫 사건(섹션 사건의 위치 0)이다. 화면 응답의 섹션은 그 항목의 사건 ID 를 위치 순으로 싣는다.
- **받는 항목은 그 사용자의 다이제스트에서 요청의 사건이 든 항목이다.** `feedback` 이 선언한 공급 인터페이스(`DigestItems`)로 묻고 `digest` 가 구현한다 — 그 사건이 든 항목이 하나면 그 항목의 사건들을, 없거나 둘 이상이면 빈 목록을 낸다. 날짜별로 보이는 다이제스트인지는 묻지 않는다. 의존은 `digest` → `feedback` 하나다.
- **누르기 한 번은 그 항목의 사건 모두를 그 값으로 둔다.** 없음(되돌리기)도 같다 — 다른 항목에서 그 사건에 남긴 값도 바뀐다.
- **기록은 둘이고 같은 트랜잭션에서 쓴다.** 항목 기록은 그 항목에 누른 값 하나이고 이력이 없다 — 화면이 보이는 값이다. 사건마다의 현재 상태 · 추가 전용 이력은 관심 신호이고 이력이 정본이다. 화면은 다이제스트 ID 로 항목 기록만 읽고, 사건 기록을 항목의 표시로 모으지 않는다.
- **잠금 순서는 항목 기록 행 → 사건 행(사건 ID 오름차순)이다.** 같은 사건을 담은 서로 다른 항목을 동시에 눌러도 교착하지 않는다.
- **싫어요는 사건으로 거른다.** 그 사용자의 값이 싫어요인 사건은 구독 후보와 큰 슬롯에서 함께 빠지고, 그 사건의 다른 기사도 들지 않는다. 판정 함수는 하나다. 좋아요는 관심도로만 든다.
- **피드백 요청 제한은 750 / 10분 그대로이고, 근거는 다이제스트 하나의 항목(많아야 최대 후보 수의 직접 입력 상한 150)을 누르고 고쳐 누르는 사용이 창 하나에 드는 값이다.** 화면의 항목 기록 조회는 다이제스트 하나의 항목 수만큼이라 상한을 두지 않는다.

## 결과

- 같은 일을 다룬 기사를 하나씩 누르지 않는다. 사건 여럿인 항목(큰 슬롯 항목 등)도 버튼은 하나다.
- 같은 사건이 다른 날짜의 다른 항목에 들어도 화면은 각 항목에 누른 값만 보인다. 사건 기록은 마지막 누르기를 따른다.
- 되돌리기가 그 항목의 사건 모두를 없음으로 두어, 다른 항목에서 남긴 값까지 지울 수 있다.
- 이미 같은 값인 사건은 이력 행이 생기지 않는다 — 사건 이력의 마지막 행은 값이 바뀐 누르기다.
- 지난 다이제스트에서 싫어요를 누른 사건은 다시 만들기와 다음 다이제스트 모두에서 빠진다. 작은 사건에 누른 싫어요가 그 사건이 합쳐진 큰 사건을 빼는지는 [ADR-0266](0266-feedback-is-recorded-under-the-original-event-id-and-feedback-resolves-merged-events-through-event-when-reading-with-the-last-pressed-value-winning.md)이 정한다.
- 다이제스트 밖의 화면(스토리라인 타임라인 등)에서는 반응을 남길 수 없다.
- 항목 키는 섹션의 위치 0 사건이 바뀌지 않는다는 데 기댄다 — 섹션은 끝낼 때 한 번 쓰고 고치지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 반응은 기사마다 그대로 둔다 | 같은 일을 다룬 비슷한 기사를 하나씩 누르게 되고, 싫어요가 사건을 빼지 못한다 |
| 항목 안 사건마다 버튼을 둔다 | 화면의 단위는 항목이다 — 사건은 화면에 따로 보이지 않고, 큰 슬롯 항목에 버튼이 여럿 붙는다 |
| 항목 기록 없이 사건들의 현재 상태를 모아 항목에 보인다 | 사건 하나하나에 대한 평가가 아니라 화면에 띄울 까닭이 없고, 다른 날 다른 항목에서 누른 값이 이 항목에 보인다 |
| 항목 키를 섹션 ID 로 둔다 | 화면이 쓰지 않는 내부 행 ID 를 계약에 올리고 `feedback` 이 섹션을 알아야 한다 |
| 화면의 상태 조회를 사건 ID 목록으로 받는다 | 항목 기록을 다이제스트 ID 로 읽으면 목록과 그 상한이 필요 없다 |
| 서로 다른 항목의 사건 행을 항목 안 순서로 쓴다 | 같은 사건을 반대 순서로 담은 두 항목을 동시에 누르면 교착한다 |
| 싫어요를 기사 제외로 남기고 사건의 남은 기사로 후보를 살린다 | 반응의 단위가 사건인데 같은 사건이 다른 기사로 다시 든다 |

## 후속 기록

- 2026-11-21: 결정 "피드백 요청 제한은 750 / 10분 그대로이고, 근거는 … 직접 입력 상한 150"의 근거 — [ADR-0272](0272-the-input-token-budget-is-a-safety-ceiling-and-the-maximum-candidate-steps-set-the-digest-volume.md) · [user Spec "요청 방어"](../specs/user.md#요청-방어) user-R38
