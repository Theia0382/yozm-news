# ADR-0336: 변화 리포트의 관심 줄은 새로 생기거나 사라진 것만 고르고, 관심 변화 · 구독 변경이 없으면 관심 변화 섹션이 없다

- 상태: 승인
- 날짜: 2026-11-25
- 근거: [이슈 #242](https://github.com/Theia0382/yozm-news/issues/242) — 측정 인스턴스의 리포트에서 관심 표가 주제 40줄 · 흐름 최대 약 70줄이었고 대부분 "변화 없음"이었다. 관심 변화 재료가 별칭 · 출력 서술 · 입력 맞추기 · 사용자 구획 · 스냅숏의 공통 입력임을 코드에서 확인했다. 구현 `d467815`
- 관련: [ADR-0303](0303-a-change-report-covers-only-what-relates-to-the-user-in-three-sections-tracked-flows-subscribed-topic-coverage-and-interest-changes.md)(부분 대체 — 결과의 `EMPTY` 문장), [ADR-0311](0311-a-change-report-input-fits-the-smaller-of-the-model-budget-and-the-weekly-or-monthly-cap-by-a-share-for-earlier-reports-and-a-fixed-trimming-order.md)(부분 대체 — 덜어 내기의 "변화 없는 흐름 관심 줄" 단계), [ADR-0304](0304-a-change-report-takes-previous-values-from-the-previous-reports-snapshot-matched-one-to-one-and-keeps-them-in-its-own-snapshot.md)(스냅숏 꼴은 그대로)

## 맥락

- 관심 줄은 시작 · 끝 두 점 관심의 합집합 전부였다 — 관심이 쌓일수록 줄이 늘고, 리포트가 말할 "변화"는 그 가운데 몇 줄이다.
- 관심 변화 재료 하나가 별칭 · 출력 서술 · 입력 맞추기 · 사용자 구획 · 스냅숏의 공통 입력이다. 사용자 구획만 거르면 사용자 구획에 없는 흐름에 별칭이 매겨진다.
- 직전 값 잇기는 스냅숏의 보도량 수만 읽는다 — 관심 칸의 줄 수는 다음 리포트에 닿지 않는다.

## 결정

- **관심 줄 고르기 한 자리에서 주제 · 흐름 줄 가운데 새로 생기거나 사라진 줄(관심 있음 기준을 건넌 줄)만 낸다.** 재료 · 별칭 · 사용자 구획 · 스냅숏 · 화면이 모두 같은 줄을 쓴다. 구독 변경 줄은 그대로다.
- **관심 변화 섹션 종류는 변화 줄이나 구독 변경이 하나라도 있을 때만 든다.** 추적한 흐름이 없고 관심 변화 · 구독 변경도 없는 사용자는 리포트가 `EMPTY` 다 — 관심 값만 있고 변화가 없어도, 주제를 구독하고 있어도 그렇다([ADR-0339](0339-change-report-kinds-are-tracked-flows-and-interest-changes-and-it-leaves-out-subscribed-topic-coverage.md)).
- **입력 맞추기의 "변화 없는 흐름 관심 줄을 뺀다" 단계를 지운다** — 그 줄이 재료에 없다. 덜어 내기는 직전 글 → 추적하지 않는 흐름의 정리 → 사건 줄 → 흐름 요약 → 보도량이 적은 추적 흐름이다.
- 스냅숏 꼴은 그대로이고 관심 칸의 줄 수만 준다. 바뀌기 전에 만든 리포트는 저장된 대로 보인다.

규칙은 [report Spec](../specs/report.md#재료) report-R14 · R17, [직전 리포트와 스냅숏](../specs/report.md#직전-리포트와-스냅숏) report-R19 와 [입력 맞추기](../specs/report.md#입력-맞추기) report-R22 에 있다.

## 결과

- 관심 표와 관심 변화 섹션의 글이 그 기간의 변화만 다룬다.
- 관심이 조금 오르내려도 기준을 건너지 않으면 리포트에 나오지 않는다 — 관심의 크기와 흐름은 관심사 히스토리가 보인다.
- 관심 신호만 있는 사용자의 리포트가 `READY` 에서 `EMPTY` 로 바뀔 수 있다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| LLM 에는 변화 줄만, 화면은 나머지를 "변화 없는 관심 N개"로 접어 보인다 | 재료와 스냅숏에 다른 줄을 두려면 스냅숏 쓰기가 관심 줄을 따로 받아야 하고, 재료에서 걸러야 별칭 · 서술과 어긋나지 않는다 — 장치가 둘이 된다 |
| 지금대로 전부 싣고 지시로 덜 쓰게 한다 | 표가 수십 줄로 남고, 입력 맞추기가 줄을 덜어 내는 단계가 계속 필요하다 |
