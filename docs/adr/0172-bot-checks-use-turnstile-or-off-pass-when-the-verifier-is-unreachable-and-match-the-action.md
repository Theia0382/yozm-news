# ADR-0172: 봇 차단은 Turnstile · 끔(기본 끔)이고, 검증 서비스에 닿지 못하면 통과하며 동작 이름을 대조한다

- 상태: 승인
- 날짜: 2026-11-15
- 근거: [이슈 #152](https://github.com/Theia0382/yozm-news/issues/152) — 봇 차단 `b998fa2`, 화면 `c196582`
- 관련: [ADR-0012](0012-swappable-external-dependencies.md)(보충 — "봇 차단" 행의 `HumanVerifier` · 끄기 · 개발 대체물을 구현한다), [ADR-0057](0057-local-substitutes-registered-only-in-local-profile-without-operator-switch.md)(로컬 대체물의 등록 조건), [ADR-0169](0169-request-limits-and-bot-checks-are-platform-marks-applied-by-the-user-session-chain-before-the-handler.md)(봇 차단을 거는 자리와 거부 응답)

## 맥락

- 봇 차단은 로그인 · 복구 코드 사용 · 공개 가입에 건다. 끄면 요청 제한만 남는다(ADR-0012).
- Turnstile 토큰은 화면의 위젯이 주고, 서버가 Cloudflare 에 확인해야 한다(토큰은 위조할 수 있다). 토큰은 300초 유효하고 한 번만 확인된다. 확인은 외부 요청이다.
- 위젯은 브라우저가 Cloudflare 에서 스크립트를 받아야 돈다.

## 결정

- **공급자는 Turnstile 과 끔이고 기본은 끔이다.** 켜려면 사이트 키 · 비밀 키가 모두 있어야 하고, 없으면 앱이 뜨지 않는다. 검증은 포트(`HumanVerifier`) 뒤 Turnstile 구현이 한다 — 주소는 코드 상수, 마감 · 본문 상한 · 리다이렉트 없음.
- **검증 서비스에 닿지 못하면(연결 · 마감 실패, 5xx, 읽지 못하는 본문) 통과시키고 경고 로그를 남긴다.** `success` 가 거짓이면(서비스 내부 오류 코드 포함) 언제나 거부한다. 비밀 키가 틀렸다는 답은 거부이고 오류 로그로 운영 설정을 알린다.
- **답의 `action` 을 처리기의 동작 이름과 대조한다** — 다른 화면의 토큰을 재사용하지 못한다. 시험 비밀 키의 답(`metadata.result_with_testing_key` 가 참)은 `action` 을 싣지 않으므로 대조하지 않는다 — 이 표시는 Cloudflare 가 시험 비밀 키에만 붙여 실제 비밀 키의 인스턴스에는 영향이 없다. `hostname` 은 앱이 인스턴스의 바깥 주소를 모르므로 보지 않는다.
- **거부는 동작과 까닭을 INFO 로그로 남긴다** — 봇의 거부는 흔하므로 경고로 올리지 않는다. 비밀 키 오류만 ERROR 다.
- **개발 대체물** — 로컬 개발(profile `local`)에서 비밀 키가 없으면 어떤 토큰에도 통과하는 검증기를 등록한다. 화면은 설정의 사이트 키(공식 시험 사이트 키)로 실제 위젯을 띄운다 — 화면 · 공개 API 에 개발 전용 갈래가 없다. 시험은 WireMock(검증기)과 답을 정하는 검증기 대역(앱 수준)을 쓴다.

## 결과

- Cloudflare 가 통째로 멈추거나 사용자의 네트워크가 Cloudflare 를 막으면 위젯이 돌지 않아 그 화면은 제출할 수 없다 — 통과는 "위젯은 돌았는데 서버가 검증 서비스에 닿지 못한" 경우에만 의미가 있다. 그동안 봇 차단은 끈 것과 같고 요청 제한은 그대로다.
- 봇 차단을 켜면 그 화면의 브라우저와 앱이 Cloudflare 에 연결한다 — 고지 페이지가 켠 인스턴스에서 이를 보인다.
- 개발에서 위젯 흐름을 보려면 인터넷이 필요하다. 오프라인이면 봇 차단을 끄고 개발한다.
- 다른 봇 차단 수단(hCaptcha 등)은 포트의 구현을 더해 붙인다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 검증 서비스에 닿지 못하면 거부한다 | 외부 서비스 장애가 모든 사용자의 로그인을 막는다 — 셀프 호스팅 인스턴스가 제3자 가용성에 묶인다 |
| 시험 비밀 키에도 `action` 대조를 지킨다 | 공식 시험 키로는 모든 확인이 거부되어, 운영자 · 개발자가 시험 키로 켜 본 인스턴스의 로그인이 막힌다 |
| `hostname` 도 대조한다 | 앱이 인스턴스의 바깥 주소를 알아야 해 운영 설정이 하나 는다. 위젯의 호스트 이름 제한은 Cloudflare 대시보드가 건다 |
| 로컬 개발에서 공개 API 가 `LOCAL` 을 알리고 화면이 고정 토큰을 보낸다 | 공개 API 열거에 개발 전용 값이 생겨 명세 · 생성 타입 · 화면 분기가 늘고, 사이트 키 없는 켬을 로컬에서만 받으려면 설정 판정이 대체물 유무에 기대야 한다 |

## 후속 기록

- 2026-11-27: 결정 절 "주소는 코드 상수, 마감 · 본문 상한 · 리다이렉트 없음"의 검증기 클라이언트 — [ADR-0352](0352-external-api-http-clients-come-from-one-platform-configuration-pinned-to-http-1-1-so-a-connection-past-its-deadline-is-dropped.md) · [`platform` Spec](../specs/platform.md#externalhttpclients)
