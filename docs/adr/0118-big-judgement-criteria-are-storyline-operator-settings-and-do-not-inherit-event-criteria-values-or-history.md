# ADR-0118: 큰 판정 기준은 `storyline` 의 운영자 설정으로 두고 사건 기준의 값 · 이력을 이어받지 않는다

- 상태: 승인
- 날짜: 2026-11-12
- 근거: [이슈 #124](https://github.com/Theia0382/yozm-news/issues/124) — 큰 사건 기준의 저장 · 운영 API 와 `storyline` 의 설정 꼴을 조사해 리드와 정함
- 관련: [ADR-0083](0083-big-event-criteria-as-operator-settings-with-append-only-history-and-preview.md)(큰 사건 기준 — 전체 대체), [ADR-0116](0116-storyline-big-judgement-starts-with-a-provisional-rule-of-distinct-sources-and-mean-importance-judged-at-query-time.md)(큰 판정의 임시 산식), [ADR-0081](0081-event-assignment-criteria-in-settings-and-rejudgement-within-stored-scores.md)(사건 배정 기준은 설정), [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md)(기준을 둘 곳)

## 맥락

- 큰 사건 기준(소스 기준 · 중요도 기준)은 `event` 의 운영자 설정이었다 — 지금 값 한 행, 추가 전용 이력, 미리보기([ADR-0083](0083-big-event-criteria-as-operator-settings-with-append-only-history-and-preview.md)).
- 큰 판정은 스토리라인 단위이고 조회할 때 판정해 저장하지 않는다([ADR-0116](0116-storyline-big-judgement-starts-with-a-provisional-rule-of-distinct-sources-and-mean-importance-judged-at-query-time.md)). 기준값은 측정 전 목표이고 평가 세트와 운영자의 표본 검수로 맞춘다.
- `storyline` 의 잇기 기준 · 상태 기간은 설정(환경 변수)이다. 잇기는 판정 결과를 저장해, 판정마다 쓴 기준을 기준 이력으로 가리킨다.
- 스토리라인은 여러 사건의 기사를 모아 센다 — 같은 기준값이라도 사건 단위와 뜻이 다르다.
- 기준의 중요도 범위는 `evaluation` 의 중요도 수준 범위다.

## 결정

- **큰 판정 기준은 `storyline` 의 운영자 설정이다.** 지금 값 한 행과 추가 전용 이력(이전 값 · 새 값 · 행위자 종류 · 시각)을 둔다. 변경은 공개 운영 API 가 받는다 — 지금 값 행을 잠근 한 트랜잭션이고, 같은 값이면 바꾸지 않고 범위 밖이면 거부한다. HTTP · 화면은 `admin` 이 맡는다.
- **처음 값은 코드 상수이고 기동 때 지금 값이 없으면 넣는다.** 넣었을 때만 행위자 `SYSTEM` 의 이력 한 행을 남긴다. 처음 값은 측정 전 목표다.
- **미리보기는 제안 기준으로 기간 안 큰 스토리라인을 같은 판정으로 낸다.** 아무것도 쓰지 않는다.
- **사건 기준의 지금 값과 이력은 옮기지 않고, 그 테이블을 지운다.**
- 범위: 소스 기준 1 이상, 중요도 기준은 중요도 수준의 범위 안 · 소수 둘째 자리까지. 행위자 종류는 운영 설정이 함께 쓰는 공개 enum 이다.

## 결과

- 운영자는 재기동 없이 기준을 바꾸고, 바꾸기 전에 그 기준의 큰 스토리라인을 미리 본다.
- `storyline` 안에 설정 꼴이 둘이다 — 잇기 기준 · 상태 기간은 설정, 큰 판정 기준은 운영자 설정. 판정 결과를 저장하는지가 갈랐다.
- `storyline` → `evaluation` 의존이 생긴다(중요도 수준 범위).
- 판을 올리면 큰 판정 기준은 처음 값으로 시작하고, 운영자가 사건 기준을 조정한 이력은 사라진다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 설정(환경 변수 — 잇기 기준처럼)으로 두고 화면은 읽기 전용 · 미리보기만 | 표본 검수로 값을 맞추는 동안 바꿀 때마다 재기동하고, 누가 · 언제 바꿨는지 남지 않는다. 잇기 기준이 설정인 이유(판정이 저장되는 누적 상태)가 큰 판정에는 없다 |
| 사건 기준의 값 · 이력을 새 테이블로 옮긴다 | 사건 단위로 맞춘 값이 스토리라인 판정의 지금 값이 되고, 이력에 단위가 다른 값이 섞인다 |
| 새 기준은 처음 값으로 시작하고 사건 기준 테이블은 남긴다 | 코드가 읽지 않는 테이블이 스키마에 남아 정본이 흐려지고, 지울 계기가 다시 오지 않는다 |
