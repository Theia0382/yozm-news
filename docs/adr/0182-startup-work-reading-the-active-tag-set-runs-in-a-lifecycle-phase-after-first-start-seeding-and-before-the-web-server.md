# ADR-0182: 기동 때 활성 태그 체계를 읽는 작업은 웹 서버 앞의 기동 단계에서 첫 기동 적재 뒤에 돈다

- 상태: 승인
- 날짜: 2026-11-15
- 근거: [이슈 #151](https://github.com/Theia0382/yozm-news/issues/151) — Spring Boot 4.1 의 웹 서버 시작 단계(`Integer.MAX_VALUE − 2048`)와 JobRunr 작업 서버의 시작 시점(`ApplicationReadyEvent`)을 확인
- 관련: [ADR-0054](0054-tag-set-seeded-from-default-yaml-on-first-start-with-immutable-versions-and-question-templates.md)(첫 기동 적재는 싱글톤을 모두 만든 뒤 · 웹 서버 전), [ADR-0139](0139-first-admin-is-created-only-from-environment-variables-and-startup-warns-without-an-admin-or-about-leftover-values.md)(첫 운영자는 기동 때 만든다)

## 맥락

- 첫 기동 적재(`tag`)는 `SmartInitializingSingleton` 에서 돈다. 계정을 만들 때 활성 체계의 형식 기본 제외를 넣으므로, 첫 운영자 생성과 기존 계정의 설정 채우기(`user`)도 활성 체계를 읽는다.
- `SmartInitializingSingleton` 끼리의 호출 순서는 빈 등록 순서이고 빈 의존과 무관하다 — 새 인스턴스의 첫 기동에서 첫 운영자가 적재보다 먼저 돌면 활성 체계가 없어 실패한다.
- `SmartLifecycle` 은 모든 `SmartInitializingSingleton` 뒤에 단계 순으로 시작한다. 단계를 정하지 않으면 기본값(`Integer.MAX_VALUE`)이라 웹 서버 뒤다.

## 결정

- **기동 때 활성 태그 체계를 읽는 작업(첫 운영자 생성 · 기존 계정의 설정 채우기)은 `SmartLifecycle` 의 기동 단계에서 돈다.** 단계는 웹 서버 시작 단계보다 작은 코드 상수 하나다.
- 첫 기동 적재가 `SmartInitializingSingleton` 단계라는 것은 이 작업이 기대는 계약이다.
- 기동 단계의 실패(첫 운영자의 기동 거부)는 앱을 띄우지 않는다.

## 결과

- 빈 등록 순서와 무관하게 첫 기동 적재 → 기동 단계 → 웹 서버 순서다. 작업 서버(JobRunr)는 그 뒤에 시작한다.
- 다른 모듈이 활성 체계를 읽는 기동 작업을 더하면 그 작업도 첫 기동 적재 뒤 · 웹 서버 앞의 `SmartLifecycle` 단계에서 돌아야 한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `@DependsOn` · 빈 등록 순서로 맞춘다 | `afterSingletonsInstantiated` 의 호출 순서는 빈 의존과 무관해 계약이 아니다 |
| `ApplicationReadyEvent` 에서 돈다 | 웹 서버가 먼저 요청을 받는다 |
| 첫 기동 적재를 싱글톤 생성 중(`@PostConstruct`)으로 옮긴다 | 첫 기동 적재의 자리(ADR-0054 "싱글톤을 모두 만든 뒤")를 바꾸고, 적재를 읽는 빈이 그 빈에 의존해야 한다 |
