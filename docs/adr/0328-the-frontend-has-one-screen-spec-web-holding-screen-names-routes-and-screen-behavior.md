# ADR-0328: 프론트엔드도 화면 Spec 하나(`web`)를 갖고, 화면 이름 · 화면 경로 · 화면 동작을 그 Spec 이 갖는다

- 상태: 승인
- 날짜: 2026-11-25
- 근거: [이슈 #281](https://github.com/Theia0382/yozm-news/issues/281) — 모듈 Spec §5 의 규칙 가운데 화면 동작을 말하는 문장이 account · reader · user 에 몰려 있었고, 공통 구조에 화면 동작을 둘 자리가 없었다. 구현 커밋 `c804fd3`(규범) · `decd52e`(화면 Spec 신설)
- 관련: [ADR-0034](0034-module-spec-as-public-interface-specification-and-docs-by-kind.md)(부분 대체 — 결정의 규칙 식별자 문장과 용어의 자리 문장), [ADR-0036](0036-module-spec-holds-internal-structure-and-port-declarations-in-eight-sections.md)(보충 — 여덟 절을 화면 Spec 이 같은 번호로 쓴다), [ADR-0320](0320-operator-screens-operations-docs-and-the-evaluation-tool-also-call-a-storyline-an-event-flow-and-a-big-one-a-big-topic.md)(부분 대체 — 결정 다섯째 항목의 대응 문장과 결과 첫 항목의 둘째 문장), [ADR-0326](0326-development-names-call-a-shared-storyline-storyline-a-user-storyline-user-storyline-and-a-judgement-candidate-of-unknown-kind-candidate.md)(부분 대체 — 결과 첫 항목의 둘째 문장)

## 맥락

- 2층 문서는 Spring Modulith 모듈 하나마다 Spec 하나였다. 프론트엔드에는 2층 문서가 없었다.
- 화면 동작(무엇을 언제 그리고 어디로 보내는가)은 서버 계약을 말하는 규칙 안에 섞여 있었다. `account-R20` 은 로그인 상태 조회의 응답과 화면의 이동 · 캐시 비우기를 한 규칙에 담았고, `reader-R16` 은 응답 칸과 리포트 화면의 상태 넷 · 버튼을 함께 담았다.
- 화면 동작의 판정 근거는 서버 규칙과 다르다. 서버 규칙은 시험이 지키고, 화면 동작은 UI 확인이 1차 근거다(`docs/qa/test-strategy.md` §3).
- 화면 이름("사건 흐름" · "흐름 추적" · "큰 화제")과 개발 용어의 대응은 `overview` §8 의 개발 용어 행 안에 문장으로 섞여 있었다.

## 결정

- **프론트엔드는 화면 Spec 하나(`docs/specs/web.md`)를 갖는다.** 모듈 Spec 과 같은 여덟 절을 같은 번호로 쓰고, 절의 뜻은 프론트엔드에 맞춘다 — §2 화면 이름, §3 화면 경로와 부르는 화면용 API, §4 기능 폴더, §5 화면 동작 규칙. 정본은 [`specs/README` §2 "화면 Spec"](../specs/README.md#화면-spec)이다.
- **화면 동작 규칙의 식별자는 `web-R{번호}` 다.** 규칙 식별자의 앞말은 모듈 이름 또는 `web` 이다.
- **모듈 Spec 의 규칙은 서버가 지키는 것만 쓴다.** 화면 동작은 화면 Spec 에 쓰고, 모듈 Spec 의 규칙은 그 `web-R` 식별자를 가리킨다. 서버가 내는 값 · 조회 범위를 말하며 화면을 소비자로 드는 문장은 모듈 Spec 에 남는다.
- **화면 이름과 개발 용어의 대응은 화면 Spec 의 §2 가 갖는다.** `overview` §8 은 개발 용어만 갖는다.

## 결과

- 화면 동작 규칙을 옮긴 원래 규칙(예: `account-R20` · `reader-R16`)은 식별자를 그대로 두고 그 규칙 전체를 덮는다. 옮긴 몫은 `web-R` 을 가리키므로, 원래 식별자를 인용한 코드 · 시험은 고치지 않아도 맞다.
- Spec 은 모듈 열여섯과 화면 하나다. `docs/README.md` §1 의 2층은 "모듈 · 화면 Spec" 이다.
- `overview` §8 이 개발 용어만 갖게 고쳐지기 전까지 화면 이름의 문장이 `overview` §8 과 화면 Spec §2 두 곳에 있다. 같은 작업이 `overview` §8 을 고친다.
- 화면 이름을 정한 ADR-0292 · ADR-0241 의 대체 표시가 가리키던 지금 상태는 화면 Spec §2 다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 화면을 엮는 백엔드 Spec(`reader` · `admin`)에 화면 하위 절을 둔다 | `reader` · `admin` 은 HTTP API 모듈이라 화면 동작과 경계가 다르고, 로그인 이동 · 알림함처럼 `account` · `user` 의 화면 문장은 자리가 맞지 않는다 |
| 문서에서 빼고 프론트엔드 코드와 ADR 만 정본으로 둔다 | 화면 동작이 여러 컴포넌트에 흩어져 한곳에서 읽을 수 없고, 화면 이름의 정본 자리가 계속 없다 |
| 화면 이름을 `overview` §8 에 열 하나로 둔다 | 탭 · 버튼처럼 화면에만 있는 이름이 1층에 들어온다 |
| 화면 Spec 을 기능 폴더마다 여러 개로 | 화면 이름 · 경로 · 로그인 이동처럼 여러 기능에 걸친 것이 자리를 잃는다 |
