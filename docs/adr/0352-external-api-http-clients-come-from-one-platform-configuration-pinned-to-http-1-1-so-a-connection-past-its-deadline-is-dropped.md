# ADR-0352: 외부 API 를 부르는 JDK 클라이언트는 platform 공용 구성 하나로 만들고 HTTP/1.1 로 고정한다 — 마감에 걸린 연결을 버린다

- 상태: 승인
- 날짜: 2026-11-27
- 근거: [이슈 #140](https://github.com/Theia0382/yozm-news/issues/140) — 실측(2026-11-27, rootless Docker · 가짜 Jev 는 Caddy 의 TLS HTTP/2, 앱과 같은 클라이언트 구성 · 요청 방식의 탐침, 이미 맺은 연결만 iptables DROP). 구현 `6b11ab3`
- 관련: [ADR-0150](0150-user-keys-travel-over-an-own-jdk-transport-for-the-sdks-without-redirects-or-proxy-and-with-a-response-body-cap.md)(보충 — 리다이렉트 · 프록시 없음을 공용 구성이 정한다), [ADR-0172](0172-bot-checks-use-turnstile-or-off-pass-when-the-verifier-is-unreachable-and-match-the-action.md)(보충 — Turnstile 검증기의 클라이언트), [ADR-0204](0204-openrouter-keys-are-validated-with-the-key-lookup-request-not-the-model-list.md)(보충 — 키 조회가 가는 전송의 클라이언트), [ADR-0058](0058-evaluator-failures-in-three-kinds-and-unavailable-evaluator-skips-article-retries.md)(마감 실패는 일시 실패이고 재시도는 부르는 쪽)

## 맥락

- 외부 API 를 부르는 JDK `HttpClient` 는 넷이다 — `evaluation` 의 Jev 평가기 · 외부 임베더, `account` 의 Turnstile 검증기, `llm` 의 공급자 전송. 넷 다 연결 마감 · 리다이렉트 없음 · 프록시 없음을 각자 적었고 프로토콜 버전은 정하지 않았다(기본값 HTTP/2). 앞의 셋은 bean 하나가 클라이언트 하나를 계속 쓰고, llm 전송은 요청마다 만들고 닫는다.
- 호스트의 네트워크가 바뀐 뒤 한 인스턴스가 재시작 전까지 Jev 판정마다 응답 마감에 걸렸다. 같은 때 새 TCP 연결은 됐다.
- 재현 결과(응답 마감 15초, 요청 간격 2초) — 이미 맺은 연결의 패킷만 버리자 HTTP/2 클라이언트는 그 연결을 계속 다시 써서 요청 63개가 연달아 마감에 걸렸고, 15분 51초 뒤 커널이 연결을 포기하고 나서야 새 연결로 회복했다. 같은 조건의 HTTP/1.1 클라이언트는 요청 하나만 마감에 걸리고 다음 요청부터 새 연결로 정상이었다. 요청 간격이 40초면 HTTP/2 도 실패하지 않았다 — 요청 사이에 30초 쉬는 동안 연결이 닫혔다.
- 기제 — JDK 클라이언트는 HTTP/2 에서 목적지마다 연결 하나에 요청을 모두 싣고, 요청이 마감에 걸려 취소돼도 연결을 남긴다. 그 연결이 버려지는 길은 진행 중인 요청 없이 유휴 마감(`jdk.httpclient.keepalive.timeout`, 기본 30초)이 지나거나 커널이 재전송을 포기하는(`tcp_retries2`, 기본 15) 것뿐이다. HTTP/2 PING 을 켜는 설정은 없다. HTTP/1.1 에서는 마감에 걸려 취소된 요청이 그 연결을 닫는다.
- 신호 없이 죽은 연결은 rootless Docker 의 네트워크 전환만이 아니라 공유기 · NAT 장비 재시작으로도 생긴다. 붙잡는 시간은 Docker 방식이 아니라 앱이 도는 네트워크 이름공간의 `tcp_retries2` 가 정한다.
- Turnstile 검증기는 닿지 못하면 통과시키므로(ADR-0172), 죽은 연결을 붙잡는 동안 봇 차단이 조용히 꺼진다.

## 결정

- **외부 API 를 부르는 JDK `HttpClient` 는 `platform` 의 공용 구성(`ExternalHttpClients`) 하나로 만든다.** 구성은 HTTP/1.1 · 리다이렉트를 따르지 않음 · 시스템 프록시를 읽지 않음이다. 연결 마감 · 요청 마감 · 응답 본문 상한은 부르는 쪽이 정한다. 넷 다(llm 전송 포함) 이 구성으로 만든다.
- **프로토콜은 HTTP/1.1 로 고정한다** — 마감에 걸린 연결은 버려지고 다음 요청이 새 연결로 간다. 건강한 연결은 쉬는 동안 유휴 마감까지 다시 쓴다.
- main 에서 JDK 클라이언트를 만들거나 그 버전을 정하는 곳은 공용 구성 하나이고, 봉인 시험이 지킨다.

규칙은 [platform Spec](../specs/platform.md#externalhttpclients) platform-R135 에 있다.

## 결과

- 네트워크가 바뀌어 연결이 신호 없이 끊겨도 다음 요청 하나가 마감에 걸린 뒤 새 연결로 이어 간다. 재시작이 필요 없다. 연결 모음에 쉬는 동안 죽은 연결이 여럿 있었으면 그 수만큼 요청이 한 번씩 마감에 걸린다 — 일시 실패라 부르는 쪽이 다시 시도한다(ADR-0058).
- 동시에 나가는 요청 수만큼 연결 · TLS 핸드셰이크가 생긴다. 판정 · 임베딩 요청은 동시 수가 적다.
- 외부 API 클라이언트의 구성 사본 넷이 하나가 된다. 새 어댑터도 봉인 시험 때문에 이 구성을 거친다.
- 회복은 어댑터 셋(Jev · 임베더 · Turnstile)의 시험이 지킨다 — 시험은 HTTPS 전용 WireMock(HTTP/2 를 협상한다)과, 이미 맺은 연결을 신호 없이 멈추는 중계를 쓴다. 평문 WireMock 앞에서는 JDK 클라이언트가 본문 있는 POST 에 h2c 업그레이드를 하지 않아 결함이 드러나지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| HTTP/2 를 두고, 마감 · 입출력 실패가 나면 공유 클라이언트를 새로 만들어 바꾼다 | 공유 클라이언트를 원자적으로 바꾸는 상태 있는 장치가 생긴다. 서버가 느려서 걸린 마감에도 건강한 연결을 버린다. HTTP/2 가 지키는 이득(연결 하나에 요청 싣기)이 동시 수가 적은 판정 요청에서는 작다 |
| 요청마다 클라이언트를 새로 만들고 닫는다(llm 전송의 꼴) | 연결을 다시 쓰지 않아 요청마다 TCP · TLS 핸드셰이크와 클라이언트의 선택자 스레드가 생긴다. llm 전송은 키가 요청마다 달라 이 꼴을 골랐고, 공유 키로 부르는 Jev · 임베더 · Turnstile 에는 그 이유가 없다 |
| 어댑터마다 생성자에 HTTP/1.1 한 줄을 더한다 | 같은 구성의 사본 넷이 남고, 새 어댑터가 버전 줄을 빠뜨려도 막는 장치가 없다 |
| 유휴 마감(`jdk.httpclient.keepalive.timeout.h2`)을 줄인다 | 진행 중인 요청이 없어야 닫힌다. 요청이 이어지면(재현의 25초 간격) 쉬는 시간이 생기지 않는다 |
| TCP keepalive · `TCP_USER_TIMEOUT` 을 줄인다 | JDK `HttpClient` 가 소켓 옵션을 노출하지 않는다. 호스트 sysctl 은 운영자 환경이라 앱이 지킬 수 없다 |
| 운영 안내에 재시작만 적는다 | 실행 환경과 무관하게 오래 붙잡는다 |
