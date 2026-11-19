# ADR-0251: 측정용 시계는 profile `measurement` 와 운영자가 넣는 DB 시계 행이 함께 있을 때만 뜨고, 운영자 API 로 앞으로만 넘긴다

- 상태: 승인
- 날짜: 2026-11-19
- 근거: [이슈 #223](https://github.com/Theia0382/yozm-news/issues/223) — 다이제스트 완료 확인([#182](https://github.com/Theia0382/yozm-news/issues/182))이 지난 데이터를 복원한 일회용 인스턴스에서 시계를 하루씩 넘기며 실제 LLM 으로 다이제스트를 받는다. `local` profile 은 다이제스트 생성기를 로컬 대체물로 바꾼다(ADR-0187). 구현 `ea5976c`
- 관련: [ADR-0057](0057-local-substitutes-registered-only-in-local-profile-without-operator-switch.md)(보충 — 같은 원리를 따른다), [ADR-0042](0042-app-roles-as-web-worker-profiles-marked-by-platform-annotations.md)(역할 규칙은 그대로 — 측정 profile 은 역할이 아니다), [ADR-0045](0045-admin-module-serves-operator-api-over-module-public-operation-apis.md)(운영자 API 는 `admin` 이 낸다), [ADR-0250](0250-the-apps-now-comes-from-one-appclock-bean-that-is-not-a-java-time-clock.md)(앱 시계)

## 맥락

- 측정용 시계로 쓴 행은 시각이 실제와 다르다. 운영 인스턴스에서 켜지면 데이터가 틀어지고, 측정 DB 를 운영으로 쓰면 같은 문제가 난다.
- 측정용 시계의 상태(출발 시각 · 넘긴 기록)를 메모리에만 두면 재기동 때 출발 시각으로 돌아간다. 시간이 뒤로 가면 이미 쓴 다이제스트 날짜 · 임대 만료가 미래가 된다.
- 로컬 대체물은 개발용 profile 로만 켜고 켜는 운영 설정을 두지 않는다(ADR-0057). 측정용 시계는 로컬 대체물이 아니지만 같은 위험(운영 설정 하나로 데이터가 틀어짐)을 갖는다.
- 일정 생성은 생성 시각 뒤 2시간(따라잡기 창) 안에서만 그 날짜를 만든다(digest-R29). 넘긴 구간에 창이 든 사용자는 그 날짜를 건너뛴다.

## 결정

- **측정용 시계는 profile `measurement` 와 DB 의 시계 행(`measurement_clock`)이 함께 있을 때만 뜬다.** 시계 행은 운영자가 측정 DB 에 SQL 로 넣는다(출발 시각). 켜는 설정 키 · 환경 변수는 없다.
  - 측정 profile 인데 시계 행이 없으면 기동하지 않고 넣을 SQL 을 알린다.
  - 시계 행이 있는데 측정 profile 이 없으면 기동하지 않는다 — 측정용 시계로 돈 DB 다.
  - 측정 profile 은 한 프로세스가 두 역할을 함께 할 때만 뜬다. 상태는 기동 때 DB 에서 읽고 넘길 때 DB 와 메모리를 함께 바꾸므로, 역할을 나눈 프로세스는 같은 지금을 보지 못한다.
  - 기동 판정은 `platform/domain` 의 `ClockStart` 가 한다. profile 이름의 정본은 `platform` 내부 애너테이션 `MeasurementOnly.PROFILE` 이다.
- **지금 = 출발 시각 + (실제 지금 − 출발에 대응하는 실제 시각) + 넘긴 길이의 합이다.** 대응 실제 시각은 처음 뜰 때 앱이 채운다. 출발 시각은 막지 않는다 — 지난 데이터보다 앞선 시각에서도 출발한다.
- **운영자가 API 로 앞으로만 넘긴다.** `platform` 이 공개 운영 API `MeasurementClockAdministration` 을 내고 `admin` 이 처리기(`GET /api/admin/measurement-clock` · `POST /api/admin/measurement-clock/advance`)를 낸다. 넘길 길이나 도착 시각 가운데 하나를 받는다. 길이는 0 보다 길고 도착 시각은 지금보다 뒤여야 한다. 넘기기 사이에는 실제 속도로 흐른다. 넘긴 구간은 DB 에 먼저 쓰고 메모리를 바꾼다.
- **처리기는 늘 등록하고, 측정용 시계가 아니면 409 로 거부한다.** 명세는 실행 환경과 무관하게 같다(platform-R6). 운영에서 시계를 넘기지 못하는 것은 측정용 시계 bean 이 없다는 사실이 막는다.
- **넘기는 폭은 제한하지 않는다.** 따라잡기 창은 `digest` 의 지식이라 `platform` 이 볼 수 없다. 쓰는 법 문서가 "시험 사용자의 생성 시각을 같게 맞추거나 2시간 이하로 나눠 넘긴다"를 적는다.

규칙은 [platform Spec](../specs/platform.md#앱-시계) platform-R27 · R29 에 있다.

## 결과

- 운영 인스턴스에서 켜려면 배포 정의의 profile 변경과 운영 DB 에 대한 SQL 이 둘 다 필요하다. 측정 DB 를 운영에 복원하면 덤프의 시계 행 때문에 기동하지 않는다.
- 운영 DB 에도 빈 표 둘(`measurement_clock` · `measurement_clock_jumps`)이 생긴다. 운영 인스턴스는 기동 때 시계 행이 있는지 한 번 본다.
- 운영 인스턴스의 운영자에게도 늘 409 를 내는 연산 둘이 보인다. 아무것도 바꾸지 못한다.
- 내려 있던 동안의 실제 시간도 측정 시각으로 흐른다.
- JobRunr 반복 간격 · Spring Session 만료 · 이벤트 발행 레지스트리 · 외부 공급자의 시간은 실제 시간 그대로다. 넘긴 뒤 다음 일정 회차는 실제 1분 안에 돈다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 측정 profile 만 두고 출발 시각은 설정 키로 받는다 | 운영 DB 에서 profile 을 켜면 바로 켜진다. 운영 문서에 출발 시각 설정 키가 나타난다 |
| 별도 이미지(측정 코드를 측정용 Dockerfile target 에만) | 빌드 target · 두 아키텍처 CI 빌드가 는다. 측정 코드가 `platform` 내부에 닿는 경계가 어색하고, 상태 보관을 따로 풀어야 한다 |
| `local` profile 을 쓴다 | `local` 은 다이제스트 생성기를 로컬 대체물로 바꿔(ADR-0187) 실제 LLM 생성을 잴 수 없다 |
| 날짜마다 출발 시각을 바꿔 재기동한다 | 재기동이 진행 중 작업을 끊고, 날짜마다 재기동을 기다린다 |
| 배속으로 흐르게 한다 | Jev · LLM 의 실제 지연이 배수만큼 늘어 임대를 넘고(24배면 180초 응답이 측정 시각 72분 — 생성 임대 40분), 기록하는 지연이 모두 틀어진다 |
| 처리기를 측정 profile 에서만 등록한다 | 측정 인스턴스에서 명세가 달라져 platform-R6 과 어긋난다. 조건 애너테이션을 `admin` 이 쓰도록 공개해야 한다 |
| 넘기는 폭을 2시간 이하로 제한한다 | `platform` 이 `digest` 의 따라잡기 창을 알아야 한다. 하루에 열두 번 넘게 불러야 한다 |
