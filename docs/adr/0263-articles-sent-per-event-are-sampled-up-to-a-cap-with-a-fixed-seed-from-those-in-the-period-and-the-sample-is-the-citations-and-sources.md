# ADR-0263: 사건마다 LLM 에 넘기는 기사는 그 기간에 든 기사에서 고정 시드로 상한까지 뽑고, 그 표본이 인용 · 출처다

- 상태: 승인. 부분 대체 — 인용이 노출 기록인 것([ADR-0268](0268-interest-counts-exposures-and-reactions-per-event-splitting-an-item-weight-one-over-n-and-counting-an-event-once-with-its-latest-share.md)) · 현재 상태 조회 상한과 요청 제한의 근거([ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md))
- 날짜: 2026-11-19
- 근거: [이슈 #225](https://github.com/Theia0382/yozm-news/issues/225) — 사건당 기사 상한 · 사건 안 무작위 · 시드 고정 · 출처 다양성 조건 없음과 상한의 처음 값 5 를 정하고, 표본의 모집단(그 기간에 든 기사) · 표본 = 인용 = 출처 · 인용 기사 수 위쪽에 맞춘 `feedback` · `user` 상한(둘 다 750)을 골랐다. 측정 인스턴스에서 후보 사건 대부분은 기사 한 건이고 상한은 큰 사건에서만 걸렸다. 구현 커밋 `f545d30`(표본 규칙) · `4c4a33b`(사건 단위 어림 · 자르기 · 인용 · 상한 750)
- 관련: [ADR-0210](0210-candidate-selection-cuts-to-the-input-token-budget-with-one-estimate-the-prompt-follows.md)(부분 대체 — 후보 줄마다의 어림이 사건마다 표본 기사 줄의 합과 사건 몫으로, 묶음 몫 없음), [ADR-0198](0198-feedback-recording-is-request-limited-per-session-user.md)(보충 — 인용 서로 다른 기사의 위쪽과 피드백 요청 제한의 값), [ADR-0209](0209-maximum-candidates-and-big-slots-are-user-settings-and-the-big-slot-takes-at-most-half.md)(보충 — 결과의 인용 서로 다른 기사 수), [ADR-0222](0222-interest-per-subtopic-is-smoothed-reactions-over-exposures-with-a-half-life-and-exposures-are-all-ready-citations.md)(노출은 인용), [ADR-0260](0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md)(후보는 사건)

## 맥락

- 후보가 사건이면 사건 하나에 기사가 여럿이다. 모두 넘기면 큰 사건 하나가 입력을 차지한다.
- 며칠 이어지는 사건을 따로 다루지 않는다 — 같은 사건이 다음 기간에 다시 오를 수 있다.
- 인용은 노출 기록이고 관심도의 분모다([ADR-0222](0222-interest-per-subtopic-is-smoothed-reactions-over-exposures-with-a-half-life-and-exposures-are-all-ready-citations.md)). 반응 버튼은 인용 기사에만 받는다. 둘 다 기사 단위로 남는다.
- 화면은 다이제스트 하나의 인용 기사 전부를 한 요청으로 묻고, 피드백 요청 제한의 한도는 다이제스트 하나의 인용 기사를 하나씩 누르는 사용이 창 하나에 드는 값이다([ADR-0198](0198-feedback-recording-is-request-limited-per-session-user.md)).

## 결정

- **사건마다 넘기는 기사(표본)는 그 사건의 기간에 든 기사 가운데 사용자의 기사 제외를 지난 기사에서 고른다** — 구독 사건은 그 기간에 첫 평가된 기사(발행 시각 하한 포함), 큰 슬롯 사건은 기간 안(사건 배정의 발행 시각) 기사 가운데 평가가 있는 기사다.
- **고르는 순서는 (사건 ID, 기사 ID) 의 고정 해시이고 앞 5 건을 넘긴다** — 같은 사건 · 같은 기사들이면 언제나 같은 표본이고, 기사가 늘어도 앞서 뽑힌 기사는 새 기사가 그보다 앞설 때만 밀린다. 출처가 겹치지 않게 고르는 조건은 두지 않는다. 5 는 측정 전 목표다.
> 부분 대체("노출 기록") — [ADR-0268](0268-interest-counts-exposures-and-reactions-per-event-splitting-an-item-weight-one-over-n-and-counting-an-event-once-with-its-latest-share.md). 지금 상태: [digest Spec "관심도"](../specs/digest.md#관심도) digest-R24
- **표본이 곧 인용(노출 기록)이고 화면의 출처 기사 목록이다.** 생성 중 지운 표본 기사는 인용에서 빠진다.
- **토큰 어림은 사건마다다** — 내용을 읽은 표본 기사 줄의 어림 합 + 사건 몫(사건 괄호와 항목 괄호 · 항목 별칭 · 칸 이름을 사건마다 센 40 코드 포인트). 예산 자르기는 사건 단위로 구독 사건의 꼬리부터, 그래도 넘으면 큰 슬롯 사건의 꼬리부터 뺀다. 표본 기사의 내용을 하나도 읽지 못한 사건은 뺀다. 40 은 측정 전 목표다.
> 부분 대체(둘째 · 셋째 문장) — [ADR-0265](0265-feedback-is-pressed-once-per-digest-item-and-recorded-on-each-event-of-the-item-the-screen-shows-the-item-value-and-signals-use-event-records.md). 지금 상태: [account Spec "요청 방어"](../specs/account.md#요청-방어) account-R30 · [feedback Spec](../specs/feedback.md#화면의-항목-기록-조회) feedback-R7
- **다이제스트 하나의 서로 다른 인용 기사는 많아야 사건당 상한 5 × 최대 후보 수의 직접 입력 상한 150 = 750 이다.** `feedback` 의 현재 상태 조회 상한과 피드백 요청 제한(세션 사용자 · 10분)을 둘 다 750 으로 둔다. 셋의 관계는 시험이 묶는다.

## 결과

- 다시 오른 사건은 그 기간에 새로 든 기사로 쓴다 — 어제와 같은 기사를 다시 요약하지 않는다. 기간에 든 기사가 한 건이면 사건의 기사가 많아도 한 건만 넘긴다.
- LLM 이 읽은 기사와 사용자가 본 출처 · 노출이 같다. 출처 목록은 사건마다 상한까지만 보인다.
- 사건마다 기사 여럿을 넘겨 입력 토큰이 기사 단위보다 늘 수 있고, 예산이 사건을 더 뺄 수 있다. 크기는 재지 않았다.
- 피드백 요청 제한이 10분 750 으로 느슨해진다. 실제 인용 수는 입력 토큰 예산이 먼저 막는 일이 많지만 예산은 운영자 파일로 바뀌어 보장이 아니다.
- 반응을 사건 단위로 바꾸면 이 상한들을 다시 본다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 표본을 그 사건의 모든 기사에서 고른다 | 다시 오른 사건에서 어제 본 기사가 다시 뽑힐 수 있다 |
| LLM 에는 표본만, 인용 · 출처는 그 기간에 든 기사 전부 | LLM 이 읽지 않은 기사가 노출로 세어지고 요약과 출처가 어긋날 수 있다 |
| `digest` 가 다이제스트 하나의 표본 기사 합에 상한을 두고 넘으면 사건을 뺀다 | 다른 모듈의 한도를 위해 다이제스트에 규칙이 하나 늘고, 최대 후보 수를 채우지 못하는 갈래가 하나 더 생긴다 |
| 화면이 현재 상태를 나눠 묻고 요청 제한은 그대로 둔다 | 피드백 요청 제한의 크기 규칙("다이제스트 하나를 하나씩 누르는 사용이 창 하나에")을 깨거나 다시 정해야 한다 |
