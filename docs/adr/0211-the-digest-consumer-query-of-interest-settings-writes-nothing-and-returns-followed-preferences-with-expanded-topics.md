# ADR-0211: 다이제스트가 읽는 관심 설정 조회는 쓰지 않고, 따라가기 결과와 펼친 주제 구독을 낸다

- 상태: 승인. 부분 대체 — 낸 값의 관심사 길이([ADR-0219](0219-candidate-selection-hands-over-the-prompt-material-read-once-with-its-token-estimate.md) · [ADR-0314](0314-the-free-text-interest-setting-is-removed-and-its-stored-values-are-dropped-by-migration.md))
- 날짜: 2026-11-17
- 근거: [이슈 #176](https://github.com/Theia0382/yozm-news/issues/176) — 선호 버전 · 따라가기 규칙 · 기존 소비자 조회의 꼴을 대조함. 구현 커밋 `641e7d4` · `4632bd5`
- 관련: [ADR-0180](0180-user-settings-changes-publish-no-event-and-consumers-query.md)(결과 "소비자용 공개 조회도 소비자의 작업이 연다"의 그 조회 — 보충), [ADR-0191](0191-the-consumer-query-of-storyline-subscriptions-is-opened-with-the-subscriptions-and-resolves-ids-without-writing.md)(같은 꼴 — 스토리라인 구독의 소비자 조회), [ADR-0175](0175-tag-preferences-follow-tag-set-activation-events-and-are-rewritten-to-active-keys.md)(따라가기)

## 맥락

- 사용자의 태그 선호는 선호 버전의 키로 저장된다. 활성 버전이 바뀌면 리스너가 사용자마다 고쳐 쓰고, 그 전의 틈은 사용자의 조회 · 저장 앞에서 따라간다. 따라가기는 키를 옮기는 것만이 아니다 — 부분 병합의 해제와 새 기본 제외 형식의 추가가 있다.
- 기사의 평가 키는 계보로 활성 키로 풀 수 있다. 다이제스트 후보 선별은 두 키를 같은 버전으로 맞춰야 한다.
- 대분류 구독은 하위 중분류 전부를 구독한 것이다. 주제 계보가 중분류를 다른 대분류로 옮기면 기사의 옛 대분류를 계보로 푼 값과 활성 버전의 상하 관계가 다를 수 있다.

## 결정

- **`user` 가 다이제스트용 소비자 조회(`DigestInterestsDirectory`)를 연다 — 쓰지 않는다.** 선호 버전이 활성 버전과 다르면 따라가기 규칙을 메모리에서 돌려 그 결과를 낸다. 설정 행이 없으면 계정을 만들 때의 기본값을 계산해 낸다. 저장 행 · 이력 · 알림은 리스너와 사용자의 조회 · 저장이 쓴다.
- **주제 구독은 구독 행마다 그 행이 덮는 활성 중분류와 함께 낸다** — 대분류를 펼치는 일도 `user` 가 한다. 기사는 중분류를 활성 키로 풀어 대조한다.
- **한 스냅숏에서 읽는다** — 잠그지 않으므로 읽기 전용 `REPEATABLE_READ` 트랜잭션 하나다. 옛 선호 버전과 새 버전 키를 섞어 읽지 않는다.
> 부분 대체 — 첫 문장은 [ADR-0219](0219-candidate-selection-hands-over-the-prompt-material-read-once-with-its-token-estimate.md) · [ADR-0314](0314-the-free-text-interest-setting-is-removed-and-its-stored-values-are-dropped-by-migration.md), 둘째 문장은 [ADR-0314](0314-the-free-text-interest-setting-is-removed-and-its-stored-values-are-dropped-by-migration.md). 지금 상태: [user Spec "다이제스트가 읽는 관심 설정"](../specs/user.md#다이제스트가-읽는-관심-설정) user-R57
- 조회는 이 작업이 쓰는 것(제외 · 관심사 길이 · 최대 후보 수 · 큰 슬롯 수)을 낸다. 시간대 · 관심사 본문 · 쓸 모델은 그것을 쓰는 작업이 더한다. 백그라운드 작업 전용이다.

## 결과

- 따라가기 계산과 대분류 펼치기의 정본이 하나다 — `digest` 가 그 규칙을 다시 구현하지 않는다.
- 리스너가 닿기 전의 틈에는 저장 행과 낸 값이 다르다. 다음 리스너 · 사용자 조회가 같은 결과로 고쳐 쓴다.
- 계정 상태(정지 등)는 보지 않는다 — 누구의 다이제스트를 만들지는 일정이 정한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 조회가 설정 행을 잠그고 실제로 따라가 쓴다 | 백그라운드 작업이 사용자 데이터를 고치는 길이 늘고 쓰기 트랜잭션 · 행 잠금을 선별마다 잡는다 |
| 선호 버전의 키를 그대로 내고 `digest` 가 계보로 푼다 | 부분 병합 해제 · 새 기본 제외 형식 추가를 `digest` 가 다시 구현한다 — 따라가기 규칙의 사본 |
| `digest` 가 대분류를 펼친다 | `digest` 가 태그 체계 구조의 값 타입(`evaluation`)에 의존한다 |

## 후속 기록

- 2026-11-18: 결정 "시간대 · 관심사 본문 · 쓸 모델은 그것을 쓰는 작업이 더한다" — 시간대 · 관심사 본문 · 요약 길이 · 추론 강도를 더했다([ADR-0219](0219-candidate-selection-hands-over-the-prompt-material-read-once-with-its-token-estimate.md) · [ADR-0223](0223-the-digest-summary-length-is-a-user-setting-in-the-digest-volume-section.md) · [ADR-0218](0218-the-digest-generation-reasoning-effort-is-a-user-setting-defaulting-to-medium.md)), 쓸 모델은 생성을 부르는 작업(#178)이 정한다. 지금 상태는 [user Spec "다이제스트가 읽는 관심 설정"](../specs/user.md#다이제스트가-읽는-관심-설정) user-R57
- 2026-11-18: 결정 "쓸 모델은 그것을 쓰는 작업이 더한다" — 쓸 모델은 이 조회에 더하지 않고 `user` 의 따로 된 조회(`DigestModelDirectory`)로 일정 · 생성 작업 · 수동 생성이 읽는다([ADR-0230](0230-scheduled-digests-are-made-after-the-generation-time-and-a-period-runs-from-the-last-ready-or-empty-end-to-that-time.md) · [user Spec "다이제스트 일정과 쓸 모델"](../specs/user.md#다이제스트-일정과-쓸-모델) user-R61)
