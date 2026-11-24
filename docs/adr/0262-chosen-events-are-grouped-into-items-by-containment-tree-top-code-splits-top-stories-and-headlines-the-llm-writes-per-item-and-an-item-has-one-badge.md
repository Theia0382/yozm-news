# ADR-0262: 고른 사건은 포함 나무 꼭대기로 항목을 묶고, 주요 뉴스와 헤드라인은 코드가 가르며, LLM 은 항목마다 쓰고 배지는 항목에 하나다

- 상태: 승인. 부분 대체 — 주요 뉴스 칸에서 버리는 원소와 출력에 없는 항목의 처분([ADR-0269](0269-the-digest-output-keys-top-stories-by-item-alias-as-required-fields-and-lists-headlines-as-elements.md) · [ADR-0270](0270-an-item-missing-from-the-output-is-asked-once-more-and-then-kept-as-a-headline-and-the-counts-are-recorded.md)) · 반응 버튼을 출처 줄마다 두는 것([ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md)) · 출력 상한 20 · 150([ADR-0269](0269-the-digest-output-keys-top-stories-by-item-alias-as-required-fields-and-lists-headlines-as-elements.md)) · 빠뜨린 항목이 남지 않는 것([ADR-0270](0270-an-item-missing-from-the-output-is-asked-once-more-and-then-kept-as-a-headline-and-the-counts-are-recorded.md))
- 날짜: 2026-11-19
- 근거: [이슈 #225](https://github.com/Theia0382/yozm-news/issues/225) — 항목 묶기(포함 나무의 꼭대기, 여러 나무면 큰 판정 나무 → 그날 고른 사건이 많은 나무 → 소속 점수, 이어짐은 쓰지 않음) · 큰 슬롯 항목은 모두 주요 뉴스 · 그 밖 주요 뉴스 수의 처음 값 5 · 항목 순위는 사건 점수의 최대 · 배지는 항목에 하나를 정하고, 헤드라인 항목에 LLM 이 제목과 코멘트를 쓰는 것 · 주요 뉴스를 항목 순위로 두는 것 · 헤드라인 수를 따로 자르지 않는 것을 골랐다. 측정 인스턴스의 기사 단위 다이제스트에서 주요 뉴스 수는 LLM 이 정해 사용자마다 달랐고 큰 슬롯 기사가 구독 기사와 한 주요 뉴스에 섞였다. 구현 커밋 `4c4a33b`(항목 묶기 · 가르기 · 입력 · 프롬프트 · 출력 · 항목 대조 · 저장) · `e1733fd`(섹션의 선정 이유 · 화면 응답) · `f156896`(화면)
- 관련: [ADR-0220](0220-big-slot-candidates-are-grouped-by-big-storyline-row-in-the-prompt-and-the-digest-llm-neither-makes-nor-links-groups.md)(전체 대체 — 큰 스토리라인 묶음 대신 항목), [ADR-0241](0241-selection-reasons-mark-only-citations-outside-subscribed-topics-named-interest-and-big-topic.md)(부분 대체 — 인용마다 표시하던 선정 이유를 항목에 하나로, 화면 이름은 그대로), [ADR-0219](0219-candidate-selection-hands-over-the-prompt-material-read-once-with-its-token-estimate.md)(부분 대체 — 생성의 입력과 덩어리의 꼴이 후보 기사 · 묶음에서 항목 · 사건 · 기사로), [ADR-0186](0186-digests-follow-removed-articles-by-deleting-their-rows-and-show-no-summary-without-any-citation.md)(보충 — 인용 없음은 항목 대조 뒤 남는 항목이 없는 것), [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)(다이제스트 LLM 은 스토리라인을 열거나 잇지 않는다), [ADR-0260](0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md)(후보는 사건), [ADR-0263](0263-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md)(인용은 표본), [ADR-0264](0264-the-storyline-tops-query-gives-each-event-its-containment-tree-roots-in-membership-score-order-without-the-scores.md)(꼭대기 조회)

## 맥락

- 사건 하나는 한 번 일어난 일이고, 한 흐름의 여러 사건은 스토리라인이 잇는다. 포함은 상위 하나의 나무이고 큰 판정 목록은 포함 나무마다 한 행이며 행의 머리가 그 나무의 뿌리다.
- 주요 뉴스를 LLM 이 기사를 골라 묶어 만들면 주요 뉴스 수와 묶음이 모델 · 날마다 흔들리고, 큰 슬롯 기사가 구독 기사와 섞여 어느 주요 뉴스가 큰 화제인지 갈리지 않는다.
- 선정 이유는 기사마다 있고 화면은 인용마다 배지를 달았다. 한 주요 뉴스에 이유가 다른 기사가 섞였다.

## 결정

- **고른 사건은 스토리라인 포함 나무의 꼭대기로 항목을 묶는다.** 큰 슬롯 사건은 그 큰 판정 행의 항목에 든다. 그 밖의 사건은 꼭대기가 없으면 혼자 한 항목이고, 하나면 그 꼭대기의 항목이며, 여럿이면 큰 판정 나무 → 고른 사건 가운데 그 꼭대기를 가진 사건이 많은 꼭대기 → 꼭대기 조회의 순서(소속 점수)로 하나를 고른다. 큰 판정 나무는 자르기 뒤 큰 슬롯 사건이 남은 큰 판정 행의 나무이고, 그 나무에 든 구독 사건은 큰 슬롯 항목에 든다. 이어짐은 쓰지 않는다.
- **주요 뉴스와 헤드라인은 코드가 가른다.** 항목 점수는 항목 안 사건 점수의 최대이고(없으면 맨 뒤), 같으면 항목의 가장 작은 사건 ID 순이다. 큰 슬롯 항목은 모두 주요 뉴스, 나머지는 순서대로 5 개까지 주요 뉴스, 그 뒤는 헤드라인이다. 주요 뉴스 · 헤드라인 모두 항목 순위의 순서다 — 큰 슬롯 항목을 위로 올리지 않는다. 헤드라인 수는 따로 자르지 않는다 — 최대 후보 수(사건 수)가 위쪽이다. 5 는 측정 전 목표다.
- **LLM 은 항목을 묶지 않고 항목마다 쓴다.** 사용자 구획은 항목(이번 요청의 항목 별칭 · 칸) → 사건 → 기사 줄이고 기사 별칭 · DB ID 는 싣지 않는다. LLM 은 항목을 합치거나 나누거나 빼지 않고 항목 별칭으로만 가리키며, 개요와 주요 뉴스 항목마다 제목 · 요약, 헤드라인 항목마다 제목 · 짧은 코멘트를 쓴다.
> 부분 대체(둘째 문장) — 주요 뉴스 칸의 모르는 항목 별칭 · 다른 칸 항목의 처분은 [ADR-0269](0269-the-digest-output-keys-top-stories-by-item-alias-as-required-fields-and-lists-headlines-as-elements.md), 출력에 없는 항목의 처분은 [ADR-0270](0270-an-item-missing-from-the-output-is-asked-once-more-and-then-kept-as-a-headline-and-the-counts-are-recorded.md). 지금 상태: [digest Spec](../specs/digest.md#인용-검증) digest-R6
- **출력은 기사를 인용하지 않는다** — 인용은 항목의 표본 기사다([ADR-0263](0263-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md)). 모르는 항목 별칭 · 다른 칸의 항목 · 앞에서 이미 쓴 항목은 버리고, 출력에 없는 항목은 저장하지 않으며, 남은 원소는 출력 순서가 아니라 항목 순서로 저장한다. 남는 항목이 없으면 인용 없음이다.
- **섹션 하나가 항목 하나다** — 개요 · 주요 뉴스 · 헤드라인. 섹션은 그 항목의 사건과 사건마다 선정 이유를 가리키는 행을 갖고, 인용은 그 항목의 표본 기사다.
> 부분 대체(마지막 문장의 반응 버튼) — [ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md). 지금 상태: [reader Spec](../specs/reader.md#다이제스트) reader-R9
- **선정 이유 배지는 항목에 하나다** — 항목 안 사건의 이유 가운데 큰 화제(`MAJOR_STORYLINE`) > 관심 반영(`INTEREST`) > 구독 주제(`SUBSCRIBED`) 순으로 높은 것이고, 구독 주제는 표시하지 않는다. 화면은 항목마다 출처 기사 목록(인용)을 보이고 반응 버튼은 출처 줄마다 둔다.

## 결과

- 주요 뉴스 수가 규칙으로 정해진다 — 큰 슬롯 항목 수(사용자의 큰 슬롯 수 이하) + 5 이하. 큰 슬롯 항목은 큰 화제 하나로 갈린다.
> 부분 대체 — [ADR-0269](0269-the-digest-output-keys-top-stories-by-item-alias-as-required-fields-and-lists-headlines-as-elements.md). 지금 상태: [digest Spec](../specs/digest.md#출력-읽기) digest-R4 · R5
- 출력 서술의 주요 뉴스 상한(20)은 큰 슬롯 수 상한(10)과 5 의 합 이상이어야 하고, 헤드라인 상한(150)은 최대 후보 수의 직접 입력 상한과 같아야 한다 — `digest` 는 `user` 의 상수를 읽지 못해 두 값이 사본이고, 시험이 그 관계를 묶는다. 넘친 항목은 읽기가 버린다.
- 헤드라인 항목마다 제목 · 코멘트를 써 출력 토큰이 는다. 크기는 재지 않았다.
- 같은 흐름이어도 포함으로 이어지지 않은 스토리라인의 사건은 다른 항목이 된다.
- 큰 화제의 주제가 관심이 낮으면 주요 뉴스 아래쪽에 선다.
> 부분 대체 — [ADR-0270](0270-an-item-missing-from-the-output-is-asked-once-more-and-then-kept-as-a-headline-and-the-counts-are-recorded.md). 지금 상태: [digest Spec](../specs/digest.md#인용-검증) digest-R6
- LLM 이 빠뜨린 항목은 다이제스트에 남지 않는다 — 그 수를 정보 로그로 센다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| LLM 이 후보 기사를 골라 주요 뉴스로 묶는다(큰 슬롯 기사는 행마다 묶어 보인다) | 주요 뉴스 수와 묶음이 모델 · 날마다 흔들리고 큰 슬롯 기사가 구독 기사와 섞인다 |
| 이어짐 관계도 따라 묶는다 | 앞 흐름과 그것이 낳은 뒤 흐름은 다른 이야기이고, 이어짐은 여럿을 가질 수 있어 항목이 한없이 커질 수 있다 |
| 큰 슬롯 항목을 먼저, 그다음 항목 순위 | 관심이 좁은 사용자의 위쪽을 구독 밖 소식이 채운다 — 큰 슬롯의 뜻은 자리를 주는 것이다 |
| 헤드라인 항목은 LLM 이 쓰지 않고 대표 기사 제목을 보인다 | 사건 여럿인 항목의 제목을 기사 하나가 대신한다 |
| 헤드라인도 주요 뉴스와 같은 제목 · 요약 | 둘의 차이가 화면 배치뿐이고 출력이 길어진다 |
| 헤드라인 항목을 상수만큼만 생성에 넘긴다 | 최대 후보 수의 뜻이 "사건 수, 단 헤드라인은 몇 개까지"로 갈린다 — 비용 위쪽은 분량 단계로 고른다 |
| 헤드라인을 섹션 하나에 담는다 | 헤드라인 항목마다 제목 · 코멘트 · 사건 · 출처가 있어 섹션과 사건의 짝이 항목을 잃는다 |
| 출력이 기사 별칭도 인용한다 | 인용이 표본이라 LLM 이 고를 것이 없다 |

## 후속 기록

- 2026-11-22: 결정의 섹션이 갖는 것 — 항목을 묶은 꼭대기도 섹션에 남긴다([ADR-0284](0284-a-digest-item-keeps-the-top-that-grouped-it-on-its-section-and-items-with-a-flow-carry-a-flow-tracking-button.md)), 지금 상태는 [digest Spec](../specs/digest.md) digest-R7 · R46
