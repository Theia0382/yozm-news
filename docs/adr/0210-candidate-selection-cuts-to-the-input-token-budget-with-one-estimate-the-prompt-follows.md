# ADR-0210: 입력 토큰 예산은 후보 선별이 `digest` 의 한 추정 규칙으로 세어 자르고, 프롬프트 구성이 그 규칙을 따른다

- 상태: 승인. 부분 대체 — 예산 산식의 사용자 구획 고정 틀([ADR-0219](0219-candidate-selection-hands-over-the-prompt-material-read-once-with-its-token-estimate.md)) · 예산 산식의 묶음 몫([ADR-0220](0220-big-slot-candidates-are-grouped-by-big-storyline-row-in-the-prompt-and-the-digest-llm-neither-makes-nor-links-groups.md)) · 후보 줄마다의 어림과 묶음 몫([ADR-0257](0257-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md))
- 날짜: 2026-11-17
- 근거: [이슈 #176](https://github.com/Theia0382/yozm-news/issues/176) — 요약문 상한(1,000 코드 포인트)과 기본 입력 토큰 예산(32,000)을 대조해 후보 60 건이 예산을 넘을 수 있음을 확인함. 구현 커밋 `b3cfac1` · `e828c70`
- 관련: [ADR-0209](0209-maximum-candidates-and-big-slots-are-user-settings-and-the-big-slot-takes-at-most-half.md)(최대 후보 수)

## 맥락

- 허용 모델마다 다이제스트 한 번의 입력 토큰 예산이 있다. 공급자의 입력 초과 오류는 종류로 갈리지 않아, 보내기 전에 예산으로 막아야 한다.
- 정확한 토큰 수는 공급자가 응답 뒤에 센다. 공급자 · 모델마다 토크나이저가 다르고 공개되지 않은 것도 있다.
- 프롬프트의 문면은 다른 작업(프롬프트와 실제 생성기)이 만든다.

## 결정

> 부분 대체 — 둘째 문장은 [ADR-0257](0257-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md), 셋째 문장은 [ADR-0219](0219-candidate-selection-hands-over-the-prompt-material-read-once-with-its-token-estimate.md) · [ADR-0220](0220-big-slot-candidates-are-grouped-by-big-storyline-row-in-the-prompt-and-the-digest-llm-neither-makes-nor-links-groups.md) · [ADR-0257](0257-articles-sent-per-event-are-sampled-up-to-a-cap-with-a-fixed-seed-from-those-in-the-period-and-the-sample-is-the-citations-and-sources.md). 지금 상태: [digest Spec "후보 선별"](../specs/digest.md#후보-선별) digest-R22
- **후보 선별이 후보마다 토큰을 어림해 예산 안까지 자른다.** 어림은 코드 포인트 × 1.0(제목 · 요약문 + 후보 줄의 고정 몫 80)이다. 예산은 모델의 입력 토큰 예산에서 지시 구획(2,000)과 사용자 프로필 구획(관심사 코드 포인트 + (구독 행 수 + 제외 지역 수) × 20)을 뺀 것이다. 값은 측정 전 목표다.
- **넘으면 구독 후보의 꼬리부터, 그래도 넘으면 큰 슬롯의 꼬리부터 뺀다.**
- **어림 규칙의 정본은 `digest` 의 `domain` 한 곳(`CandidateCost`)이고, 프롬프트 구성이 이 규칙을 따른다.**

## 결과

- 선별 결과가 곧 보낼 후보다. 후보 수 상한과 토큰 상한이 한 곳에서 정해진다.
- 어림이 실제 프롬프트와 어긋날 수 있다 — 다이제스트에 기록되는 실제 입력 토큰으로 비율 · 고정 몫을 고친다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 선별은 개수만 자르고 프롬프트 구성이 실제 문면으로 센다 | 상한이 두 작업에 갈리고, 꼬리를 자를 때 큰 슬롯을 지키는 규칙을 프롬프트 구성이 알아야 한다 |
| 토크나이저 라이브러리로 공급자마다 센다 | 공급자 · 모델마다 토크나이저가 다르고 공개되지 않은 것이 있다. 의존성이 는다 |
