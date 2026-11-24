# ADR-0169: 요청 제한 · 봇 차단은 `platform` 의 처리기 표시를 `user` 세션 체인이 처리기 앞에서 요청 제한 → 봇 차단 순으로 걸고, 거부는 429 `rate-limited` · 403 `human-check-failed` 다

- 상태: 승인. 부분 대체 — 장치의 모듈([ADR-0308](0308-account-authentication-and-security-live-in-the-account-module-while-interests-and-notices-stay-in-user.md))
- 날짜: 2026-11-15
- 근거: [이슈 #152](https://github.com/Theia0382/yozm-news/issues/152) — 요청 제한 `bc453f5`, 봇 차단 `b998fa2`
- 관련: [ADR-0124](0124-security-configuration-lives-in-the-user-module-and-platform-keeps-only-the-screen-api-path-convention.md)(보충 — 요청 방어 장치도 보안 구성과 함께 `user` 에 있다), [ADR-0044](0044-screen-api-results-as-flat-discriminated-200-and-rfc9457-problems.md)(보충 — HTTP 오류에 429 를 더한다), [ADR-0158](0158-requests-of-session-users-without-a-key-are-closed-by-default-and-handlers-open-without-a-key-carry-a-platform-annotation.md)(같은 꼴 — `platform` 표시를 `user` 체인이 읽는다)

## 맥락

- 요청 제한은 `user`(로그인 · 복구 · 초대 · 가입)와 `credential`(키 등록 · 다시 검증) 두 모듈의 처리기에 걸린다. 봇 차단은 로그인 · 복구 · 공개 가입에 걸린다. 키 등록 · 다시 검증은 요청마다 공급자에 한 번 묻는다.
- 처리기의 공개 · 변경 전 허용 · 키 없이 허용은 이미 `platform` 의 표시를 `user` 의 세션 체인이 처리기 매핑으로 읽는 꼴이다. 보안 구성 전체는 `user` 의 것이다.
- `credential` 은 `platform` · `llm` 에만 의존한다.

## 결정

- **처리기 메서드에 `platform` 의 표시를 단다** — 요청 제한은 `@RequestLimited(동작)`, 봇 차단은 `@HumanChecked(동작)`. 동작은 `platform` 의 `GuardedAction` 이다. 표시는 메서드에만 달리고, `@HumanChecked` 는 같은 동작의 `@RequestLimited` 와 함께 단다.
> 부분 대체 — [ADR-0308](0308-account-authentication-and-security-live-in-the-account-module-while-interests-and-notices-stay-in-user.md). 지금 상태: [`account` Spec](../specs/account.md#요청-방어) account-R32
- **장치는 `user` 에 있다** — 세는 장치 · 기록 테이블 · 봇 차단 검증 포트와 구현 · 클라이언트 주소 읽기 · 적용 필터. `user` 의 세션 체인이 인가 뒤 · 처리기 앞에서 처리기의 표시를 읽어 건다 — 앞선 필터 · 인가가 거부한 요청은 세지 않는다.
- **순서는 요청 제한 → 봇 차단 → 처리기다.** 요청 제한에 걸린 요청은 봇 차단 검증(외부 요청)을 부르지 않는다. 봇 차단 토큰은 본문이 아니라 요청 헤더(`X-Human-Check`)로 받아 본문을 읽기 전에 거부한다.
- **거부는 문제 응답이다** — 요청 제한은 429 `urn:yozmnews:problem:rate-limited` 와 `Retry-After`(초), 봇 차단 실패는 403 `urn:yozmnews:problem:human-check-failed`. 모든 대상 경로에 같은 꼴이다. 명세 설정이 표시를 읽어 그 연산에 429 · 403 까닭을 단다.

## 결과

- 대상 목록이 처리기 옆의 표시로 코드에서 보이고, 등록 시험이 표시가 있는 연산을 손으로 적은 목록과 대조한다. 처리기 · 유스케이스는 바뀌지 않는다.
- 로그인 화면은 "기다려라"를 두 꼴로 받는다 — 이메일의 실패 지연(200 `WAIT`)과 이 네트워크의 요청 제한(429). 화면은 문구를 달리한다.
- `platform` 은 표시 · 판정만 갖고 테이블 · 외부 HTTP 를 갖지 않는다. `user` 가 커진다 — 나중에 체인 밖에서 요청 제한을 써야 하는 소비자가 생기면 그때 공개 API 를 연다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 각 처리기 · 유스케이스가 장치를 직접 부른다 | 같은 호출이 두 모듈에 반복되고, 공개 처리기의 거부가 본문을 읽은 뒤가 되며, 표시가 없어 누락을 시험으로 잡기 어렵다. `credential` 이 장치를 가진 모듈에 의존해야 한다 |
| 경로 표(설정)로 건다 | 경로 문자열이 처리기 매핑과 따로 사는 사본이다 — 경로를 바꾸면 조용히 빠진다 |
| 장치까지 `platform` 에 둔다 | 보안 구성은 `user` 의 것이라는 결정(ADR-0124)을 다시 연다. 장치의 소비자가 `user` 의 체인 하나뿐인데 공개 면을 연다 |
| 새 모듈이 장치를 갖는다 | 소비자 하나를 위한 모듈이 생기고, 보안 장치가 `user` 밖에 놓인다 |
| 로그인 · 복구의 요청 제한을 200 `WAIT` 에 합친다 | 같은 장치의 거부가 경로마다 다른 꼴이 되고, 체인에서 막은 요청을 유스케이스 결과처럼 꾸며야 한다. `WAIT` 의 뜻(이 이메일의 실패)이 흐려진다 |
| 클래스에도 표시를 달게 한다 | 같은 클래스의 다른 처리기(키 목록 · 삭제)까지 세게 된다 |
