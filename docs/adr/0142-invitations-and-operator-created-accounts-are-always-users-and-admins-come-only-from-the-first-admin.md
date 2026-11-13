# ADR-0142: 초대와 운영자 계정 생성은 `USER` 계정만 만든다 — `ADMIN` 은 첫 운영자로만 생긴다

- 상태: 승인
- 날짜: 2026-11-13
- 근거: [이슈 #147](https://github.com/Theia0382/yozm-news/issues/147) — 구현 `4d3f932`
- 관련: [ADR-0048](0048-sign-up-closed-by-default-with-invitations-and-operator-bootstrap.md)(닫힌 가입의 두 방식 — 만드는 계정의 역할을 정한다), [ADR-0139](0139-first-admin-is-created-only-from-environment-variables-and-startup-warns-without-an-admin-or-about-leftover-values.md)(첫 운영자)

## 맥락

- 닫힌 가입의 두 방식(초대 링크 · 운영자 계정 생성)이 어떤 역할의 계정을 만드는지 정해지지 않았다([ADR-0048](0048-sign-up-closed-by-default-with-invitations-and-operator-bootstrap.md)).
- 초대 링크는 새면 누구나 한 번 쓴다. 운영자 역할을 주는 링크가 새면 인스턴스 전체가 넘어간다.
- 역할을 바꾸는 기능은 아직 없다.

## 결정

- **초대 가입 · 운영자 계정 생성 · 공개 가입은 언제나 `USER` 계정을 만든다.** 요청에 역할 칸이 없다.
- **`ADMIN` 계정이 생기는 길은 첫 운영자 하나다.**

## 결과

- 새어 나간 초대 링크 · 운영자 화면의 실수로 운영자가 늘지 않는다.
- 운영자를 더 두려면 역할을 바꾸는 기능이 필요하다 — 그 기능이 생기기 전에는 운영자 하나로 쓴다.
- 운영자 계정을 잃으면 첫 운영자 경로로 새로 만든다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 초대 · 운영자 계정 생성 때 역할을 고른다 | 새어 나간 운영자 초대 링크가 인스턴스 전체를 넘긴다. 운영자를 들이는 길이 늘어 지킬 면이 넓어진다 |
