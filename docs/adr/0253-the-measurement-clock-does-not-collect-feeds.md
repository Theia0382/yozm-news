# ADR-0253: 측정용 시계에서는 수집하지 않는다

- 상태: 승인
- 날짜: 2026-11-19
- 근거: [이슈 #223](https://github.com/Theia0382/yozm-news/issues/223) — 수집은 앱의 시각을 실제 시간의 장치(JobRunr 예약 · 실제 대기 · 발행 시각 한도)에 넘긴다. 측정용 시계에서 과거에서 출발하면 오늘의 실제 기사가 지난 날짜로 저장되고 발행 시각이 버려지며, 수집을 멈춘 채 7일을 넘기면 켜진 피드가 STALE 이 되어 운영자 알림이 생기는 것을 확인했다. 구현 `2a82381`
- 관련: [ADR-0251](0251-the-measurement-clock-starts-only-with-the-measurement-profile-and-a-clock-row-and-moves-forward-through-the-operator-api.md)(측정용 시계), [ADR-0252](0252-the-stall-and-duration-of-work-in-progress-are-measured-as-elapsed-time-without-the-jumps-of-the-clock.md)(수집 임대는 그 규칙 밖), [ADR-0033](0033-feed-polling-db-lease-per-feed-new-items-and-per-source-request-spacing.md)(수집 점유)

## 맥락

- 측정용 시계의 쓰임은 지난 데이터를 다시 보는 것이다. 새 기사가 섞이면 측정이 오염된다.
- 돌던 인스턴스의 덤프에는 점유를 쓴 수집 상태와 그 점유의 예약 작업이 들어 있다. 측정 인스턴스의 작업 서버가 그 예약을 돌리면 토큰만 대조하는 점유 확인을 지나 실제 피드를 받는다.
- 수집 작업 bean 을 등록하지 않으면 덤프에 남은 반복 작업 행 · 예약 작업이 bean 없이 계속 실패한다. 그 행을 지우려면 `source` 가 JobRunr 행을 아는 측정 전용 기동 구성요소가 필요하다.

## 결정

- **수집은 앱의 시각이 실제 시간일 때만 한다(`AppClock.realTime()`).** 측정용 시계에서는 다음이 돌지 않는다 — 반복 작업과 예약 작업의 등록은 그대로 두고 회차가 건너뛴다.
  - 수집 점유(`PollScheduler.claimDueFeeds`)
  - 점유한 수집(`FeedPoller.poll` — 덤프에 남은 점유 · 예약 작업 포함). 점유는 그대로 두어 만료된다
  - 피드 상태 확인(`FeedHealthCheck.run`)
- **실제 피드를 요청하는 운영자 길은 요청 전에 거부한다** — 피드 켜기 미리보기(`SourceOperations`)와 운영자 원문 미리보기(`ProfilePreviewer`). `AppClock.requireRealTime()` 이 `platform` 의 `RealTimeRequiredException` 을 던지고 화면용 API 는 409 문제 응답으로 낸다.

규칙은 [source Spec](../specs/source.md#5-규칙과-불변식) source-R58 과 [platform Spec](../specs/platform.md#앱-시계) platform-R30 에 있다.

## 결과

- 소스를 켜 둔 채 측정해도 새 기사가 섞이지 않고 피드 상태 알림도 생기지 않는다. 덤프의 소스 켜기 상태 · JobRunr 행을 건드리지 않는다.
- 측정 인스턴스는 새 기사를 받지 않는다. 피드 상태는 덤프 시점 그대로 남는다.
- 수집 반복 작업은 1분마다 돌고 빈 회차로 끝난다(debug 로그).

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 측정 profile 에서 수집 작업 bean 을 등록하지 않는다 | 장치가 셋(`@WorkerRole` 과 AND 로 걸리는 조건 애너테이션 · 덤프의 반복 작업 행과 예약 작업 지우기 · 운영자 길의 실행 시 거부) 든다. 행 지우기는 측정 전용 기동 구성요소와 JobRunr 행 지식을 `source` 에 더한다 |
| 쓰는 법 문서에 "측정 전에 소스를 모두 끈다"만 적는다 | 빠뜨리면 오늘 기사가 지난 날짜로 조용히 섞여 측정이 오염된다. 덤프의 소스 켜기 상태가 바뀐다 |
| 측정 시각에서도 수집을 지원한다(예약 · 대기를 실제 시각으로 옮기고 발행 시각 한도를 실제 시간으로) | 받은 시각이 측정 시각이라 오늘 기사가 지난 날짜에 들어오는 의미 문제가 그대로다. `source` 에 측정 시각과 실제 시각을 오가는 지식이 퍼진다 |

## 후속 기록

- 2026-11-25: 맥락 절 "새 기사가 섞이면 측정이 오염된다"의 정리 재료 — [ADR-0327](0327-summary-materials-take-only-events-that-started-by-the-runs-now.md) · [user Spec](../specs/user.md#사용자-스토리라인의-정리) user-R70
