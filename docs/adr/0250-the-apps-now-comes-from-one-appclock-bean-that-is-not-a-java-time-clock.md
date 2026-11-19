# ADR-0250: 앱의 지금은 `platform` 의 `AppClock` bean 하나에서 읽고, 그 bean 은 `java.time.Clock` 이 아니다

- 상태: 승인
- 날짜: 2026-11-19
- 근거: [이슈 #223](https://github.com/Theia0382/yozm-news/issues/223) — 기점 `3fc06f9` 에서 main 클래스 70개(12개 모듈)가 저마다 `InstantSource.system()` 을 만들었다. Spring Modulith 2.1.1 의 이벤트 발행 레지스트리가 `ObjectProvider<java.time.Clock>` 으로 시계 bean 을 집어 쓰는 것을 확인했다(`EventPublicationConfiguration`). 구현 `62f2b72`
- 관련: [ADR-0251](0251-the-measurement-clock-starts-only-with-the-measurement-profile-and-a-clock-row-and-moves-forward-through-the-operator-api.md)(측정용 시계), [ADR-0252](0252-the-stall-and-duration-of-work-in-progress-are-measured-as-elapsed-time-without-the-jumps-of-the-clock.md)(경과 판정)

## 맥락

- 앱은 시각을 모두 `InstantSource` 로 읽었다. 벽시계를 직접 읽는 다른 길(`Instant.now()` · SQL `now()` · 열 기본값)은 없었다. 다만 클래스마다 편의 생성자 · 결선에서 `InstantSource.system()` 을 만들었다. 그래서 앱 전체의 지금을 한 번에 바꿀 수 없었다.
- 여러 날에 걸친 동작(일정 생성 · 관심도 감쇠)을 지난 데이터로 확인하려면 측정용 시계가 앱 전체의 지금이 되어야 한다.
- 측정용 시계는 넘긴 시간을 뺀 경과를 따로 내야 한다(ADR-0252). `InstantSource` 하나로는 그 판정을 담을 수 없다.
- Spring Modulith 는 `java.time.Clock` 타입 bean 이 있으면 이벤트 발행 시각과 멈춤 감시(30분)에 쓴다. 측정용 시계를 넘기면 처리 중인 발행이 모두 멈춘 것으로 보여 다시 처리된다.

## 결정

- **앱의 지금은 `platform` 의 공개 타입 `AppClock` bean 하나에서 읽는다.** `AppClock` 은 `InstantSource` 를 상속하고 경과 판정(`elapsed` · `before` · `after` · `expiredBy`)과 `realTime()` 을 더한다. 지금만 읽는 곳은 그 bean 을 `InstantSource` 로, 진행 중 작업을 판정하는 곳은 `AppClock` 으로 받는다 — bean 은 하나이고 타입으로 보는 범위만 다르다.
- **main 소스에서 실제 시간의 원천을 만드는 곳은 `ClockConfiguration` 하나다.** 그 밖의 main 클래스는 `InstantSource.system()` · `Clock.system*` · 인자 없는 또는 시간대만 받는 `now()` · `System.currentTimeMillis()` · 인자 없는 `Date` 생성자를 부르거나 메서드 참조로 쓰지 않는다. ArchUnit 규칙(`ClockSourceTests`)이 막는다. 실제 경과를 재는 단조 시계(`System.nanoTime`)는 지금을 읽는 곳이 아니라 규칙 밖이다.
- **시계 bean 은 `java.time.Clock` 이 아니다.** Spring Modulith 의 이벤트 발행 레지스트리는 자기 기본 시계(실제 시간)를 쓴다.
- 경과 산식의 정본은 `platform/domain` 의 `ClockJumps` 다. 실제 시간의 시계(`SystemClock`)는 넘긴 기록 없는 같은 산식을 쓴다.

규칙은 [platform Spec](../specs/platform.md#앱-시계) platform-R26 에 있다.

## 결과

- 편의 생성자 60개를 지웠고, 인자를 바꿔 넘기는 편의 생성자 9개와 결선 메서드 하나는 시계를 인자로 받는다. 시험은 시험용 생성자 그대로 시계를 넣는다.
- 시험 컨텍스트를 잘라 띄우면 그 컨텍스트에 시계 bean 이 있어야 한다. Spring Modulith 의 모듈 시험은 띄우지 않은 모듈의 타입으로 선언된 bean 정의를 지운다. 그래서 모듈 시험의 시험 시계는 `InstantSource` 로 선언한다.
- 이벤트 발행 레지스트리의 시각은 측정용 시계를 따르지 않는다. 측정 인스턴스의 발행 · 완료 시각은 실제 시간이다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `InstantSource` bean 하나만 두고 공개 타입을 두지 않는다 | 넘긴 시간을 뺀 경과(ADR-0252)를 줄 자리가 없다 |
| 70곳 모두 `AppClock` 으로 받는다 | 지금만 읽는 곳에 경과 판정까지 보이는 넓은 타입이다. 같은 타입으로도 `instant().minus(임대)` 를 쓸 수 있어 실수를 막지 못한다 |
| 시계 bean 을 `java.time.Clock` 으로 둔다 | Spring Modulith 가 집어 써, 측정용 시계를 넘길 때 처리 중인 발행이 다시 처리된다 |
