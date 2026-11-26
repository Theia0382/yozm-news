# ADR-0350: 테스트 JVM 은 메모리가 모자라면 바로 끝나고, 힙 덤프는 기본으로 남기지 않는다

- 상태: 승인
- 날짜: 2026-11-27
- 근거: [이슈 #158](https://github.com/Theia0382/yozm-news/issues/158) — 힙 상한을 1 GiB 로 낮춘 전체 테스트에서 메모리 부족을 일으켜 JVM 과 Gradle 의 동작을 확인함. 구현 커밋 `de083a6`
- 관련: [ADR-0349](0349-test-jvm-heap-cap-stays-at-least-twice-the-measured-live-heap.md)(힙 상한 — 경계에 가지 않게 하는 여유)

## 맥락

- JVM 은 기본으로 `OutOfMemoryError` 가 난 스레드만 끝낸다. 시험 스레드면 그 시험이 실패하고 실행이 이어지지만, 작업 서버 · 연결 풀 관리 같은 다른 스레드면 그 스레드를 기다리는 시험이 끝나지 않을 수 있다. 힙 상한이 없던 때 테스트 JVM 이 죽지 않고 멎은 실행이 있었다.
- 1 GiB 상한에서 G1 은 Full GC 를 775회 되풀이한 뒤에야 `OutOfMemoryError` 를 냈다. 정지 시간 133초 가운데 132초가 마지막 약 209초에 몰렸고, 그 동안 시험은 거의 나아가지 않았다.

## 결정

- **테스트 JVM 을 `-XX:+ExitOnOutOfMemoryError` 로 띄운다.** 어느 스레드에서 난 `OutOfMemoryError` 든 JVM 을 끝낸다.
- **힙 덤프는 기본으로 남기지 않는다.** 원인을 볼 때 손으로 붙인다.
- 설정은 `backend/build.gradle.kts` 의 시험 태스크에 있고, 동작은 [개발 인프라 — 테스트 JVM](../development/infrastructure.md#테스트-jvm)이 갖는다.

## 결과

- 메모리가 모자라면 JVM 이 종료 코드 3 으로 끝나고 Gradle 이 `Process 'Gradle Test Executor N' finished with non-zero exit value 3` 으로 시험 태스크를 실패시킨다(1 GiB 실측). 결과 XML 은 그때까지 돈 시험 클래스만 남으므로 결과 집계의 결손 대조가 드러낸다.
- 경계에서 Full GC 만 되풀이하는 구간은 이 설정이 막지 못한다 — `OutOfMemoryError` 가 날 때까지 이어진다. 그 경계에 가지 않게 하는 것은 [ADR-0349](0349-test-jvm-heap-cap-stays-at-least-twice-the-measured-live-heap.md) 의 여유다.
- 테스트 `TestJvmSettingsTests` 가 JVM 을 띄운 인자로 이 옵션이 켜졌는지 본다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| JVM 기본대로 둔다 | 시험 스레드 밖에서 난 `OutOfMemoryError` 로 실행이 멎는 갈래가 남는다 |
| 힙 덤프도 기본으로 남긴다(`-XX:+HeapDumpOnOutOfMemoryError`) | 덤프가 힙 상한보다 크다(1 GiB 상한에서 1.6 GB). 검사 컨테이너 안에 남아 꺼내야 볼 수 있고, 원인을 볼 때만 필요하다 |
