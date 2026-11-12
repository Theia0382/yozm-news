# ADR-0123: v1 의 로그인은 자체 로그인 하나이고, OIDC 로그인과 외부 계정 연결은 v1 뒤로 미룬다

- 상태: 승인
- 날짜: 2026-11-12
- 근거: [이슈 #154](https://github.com/Theia0382/yozm-news/issues/154) — 기점 `079ec9b` 에서 OIDC 로그인이 코드(의존성 · 설정 · 보안 구성)에 없고 설계 문서 · 개발용 Compose · proxy 가 넘기는 경로에만 있음을 전수로 확인. 개발용 Compose · proxy 경로 정리 `1383cdb`
- 관련: [ADR-0047](0047-self-hosted-software-run-by-instance-operators.md)(셀프 호스팅 소프트웨어 — 이 결정의 맥락), [ADR-0009](0009-authentication-google-and-local.md)(Google 로그인 · 두 로그인의 세션 · 계정 연결 — 부분 대체), [ADR-0011](0011-no-email-in-v1.md)(복구 수단의 Google 계정 연결 — 부분 대체), [ADR-0012](0012-swappable-external-dependencies.md)(외부 로그인과 개발용 mock OIDC — 부분 대체), [ADR-0019](0019-dev-compose-per-checkout-and-test-image-source.md)(mock OIDC 의 고정 호스트 포트 — 부분 대체), [ADR-0048](0048-sign-up-closed-by-default-with-invitations-and-operator-bootstrap.md)(OIDC 로 가입 · OIDC 경로의 닫힘 · 처음 보는 OIDC 계정의 거부 문구 — 부분 대체), [ADR-0038](0038-container-images-use-major-tags-and-revisit-pinning-at-publication.md)(개발용 OIDC 공급자 이미지 — 보충)

## 맥락

- v1 의 로그인은 Google 로그인(OIDC)과 자체 로그인(이메일 · 비밀번호) 둘로 정해져 있었다([ADR-0009](0009-authentication-google-and-local.md)). 누구나 가입해 쓰는 공개 서비스를 전제로 한 결정이었다.
- 요즘뉴스는 개인이 자기 서버에 띄워 쓰는 셀프 호스팅 소프트웨어로 바뀌었다([ADR-0047](0047-self-hosted-software-run-by-instance-operators.md)). 한 인스턴스는 운영자가 허락한 몇 사람이 함께 쓰고, 사람을 들이는 길(초대 링크 · 운영자 계정 생성 — [ADR-0048](0048-sign-up-closed-by-default-with-invitations-and-operator-bootstrap.md))은 자체 로그인만으로 성립한다. 이 전환으로 OIDC 로그인은 당장 필요하지 않게 됐다.
- OIDC 로그인은 아직 코드에 없다. 미리 둔 것은 개발용 Compose 의 mock OIDC 서버와 proxy 가 앱으로 넘기는 `/oauth2/*` · `/login/*` 뿐이다. mock 서버는 `local` 실행마다 뜨고 고정 호스트 포트라 checkout 둘을 함께 띄우면 겹친다. 두 경로는 쓰임이 없고, 넘기는 동안 화면은 `/login/…` 아래 주소를 쓰지 못한다.

## 결정

- **v1 의 로그인은 자체 로그인(이메일 · 비밀번호) 하나다.** OIDC 공급자 로그인(기본 Google)과 외부 계정 연결은 v1 범위 밖이고 로드맵 "이후"에 둔다.
- **계정 복구 수단은 복구 코드다.** 초대 링크로 가입하는 사람도 자체 로그인으로 가입한다.
- **OIDC 를 위해 미리 둔 것을 걷는다.** 개발용 Compose 의 mock OIDC 서버를 빼고, proxy 가 앱으로 넘기는 경로는 `/api/*` 하나다.

## 결과

- 로그인 수단마다 이메일 신뢰가 갈리는 경우가 v1 에 없다. 자체 로그인의 이메일은 그대로 인증되지 않은 값이다([ADR-0011](0011-no-email-in-v1.md)).
- 복구 코드를 잃은 자체 로그인 계정은 복구할 수 없다 — 연결된 외부 계정으로 다시 들어오는 길이 없다.
- OIDC 를 다시 들일 때는 [ADR-0009](0009-authentication-google-and-local.md) · [ADR-0012](0012-swappable-external-dependencies.md) · [ADR-0048](0048-sign-up-closed-by-default-with-invitations-and-operator-bootstrap.md)의 설계(설정형 공급자 · 계정 연결 · 닫힌 가입의 거부)가 출발점이다. 개발용 공급자와 proxy 경로도 그때 다시 둔다.
- `local` 실행이 띄우는 개발 서비스는 PostgreSQL 하나이고 고정 호스트 포트가 없다.
- 화면이 `/login/…` 아래 주소를 쓸 수 있다. 자체 로그인 API 의 경로는 계정 인증을 만드는 작업이 정한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| v1 에 OIDC 로그인을 둔다 | 셀프 호스팅 소프트웨어로 바뀐 뒤에는 당장 필요하지 않다(사용자 결정) |
| OIDC 는 v1 밖으로 빼되 mock 서버 · proxy 경로는 남긴다 | 쓰지 않는 서비스가 개발 실행마다 뜨고, 쓰임 없는 경로가 화면 주소를 막는다 |
| proxy 의 `/oauth2/*` 만 빼고 `/login/*` 는 남긴다 | `/login/*` 를 앱에 넘길지를 계정 인증 작업보다 먼저 정하게 된다 |
