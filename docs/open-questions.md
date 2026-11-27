# 미결 사항

결정되면 해당 문서와 ADR에 반영하고 여기서 지운다.

## 착수 전에 필요

| # | 질문 | 권장안 | 필요 시점 |
|---|---|---|---|

## 운영하면서 결정

| # | 질문 | 권장안 |
|---|---|---|
| 8 | 다이제스트 일정 · 수동 생성 · 거두기의 값(아래 8번) | v1 은 하루 한 번 일정 + 수동 생성이다. 값은 측정 전 목표로 시작해 M6 완료 확인(7일 일정 생성)에서 실패율 · 생성 시간 · 대기열 지연과 함께 다시 본다 |
| 42 | 변화 리포트 일정 · 생성 조건 · 거두기 · 다시 만들기의 값(아래 42번) | 값은 측정 전 목표로 시작해 M8 완료 확인(2주 이상 쌓인 데이터의 주간 · 월간)에서 생성 조건으로 건너뛴 기간 · 실패율 · 다시 만들기 수 · 월간이 기다린 회차 수와 함께 다시 본다 |
| 9 | 후보 수 · 토큰 예산과 후보 선별의 상수(아래 9번) | 최대 후보 수는 사건 수이고 사용자 설정이며 평소 분량을 정한다. 모델별 입력 토큰 예산은 안전 상한이다 |
| 10 | 보존 기간 ([data-model.md](data-model.md)) | 초안 값으로 시작. RSS 요약문은 결정됨(아래) |
| 11 | 사용자 피드 추가 허용 | v1은 불가. 열 때는 사용자당 피드 수 상한, 피드 볼륨 상한, 운영자 승인 중 하나 이상이 필요하다 (Jev 비용이 운영자 부담이므로) |
| 12 | 본문 추출 도입(아래 12번) | 보류한다. 지시 규칙([digest Spec](specs/digest.md#프롬프트-구성) digest-R26)과 요약문이 주요 뉴스 요약 하한보다 짧은 항목을 헤드라인으로 두는 규칙([digest Spec](specs/digest.md#항목-묶기와-가르기) digest-R145)으로 먼저 다루고, 그 뒤 요약문이 거의 없는 소스(제목만 주는 피드)부터 허용 소스 한정으로 검토 |
| 20 | 사건의 최대 기간. 연 뒤 이만큼 지나면 새 기사가 있어도 닫는다. 기본값(측정 전)은 [event Spec §7](specs/event.md#7-설정)에 있다. 나머지 사건 묶기 값은 결정됨(아래) | 평가 세트의 다시 묶기는 며칠치라 이 길이를 담지 못한다. 운영 기록에서 연 뒤 오래 이어지는 사건이 보이면 정한다 |
| 21 | 다이제스트의 큰 슬롯 수와 스토리라인 큰 판정 기준의 조정(아래 21번) | M6 측정에서는 큰 슬롯 수 · 행마다 사건 10 · 큰 판정 기준을 그대로 두었다(지금 값과 측정은 아래 21번) |
| 24 | 관심도 · 구독 제안 · 관심사 히스토리 · 스토리라인 관심의 값(아래 24번) | 중립에서 시작해 M7 측정으로 정한다. 감쇠 · β · K · θ 와 스토리라인의 기준들은 처음 값 그대로이고 실측 인스턴스의 사용자 반응으로 다시 본다(측정과 정한 것은 아래 24번) |
| 35 | 스토리라인 잇기의 점수가 같은 분야의 무관한 사건과 같은 사안을 가르지 못한다([storyline Spec §5](specs/storyline.md#잇기-규칙) · [띠 판정](specs/storyline.md#띠-판정) · [§7](specs/storyline.md#7-설정), 아래 35번) | 정한 값으로 실측 인스턴스를 올려 몇 주의 전개 · 하루 비용 · 처리량을 재고, 띠 밖의 분야 모음이 얼마나 남는지 본다 |
| 36 | 스토리라인 관계의 기준과 관계 판정의 문구 · 입력(아래 36번) | 쌍 판정 세트에서 고른 값으로 시작해 운영 기록과 운영자 표본 검수로 다시 본다(함께 볼 것은 아래 36번) |
| 37 | 같은 원인에서 나온 나란한 스토리라인(같은 일에서 나왔지만 저마다 따로 전개되는 별개의 스토리라인)을 묶을지(아래 37번) | 운영하며 나란한 스토리라인이 따로 남아 다이제스트 · 큰 판정 목록에서 같은 사안이 여러 번 나오는 일이 잦은지 본다. 다시 다룬다면 상위 스토리라인을 여는 대신 두 스토리라인 사이의 가벼운 고리로 두는 쪽부터 견준다 |
| 38 | 같은 사안의 조각을 합치는 기준의 재현율과 흔들림(아래 38번) | 운영 기록의 합쳐진 스토리라인과 결말 "조각 — 합치기 기준 미달"인 쌍을 표본으로 본다. 잘못 합친 것이 보이면 기준을 올리고, 둘로 남는 조각이 잦으면 같은 쌍을 한 번 더 물어 두 번 다 기준을 넘을 때만 합치는 식의 장치를 견준다 |
| 39 | 다이제스트 항목의 값과 사건 단위의 결과(아래 39번) | 실측 다이제스트에서 사용자마다 주요 뉴스 · 헤드라인 수, 입력 · 출력 토큰, 다이제스트에 오르지 않은 평가 기사를 보고 주요 뉴스 수 · 사건당 기사 수 · 몫의 문턱 · 예산과 함께 조정한다(나머지 권장은 아래 39번) |
| 40 | 사용자 스토리라인이 물려받는 이어짐 판정의 품질과 맞추기의 비용(아래 40번) | 이어짐 판정의 품질을 그대로 물려받고 시작한다. 실측 인스턴스에서 갈라짐의 빈도와 맞추기 지연을 본 뒤, 묶음에 쓸 이어짐의 확률 기준을 따로 둘지와 읽는 쪽의 결과를 잠깐 기억할지를 정한다([ADR-0281](adr/0281-a-user-storyline-is-a-flow-that-received-interest-signals-and-tracks-the-tops-reached-forward-along-follows-from-its-seeds-as-one-group.md) · [ADR-0282](adr/0282-flow-tracking-points-to-a-user-storyline-which-user-handlers-store-under-the-settings-lock-while-readers-get-the-result-of-the-same-rule.md)) |
| 41 | 사용자 스토리라인의 정리의 값과 품질(아래 41번) | 처음 값으로 시작하고, 실측 인스턴스에서 실제 모델로 길이 · 잘림 · 고쳐 쓴 글의 품질을 본 뒤 정한다 |
| 31 | 메일 발송 서비스 선택(메일을 도입할 때) | SMTP 로 붙인다([ADR-0012](adr/0012-swappable-external-dependencies.md)). 후보는 Resend · Amazon SES · Postmark 다. 발송 도메인에 SPF · DKIM · DMARC 를 설정한다. 자체 메일 서버는 전달률을 위해 역방향 DNS 도 필요하다 |
| 32 | 키가 없는 사용자에게 Jev 평가 결과 기반 헤드라인 목록을 보여 줄지 | 보류한다. 생성형 LLM 을 쓰지 않아 운영자 비용이 늘지 않는다. 키가 없는 사용자에게는 헤드라인을 보여 주지 않고, 키 등록 · 계정 · 알림 · 관심 설정 화면만 쓴다([ADR-0003](adr/0003-byok-personalized-digest.md)) |

표 칸에 다 쓰지 않은 값 · 사정 · 측정 기록은 아래 번호별 목록에 있다.

- **8번 다이제스트 일정 · 수동 생성 · 거두기의 값**
  - 값([digest Spec](specs/digest.md#일정) digest-R29 ~ R32 · [user Spec](specs/user.md#다이제스트-일정과-쓸-모델) user-R61)
    - 따라잡기 창 2시간
    - 기간 상한 72시간
    - 첫 기간 24시간
    - 하루 수동 세 번
    - 등록 기다림 15분
    - 시작 기한 1시간
    - 임대 40분(생성 호출 최악 × 2 + 10분)
    - 일정 목록 쪽 500
    - 거두기 쪽 100
  - 평가 시각이 커밋 직전에 찍혀, 기간 끝 직전에 찍히고 선별 뒤 커밋된 평가는 빠질 수 있다
- **42번 변화 리포트 일정 · 생성 조건 · 거두기 · 다시 만들기의 값**
  - 값([report Spec](specs/report.md#일정) report-R27 ~ R30 · R32)
    - 따라잡기 창 2시간
    - 일정 회차 10분
    - 주간 생성 조건 READY 다이제스트 3일
    - 월간 생성 조건 기간 안에 다 든 READY 주간 2편
    - 등록 기다림 15분
    - 시작 기한 1시간
    - 임대(생성 호출 최악 둘 + 10분)
    - 다시 만들기 리포트마다 세 번
    - 일정 목록 쪽 500
    - 거두기 쪽 100
  - 생성 조건에 못 미친 기간은 행 없이 건너뛰어 목록에 남지 않는다
  - 월간이 끝나지 않은 주간을 기다리는 동안 따라잡기 창이 지나면 그 달의 월간은 생기지 않는다
- **9번 후보 수 · 토큰 예산과 후보 선별의 상수**
  - 최대 후보 수의 사용자 설정은 단계 20 · 40 · 60, 직접 입력 10 ~ 100, 기본 40 이다
  - 모델별 입력 토큰 예산은 기본 파일 모델마다 120,000 이다([llm Spec](specs/llm.md#공급자와-허용-모델-목록) llm-R4 · [ADR-0272](adr/0272-the-input-token-budget-is-a-safety-ceiling-and-the-maximum-candidate-steps-set-the-digest-volume.md))
  - 토큰 어림의 상수는 측정 전 값이다([digest Spec](specs/digest.md#후보-선별) digest-R15 ~ R22 · R44 · R45)
    - 코드 포인트 × 1.0
    - 기사 줄 80
    - 사건 몫 40
    - 지시 6,000. 지시 · 주요 뉴스 칸이 가득 찬 출력 스키마 · 다시 묻는 진단의 몫이고, 시험으로 잰 가장 긴 경우는 5,100 이다
    - 프로필 태그 20
    - 사용자 구획 고정 틀 200
  - 후보 선별의 다른 상수도 측정 전 값이다([digest Spec](specs/digest.md#후보-선별) digest-R15 ~ R22 · R44 · R45)
    - 발행 시각 하한 24시간
    - `low_confidence` 가중치 0.7
    - 사건의 주제 · 지역을 정하는 몫의 문턱 0.5
    - 사건당 넘기는 기사(표본) 5
  - 추론 강도의 기본 보통([user Spec](specs/user.md#관심-설정) user-R59)도 측정 전 값이다
  - M6 측정에서 토큰 어림 상수는 그대로 두었다. 비율 1.0 으로 센 후보 몫이 실제 입력과 거의 같았고(Anthropic), 남는 몫은 지시 몫에서 나온다
  - 단계 값 · 예산 · 요약 길이마다의 길이 기준([digest Spec](specs/digest.md#프롬프트-구성) digest-R26)은 바꾼 값으로 다시 재서 정했다
    - 후보가 모자란 날이 아니면 사건 수가 고른 단계와 같았다
    - 예산은 자르지 않았다
    - 요약 길이 단계마다 길이가 벌어졌다
- **12번 본문 추출 도입**
  - M6 품질 측정에서 보류했다. 요약문이 뉴스레터 도입부 · 잘린 장면 묘사 · 잘린 논지뿐인 기사 하나짜리 항목에서 요약이 빈약하거나 예고 문장 · 입력에 대한 말이 섞였다
  - 요약문이 주요 뉴스 요약 하한보다 짧은 항목은 주요 뉴스가 아니라 헤드라인이다([ADR-0353](adr/0353-an-item-whose-summaries-fall-short-of-the-top-story-summary-lower-bound-is-a-headline-the-next-item-takes-its-seat-and-the-count-is-recorded.md)). 요약문이 하한 이상이지만 논지 중간에서 끊긴 항목은 그 규칙이 거르지 않는다
- **21번 다이제스트의 큰 슬롯 수와 스토리라인 큰 판정 기준의 조정**
  - 알려진 모습
    - 큰 판정 기준의 처음 값은 정했다([ADR-0174](adr/0174-big-judgement-criterion-first-value-kept-by-operator-daily-preview-review-where-the-evaluation-set-cannot-separate-neighbours.md))
    - 기사가 적은 주말에는 큰 스토리라인이 없는 날이 있다
    - 포함으로 이어지지 않은 같은 사안의 두 스토리라인이 같은 날 각각 크다고 나온다. 합치기 기준에 못 미친 조각이나 나란한 스토리라인이다
    - 스토리라인이 없는 단발성 큰 일은 큰 판정이 보지 못한다
    - 하루 기준을 여러 날 기간에 그대로 쓴 모습도 조금만 봤다
  - 지금 값([ADR-0271](adr/0271-when-the-input-token-budget-cuts-candidates-the-big-slot-keeps-at-most-half-of-what-is-left-and-uses-what-subscribed-events-leave.md) · [ADR-0260](adr/0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md) · [ADR-0209](adr/0209-maximum-candidates-and-big-slots-are-user-settings-and-the-big-slot-takes-at-most-half.md))
    - 큰 슬롯 수는 사용자 설정(0 ~ 10, 기본 3)이다
    - 큰 스토리라인마다 사건 10 개까지다
    - 큰 슬롯 사건은 최대 후보 수의 절반까지다
    - 입력 토큰 예산으로 자를 때도 큰 슬롯 사건은 남는 사건의 절반까지다. 구독 사건이 하나라도 빠졌을 때다
    - 머리가 사용자의 거른 스토리라인(스토리라인 관심 −0.2 이하)인 큰 판정 행은 건너뛰고 다음 행이 큰 슬롯을 쓴다([ADR-0279](adr/0279-storyline-interest-multiplies-the-candidate-rank-and-flows-at-or-below-the-exclusion-threshold-leave-subscribed-candidates-and-the-big-slot.md))
  - M6 측정
    - 예산이 사건 수를 정할 때는 남는 사건의 절반이 큰 화제 몫을 정해 큰 슬롯 수가 결과를 바꾸지 못했다
    - 예산이 안전 상한이 된 뒤([ADR-0272](adr/0272-the-input-token-budget-is-a-safety-ceiling-and-the-maximum-candidate-steps-set-the-digest-volume.md))에는 큰 화제가 최대 후보 수의 절반까지 들어 뜻이 달라졌다. 새 분량으로 다시 재서 그대로 두었다
    - 큰 슬롯 수는 사용자 설정이고, 넓게 구독한 사용자에게는 큰 화제도 구독 주제 안에서 나온다
    - 같은 사안의 스토리라인 둘이 각각 큰 화제 항목이 되는 경우는 실제 다이제스트에서 보였다. 서로 다른 사건이라 결함으로 보지 않는다
- **24번 관심도 · 구독 제안 · 관심사 히스토리 · 스토리라인 관심의 값**
  - 값
    - 관심도 감쇠 · 가중치의 값
    - 구독 신호의 값 γ 는 주제 0.1 · 스토리라인 1/3 이다
    - 구독 제안 기준은 처음 0.35 다
    - 거절한 제안을 다시 내지 않는 기간은 처음 90일이다
    - 관심사 히스토리의 "관심 있음" 기준값 θ 는 처음 0.2 다. 생김 · 사라짐과 화면에 드러낼 주제를 정한다
    - 스토리라인 관심의 상수 집합. 주제와 다른 것은 γ 와, 반응이 앞선 무반응 노출을 대신한다는 것이다
    - 다시 본 스토리라인의 기간은 처음 7일이다
    - 거름 기준은 처음 −0.2 다. 스토리라인 거름과 큰 슬롯 사건의 주제 거름이 함께 쓴다
    - 관심 스토리라인 기준은 처음 0.35 다
    - 남길 기준은 처음 0.2 다
    - 주제 구독 밖 관심 스토리라인의 몫은 처음 구독 사건 자리의 0.2 다
  - M6 · M7 측정
    - M6 측정의 인스턴스에는 반응이 없어 반응이 관심도 · 순위에 주는 효과는 보지 못했다
    - M7 측정에서 구독만 한 주제가 관심 있음의 위쪽을 채웠다. 그래서 구독 신호를 노출 분모 밖 항으로 옮기고 주제의 γ 를 θ 보다 작게 정했다([ADR-0295](adr/0295-the-subscription-signal-of-interest-is-a-term-outside-the-exposure-denominator-and-the-topic-value-is-below-the-presence-threshold.md))
    - 싫어한 주제는 스토리라인 거름으로 나아져 주제 산식을 더 바꾸지 않았다
    - 구독 밖 주제의 큰 스토리라인이 스토리라인만 바꿔 거듭 들었다. 그래서 큰 슬롯에도 관심 가중치를 곱하고 주제 관심으로 거른다. 거름 기준은 스토리라인과 같은 값이다([ADR-0296](adr/0296-the-big-slot-takes-big-judgement-rows-in-the-order-of-intensity-times-the-interest-weight.md) · [ADR-0297](adr/0297-an-event-whose-mean-topic-interest-is-at-or-below-the-exclusion-threshold-does-not-enter-the-big-slot.md))
    - 구독 제안 기준은 처음 값 그대로 둔다
    - 같은 스토리라인을 말없이 본 뒤 누른 싫어요가 그 앞의 노출에 묽어져 거름에 닿지 못했다. 그래서 스토리라인 관심에서는 반응이 앞선 무반응 노출을 대신하게 했다([ADR-0298](adr/0298-storyline-interest-drops-silent-exposures-before-the-latest-reacted-exposure-per-flow-and-topics-and-tracking-are-left-out.md))
  - 스토리라인 관심은 가중치 곱과 거름으로 시작한다. 관심 스토리라인의 효과가 부족하면 가중치를 고칠지 관심 스토리라인에 자리를 보장할지 측정을 보고 정한다([ADR-0277](adr/0277-storyline-interest-is-counted-per-containment-tree-top-with-the-topic-formula-and-reactions-to-a-flow-seen-within-seven-days-go-to-the-storyline-only.md) · [ADR-0278](adr/0278-followed-flows-are-tops-at-or-above-the-follow-threshold-or-subscribed-less-the-excluded-and-the-keep-threshold-is-0-2.md) · [ADR-0279](adr/0279-storyline-interest-multiplies-the-candidate-rank-and-flows-at-or-below-the-exclusion-threshold-leave-subscribed-candidates-and-the-big-slot.md) · [ADR-0280](adr/0280-events-of-followed-flows-outside-the-subscribed-topics-enter-one-per-flow-within-a-share-of-the-subscribed-seats-as-interest.md))
- **35번 스토리라인 잇기의 점수**
  - 점수가 애매한 띠 안 후보는 Jev 의 스토리라인 소속 판정이 가르지만, 띠 밖은 점수만으로 정한다
  - 위 기준보다 높은 점수의 분야 모음 소속은 남고, 아래 기준보다 낮은 점수의 소속은 되찾지 못한다. 관계는 이 손실을 메우지 못했다
  - 띠 · 확률 기준의 처음 값은 정했다
  - 기사 한 건짜리 사건은 표본에서 따로 다룰 근거가 나오지 않았다
  - 재지 못한 것([ADR-0121](adr/0121-storyline-linking-scores-centroid-cosine-plus-idf-weighted-key-term-overlap-without-time-decay-jev-or-topic-tags.md) · [ADR-0194](adr/0194-storyline-linking-asks-jev-only-for-band-candidates-and-reuses-recorded-probabilities-until-either-side-doubles.md) · [ADR-0195](adr/0195-storyline-linking-band-and-probability-defaults-chosen-for-precision-by-simulator-grid-evaluation-instance-and-operator-sample-review.md))
    - 핵심 말 가중치 · 시간 창
    - 몇 주에 걸친 전개
    - 사건이 자라는 동안의 판정
    - 닫힌 사건이 섞인 잇기
    - 말의 흔함을 세는 범위
    - 실측 인스턴스의 하루 판정 비용 · 잇기 처리량
- **36번 스토리라인 관계의 기준과 관계 판정의 문구 · 입력**([ADR-0132](adr/0132-storyline-relations-are-judged-by-jev-on-candidate-pairs-and-linked-only-above-a-probability-threshold.md) · [ADR-0133](adr/0133-storyline-evaluation-closes-system-containment-per-layer-view-and-measures-relations-by-direct-pair-judgement.md))
  - 관계의 기준([storyline Spec §7](specs/storyline.md#7-설정))
    - 확률 기준
    - 한 사안 기준
    - 사건 수 하한
    - 후보 점수 기준
    - 시간 창
  - 고르고 잰 쌍이 같고 며칠치라 아래를 재지 못했다
    - 다른 기간에서의 품질
    - 시간 창보다 멀리 떨어져 이어진 스토리라인
    - 스토리라인이 자라는 동안 다시 물은 판정의 흔들림
  - 운영 기록에서 맺힌 관계의 다수는 상위 스토리라인과 그 하위 스토리라인으로 맞았다. 틀린 것은 같은 사안이 갈라진 두 스토리라인을 포함으로 이은 것과 한쪽이 분야 모음인 것이었다
  - 세 층으로 이어진 포함 고리도 같은 사안의 조각에서 나왔다
  - 대기가 빈 뒤의 평소 판정량은 재지 못했다
  - 함께 볼 것
    - 기간이 긴 평가 세트가 생기면 정답 관계로 재현율을 잰다
    - 이어짐이 포함으로 맺어지는 경계는 관계 기준 · 판정 문구와 함께 본다. 운영자는 관계를 하나씩 고치지 않는다([ADR-0137](adr/0137-storyline-relations-are-not-edited-one-by-one-by-operators-and-are-tuned-through-relation-criteria.md))
    - 한 사안 기준은 잇기가 여는 스토리라인의 품질을 다시 볼 때 함께 본다
- **37번 같은 원인에서 나온 나란한 스토리라인**
  - 관계 판정에 그 답이 없어 나란한 두 스토리라인은 관계 없이 따로 남는다. 상위가 이미 스토리라인으로 있을 때만 그 하위들로 드러난다
  - 같은 사안이 갈라진 것은 조각 답으로 합친다([ADR-0234](adr/0234-fragments-of-one-matter-are-recognised-by-the-relation-judgement-and-merged-above-a-merge-probability-without-a-way-back.md))
  - 평가 인스턴스에서 나란한 스토리라인을 묶어 보았을 때 맞게 묶인 사안이 적었고, 합쳐야 할 조각을 나란한 스토리라인으로 받는 일이 있었다([ADR-0131](adr/0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md))
- **38번 같은 사안의 조각을 합치는 기준**([ADR-0234](adr/0234-fragments-of-one-matter-are-recognised-by-the-relation-judgement-and-merged-above-a-merge-probability-without-a-way-back.md))
  - 기준은 합치기 확률 기준이다([storyline Spec §7](specs/storyline.md#7-설정))
  - 확실한 조각만 합치는 쪽으로 정해, 조각인데 둘로 남는 쌍이 있다
  - 같은 요청도 확률이 실행마다 조금씩 흔들려 기준 근처의 쌍은 합쳐지기도 하고 아니기도 하다. 합치기는 되돌리지 않는다
  - 기준을 정한 표본은 한 인스턴스 · 몇 주치다
- **39번 다이제스트 항목의 값과 사건 단위의 결과**([ADR-0260](adr/0260-digest-candidates-are-events-and-only-assigned-events-with-an-article-first-evaluated-in-the-period-enter.md) · [ADR-0262](adr/0262-chosen-events-are-grouped-into-items-by-containment-tree-top-code-splits-top-stories-and-headlines-the-llm-writes-per-item-and-an-item-has-one-badge.md) · [ADR-0263](adr/0263-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md))
  - 큰 슬롯 밖 주요 뉴스 수는 처음 5(측정 전)다([digest Spec](specs/digest.md#항목-묶기와-가르기) digest-R47). 올리면 주요 뉴스 칸의 공급자 키 한도([digest Spec](specs/digest.md#출력-읽기) digest-R4)를 함께 본다
  - 헤드라인 항목마다 LLM 이 제목 · 코멘트를 써 출력 토큰이 는다
  - 사건마다 기사 여럿을 넘겨 입력 토큰이 늘어, 예산 자르기가 사건을 더 뺄 수 있다
  - 선별 때까지 사건에 배정되지 않은 기사만으로 된 사건은 다이제스트에 오르지 않는다. 그 크기는 재지 못했다
  - 헤드라인의 출력 비용이 크면 헤드라인 항목 수에 상한을 두는 것을 견준다
  - M6 측정 뒤 남은 것으로, 주요 뉴스 수를 그날 항목 수에 비례하게 하는 것은 따로 다룬다. 구독 · 관심 순위 앞 5(요약문이 짧은 항목은 뺀다) 고정이라 항목이 많은 날 헤드라인만 는다
- **40번 사용자 스토리라인이 물려받는 이어짐 판정의 품질과 맞추기의 비용**
  - 사용자 스토리라인은 이어짐의 끝(헤드)마다 묶인다([ADR-0294](adr/0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md)). 그래서 잘못 맺힌 이어짐은 다른 스토리라인을 같은 헤드의 사용자 스토리라인에 넣거나 헤드를 옮긴다
  - 관계가 고쳐지면 사용자 스토리라인이 갈라져, 추적이 헤드를 가진 조각에 남는다
  - 갈라지기 전 꼭대기는 사용자 스토리라인마다 다시 맞춰져, 맞추기의 읽기가 사용자 스토리라인 수만큼 는다
  - 평상시(관계 판정 문구나 기준을 바꾸지 않는 동안) 맺힌 이어짐이 사라지는 빈도는 재지 못했다
  - 맞추기는 아래마다 신호 이력과 관계를 읽는다. 그 지연도 재지 못했다
    - 흐름 추적의 조회 · 저장
    - 다이제스트 선별
    - 관심사 히스토리
    - 구독 제안
  - M7 측정은 고정한 덤프로 돌아 관계가 바뀌지 않았고, 갈라짐은 관측하지 못했다
- **41번 사용자 스토리라인의 정리의 값과 품질**
  - 값([user Spec](specs/user.md#사용자-스토리라인의-정리) user-R69 ~ R71)
    - 한 호출의 사건 상한(처음 60)
    - 한 실행의 호출 상한(처음 20)
    - 권하는 글 길이(제목 60 · 흐름 요약 600 · 최근 전개 400 코드 포인트)와 그 두 배에서 자르기
    - 글 한 벌의 본문 토큰(처음 2,400)
    - 연속 실패로 쉬는 날(실패 수, 7일까지)
    - 추론 강도 낮음 고정
  - 재지 못한 것
    - 실제 모델이 권하는 길이를 지키는지와 잘린 글의 빈도
    - 지난 글에 새 사건을 더해 고친 글이 오래된 사용자 스토리라인에서 처음 전개를 얼마나 잃는지
    - 상한에 걸려 넣지 못한 사건의 영향
    - 추적 중인 사용자 스토리라인 목록이 제목을 읽으려고 정리 전체를 읽는 비용
    - 스토리라인에 묶인 지난 항목 조회의 비용
    - 사용자 스토리라인마다 갈라지기 전 꼭대기의 사건을 다시 넣는 비용([ADR-0294](adr/0294-a-user-storyline-is-one-per-head-and-a-top-before-a-fork-belongs-to-several-of-them.md))
  - 덤프를 다시 돌린 재생에서 고쳐 쓰는 방식의 입력 토큰은 처음부터 다시 쓰는 방식의 14 ~ 22% 였다
  - M7 측정에서 처음 정리는 실패 없이 만들어졌고, 측정 기간이 짧아 고쳐 쓴 글은 보지 못했다

## 확인이 필요한 외부 사실

| # | 내용 |
|---|---|
| 16 | 네이버 검색 API 약관 개정 공식 원문. 보도 기준으로 결정했으며 원문은 직접 확인하지 못했다 |
| 25 | pgvector 이미지(OS 계열을 맞춘 PG 18 태그)의 재빌드 주기 — PostgreSQL 보안 패치가 반영되는 속도 |

## 결정됨 (2026-10-25)

| # | 내용 | 반영 |
|---|---|---|
| 2 | v1 LLM 공급자: OpenAI(ChatGPT), Anthropic(Claude) — 중계 공급자 OpenRouter 를 더함 | [ADR-0008](adr/0008-v1-llm-providers.md) · [ADR-0202](adr/0202-openrouter-is-added-as-a-user-llm-provider-and-its-default-models-are-the-two-providers-models-under-openrouter-ids.md) |
| 3 | v1 로그인: 자체 로그인 | [ADR-0009](adr/0009-authentication-google-and-local.md) · [ADR-0123](adr/0123-v1-login-is-local-only-and-oidc-and-account-linking-come-after-v1.md) |
| 4 | 데이터 접근: Spring Data JDBC + JdbcClient | [ADR-0014](adr/0014-spring-data-jdbc.md) |
| 18 | 아키텍처 패턴, 기본 패키지 `net.yozmnews`, 컨트롤러는 각 모듈 안 | [ADR-0013](adr/0013-architecture-patterns.md) |
| 5 | 프론트엔드 세부: React Router, TanStack Query, Tailwind + shadcn/ui | [ADR-0005](adr/0005-frontend-react-spa.md) |
| 17 | 메일 발송: v1에서 하지 않음 | [ADR-0011](adr/0011-no-email-in-v1.md) |
| 6 | 서비스 이름: 요즘뉴스(Yozm News), 프로젝트 도메인 `yozmnews.net` (Cloudflare Registrar). 비상업 개인 프로젝트 성격이라 상표 리스크는 감수 | [ADR-0047](adr/0047-self-hosted-software-run-by-instance-operators.md) |

## 결정됨 (2026-10-27)

| # | 내용 | 반영 |
|---|---|---|
| 10 (요약문) | RSS 요약문은 기본 무기한 보존하고, 자동 삭제는 운영 설정으로 켠다 | [ADR-0029](adr/0029-indefinite-summary-retention-with-optional-deletion.md) |

## 결정됨 (2026-10-31)

| # | 내용 | 반영 |
|---|---|---|
| 13 | 저가 모델 기반 기본 티어: 두지 않는다 | [ADR-0047](adr/0047-self-hosted-software-run-by-instance-operators.md) |

## 결정됨 (2026-11-02)

| # | 내용 | 반영 |
|---|---|---|
| 15 | Jev 공식 자료 확인(확인한 사실은 아래 15번) | [evaluation Spec](specs/evaluation.md#구현과-로컬-대체물) · [ADR-0063](adr/0063-jev-called-through-one-system-one-schema-context-overflow-probed-with-empty-state-latest-model-alias-by-default.md) |

- **15번 Jev 공식 자료에서 확인한 사실**
  - 가격은 입력 100만 토큰당 $0.042 이고 출력은 무료다
  - 경로는 TypeSafe System One API(`POST /v1/systemone`)이고, OpenRouter 가 같은 스키마의 호환 경로를 둔다
  - Cloudflare · Vercel AI Gateway 경유는 공식 문서에 없다
  - 문맥 상한은 요청 전체 64k · 질문 하나 32k 토큰이고, 질문 수 상한은 없다
  - Choice 선택지는 1 ~ 255개, Score 수준은 1 ~ 10개다

## 결정됨 (2026-11-07)

| # | 내용 | 반영 |
|---|---|---|
| 27 · 20 (임베딩 모델) | 기본 로컬 임베딩 모델은 multilingual-e5-small int8 이고 빌드 때 받아 이미지에 담는다. OpenAI 호환 외부 API 를 설정으로 고를 수 있다. 후보의 크기 · 메모리 · 품질은 조사 자료에 있다 | [ADR-0090](adr/0090-article-embedding-defaults-to-an-in-app-local-model-with-an-openai-compatible-api-by-setting.md) · [ADR-0091](adr/0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md) · [임베딩 모델 조사](research/2026-11-embedding-models.md) |
| 22 | 사건 배정 입력(기사 벡터)의 보관: 그 기사의 사건이 닫힐 때까지, 열린 사건에 배정되지 않은 표현은 만든 뒤 시간 창 + 여유. 사건을 닫는 기준은 마지막으로 기사가 붙은 뒤 닫는 기간 · 연 뒤 최대 기간이다(값은 20) | [ADR-0088](adr/0088-article-features-kept-until-their-event-closes-and-unassigned-ones-for-the-window-plus-a-margin.md) · [ADR-0086](adr/0086-events-close-after-an-idle-period-since-the-last-join-or-a-maximum-period-since-opening.md) · [event Spec §5](specs/event.md#사건-정리) |

## 결정됨 (2026-11-08)

| # | 내용 | 반영 |
|---|---|---|
| 1 | 첫 체계의 태그 설명 · 중요도 기준 문구를 평가 세트로 정했다 — 주제 중분류 둘(의료·헬스케어 서비스 · 가족·관계)을 더하고, 설명은 제외 목록 없이 태그가 무엇인지만 짧게 쓴다. 중요도는 사안이 실제로 바꾸거나 일으킨 일의 크기와 영향 범위로 매긴다 | 기본 체계 파일 · [ADR-0099](adr/0099-importance-judged-by-what-the-matter-changes-and-its-reach-with-levels-written-as-reach-without-article-type-examples.md) |
| 7 | 주제 · 지역의 태그 기준 · 저장 기준은 표본 검수로 기본 체계 파일의 처음 값 그대로 둔다. 중요도 기준 문구는 1 과 같다 | 기본 체계 파일 |

## 결정됨 (2026-11-10)

| # | 내용 | 반영 |
|---|---|---|
| 26 | 로컬 임베딩(ONNX Runtime · DJL 토크나이저)은 linux-aarch64 네이티브를 담아 root 가 아닌 컨테이너에서 `/tmp` 에 풀려 돈다. arm64 실기에서 기동하고 로컬 모델로 추론하는지는 CI 의 arm64 러너가 PR 마다 확인한다 — 러너 CPU 에 없는 명령어 조합의 오래된 코어는 확인 범위 밖이다 | [ADR-0091](adr/0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md) · [ADR-0104](adr/0104-arm64-run-checked-on-arm64-runners-with-a-symmetric-deploy-check-per-architecture.md) · [개발 인프라 — CI](development/infrastructure.md#ci) |

## 결정됨 (2026-11-11)

| # | 내용 | 반영 |
|---|---|---|
| 20 (일부) | 사건 묶기의 기본값(정한 것은 아래 20번) | [ADR-0109](adr/0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md) · [event Spec §7](specs/event.md#7-설정) · [운영 설정 — 사건 묶기](operations/configuration.md#사건-묶기) |

- **20번(일부) 사건 묶기의 기본값**
  - 사건 기준(시간 창 · 위 · 아래 · 확률 기준) · 저장 기준의 기본값과 점수 산식의 반감기 · 핵심 말 가중치는 기본 임베딩 모델에 맞춘다
  - 반감기 · 가중치는 코드 상수로 둔다
  - 닫는 기간은 시간 창에 늦게 수집되는 기사를 덮는 여유를 더한 값이다
  - 기본 임베딩 모델은 그대로 두고, 견준 외부 모델 둘의 권장 기준은 운영 안내에 둔다
  - 값을 정한 방법과 판단의 이유는 [ADR-0109](adr/0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md)에 있다

## 결정됨 (2026-11-12)

| # | 내용 | 반영 |
|---|---|---|
| 28 | Jev 사건 소속 판정의 배정 판정과 병합 판정(정한 것은 아래 28번) | [ADR-0119](adr/0119-event-merge-does-not-ask-band-pairs-whose-prior-assignment-probability-is-below-the-probability-threshold.md) · [ADR-0120](adr/0120-event-membership-probability-threshold-default-chosen-by-merge-precision-among-values-indistinguishable-in-assignment.md) · [ADR-0109](adr/0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md) · [event Spec §5](specs/event.md#사건-정리) event-R13 · [event Spec §7](specs/event.md#7-설정) |

- **28번 Jev 사건 소속 판정의 배정 판정과 병합 판정**
  - 병합은 배정 때 이미 확률 기준 미만의 답을 받고 갈린 쌍을 묻지 않는다
  - 확률 기준의 기본값은 병합 판정의 정밀이 높은 값으로 다시 골랐다
  - 병합 판정의 품질이 더 필요해지면 사건 대 사건을 묻는 질문을 다음 대안으로 본다
  - 판단의 이유와 평가 세트의 실측은 [ADR-0119](adr/0119-event-merge-does-not-ask-band-pairs-whose-prior-assignment-probability-is-below-the-probability-threshold.md) · [ADR-0120](adr/0120-event-membership-probability-threshold-default-chosen-by-merge-precision-among-values-indistinguishable-in-assignment.md) · [ADR-0109](adr/0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md)에 있다

## 결정됨 (2026-11-18)

| # | 내용 | 반영 |
|---|---|---|
| 34 | 스토리라인에 주제 · 지역 태그를 주지 않는다. 스토리라인 구독은 주제별 관심도에 넣지 않고 스토리라인 관심(사용자 스토리라인마다, [ADR-0283](adr/0283-storyline-interest-is-keyed-by-user-storyline-and-its-value-is-spread-to-each-top-for-selection.md))에 넣는다. 주제가 필요한 곳이 없다. 스토리라인을 주제로 거르는 화면이 생기면 저장을 다시 본다 | [ADR-0277](adr/0277-storyline-interest-is-counted-per-containment-tree-top-with-the-topic-formula-and-reactions-to-a-flow-seen-within-seven-days-go-to-the-storyline-only.md)(이 ADR 이 [ADR-0214](adr/0214-a-storyline-subscription-enters-topic-interest-by-the-topic-shares-of-its-event-representatives.md)를 대체한다) |
