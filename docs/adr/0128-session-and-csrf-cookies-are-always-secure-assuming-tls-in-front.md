# ADR-0128: 세션 · CSRF 쿠키는 언제나 `Secure` 이고 앞단 TLS 를 전제한다

- 상태: 승인
- 날짜: 2026-11-13
- 근거: [이슈 #145](https://github.com/Theia0382/yozm-news/issues/145) — 기점 `a02a205` 의 배포 proxy 가 HTTP 만 받고(`frontend/proxy/Caddyfile`) TLS 를 앞단에 맡기며, Spring Session 4.1.1 의 쿠키 직렬화기가 따로 정하지 않으면 요청이 보안 연결일 때만 `Secure` 를 단다는 것을 확인
- 관련: [ADR-0006](0006-deployment-compose-cloudflare.md)(배포 — 앞단 Tunnel), [ADR-0126](0126-user-paths-authenticate-with-a-jdbc-session-holding-only-the-user-id-and-cookie-csrf-reading-the-account-per-request.md)(세션 · CSRF 쿠키)

## 맥락

- 앱은 proxy 에서 언제나 HTTP 로 요청을 받는다. 앞단(Cloudflare Tunnel · 리버스 프록시)이 TLS 를 맡는다는 것이 배포 문서의 전제다.
- 요청 스킴을 따라 `Secure` 를 정하면 앱이 보는 스킴이 언제나 HTTP 라 `Secure` 가 빠진다. 앞단이 넘긴 `X-Forwarded-Proto` 를 믿으려면 proxy 와 앱 모두에 신뢰 설정이 필요하다.
- 브라우저는 보안이 아닌 오리진에서 `Secure` 쿠키를 저장하지 않는다. `localhost` 는 예외다.

## 결정

- **세션 쿠키와 CSRF 토큰 쿠키는 요청의 스킴과 무관하게 언제나 `Secure` 를 단다.** 끄는 설정을 두지 않는다.

## 결과

- TLS 없이 HTTP 주소(사설망 IP 등)로 연 인스턴스에서는 로그인할 수 없다. 이 호스트의 `localhost` 로 여는 경우와 로컬 개발은 된다.
- 화면 확인처럼 컨테이너 네트워크에서 브라우저를 띄우는 확인은 `localhost` 로 접속해야 한다.
- 전달 헤더를 믿는 설정이 없어 신뢰 경계가 늘지 않는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 기본 `Secure`, 운영 설정으로 끈다 | 보안 기본값을 끄는 스위치를 켠 채 인터넷에 열 위험이 생기고 시험 갈래가 는다 |
| `X-Forwarded-Proto` 를 믿어 요청 스킴을 따른다 | proxy 와 앱에 전달 헤더 신뢰를 들여야 하고, 앞단이 헤더를 넘기지 않으면 `Secure` 가 조용히 빠진다 |
