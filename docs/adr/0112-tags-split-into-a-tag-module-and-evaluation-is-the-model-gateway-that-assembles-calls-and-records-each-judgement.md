# ADR-0112: 태그는 `tag` 모듈로 떼고, `evaluation` 은 판정마다 요청 조립 · 모델 호출 · 판정 원본 기록을 맡는 관문으로 둔다

- 상태: 승인
- 날짜: 2026-11-11
- 근거: [이슈 #130](https://github.com/Theia0382/yozm-news/issues/130) — `evaluation` 의 태그 지식과 판정 서비스를 나누는 경계를 조사하고, 사건 소속 판정이 `evaluation` 에 기대는 꼴과 같게 태그 판정을 두기로 리드가 정함
- 관련: [ADR-0055](0055-tag-and-store-thresholds-change-with-history-and-warn-instead-of-blocking.md)(태그 기준 · 저장 기준을 `evaluation` 이 갖는다는 문장 — 부분 대체), [ADR-0056](0056-platform-reads-yaml-text-and-applies-common-rejection.md)(기본 태그 체계 파일을 `evaluation` 이 읽는다는 문장 — 부분 대체), [ADR-0064](0064-topic-coverage-counts-articles-by-first-evaluation-time-with-query-time-judgement-over-key-lineage-in-the-app.md)(주제별 보도량을 `evaluation` 이 센다는 문장 — 부분 대체), [ADR-0071](0071-event-membership-judged-from-article-ids-with-questions-built-in-evaluation.md) · [ADR-0094](0094-event-membership-judgements-identified-by-id-read-by-operators-and-referenced-by-the-records-that-apply-them.md)(기사 ID 를 넘기고 판정 원본 ID 로 잇는 꼴을 태그 판정으로 넓힘 — 보충), [ADR-0073](0073-event-membership-api-in-both-roles-looks-up-the-evaluator-on-call-and-fails-without-one.md)(평가기를 부를 때 찾는 방식을 태그 판정 API 에도 씀 — 보충)

## 맥락

- `evaluation` 은 태그 체계 · 태그 기준 · 저장 기준 · 평가 대기 · 평가 결과 · 빠진 태그 채움 · 평가 현황 · 검수 같은 태그 지식과, Jev 관문 · 사건 소속 판정 같은 판정 서비스를 함께 가졌다.
- 그래서 사건 소속 판정만 쓰는 `event` 가 태그 지식을 대부분 가진 모듈에 기댔다. 관문 포트 `ArticleEvaluator` 도 태그 체계 타입(`TagSetView`)을 직접 받았다.
- 사건 소속 판정은 이미 `evaluation` 의 공개 API 를 거쳤다. 부르는 쪽이 기사 ID 를 넘기면 `evaluation` 이 기사를 읽어 질문을 만들고, 판정 원본을 기록해 그 ID 를 돌려주고, 부르는 쪽은 자기 기록에 그 ID 를 남긴다(ADR-0071 · ADR-0094).
- 빠진 태그 채움은 저장된 원본 응답을 평가기로 다시 읽는다. 원본 응답의 형식은 그 응답을 낸 평가기만 안다. 그래서 태그 쪽이 판정 쪽을 부르는 방향만 순환 없이 성립한다.
- 평가기 구현(Jev 요청 · 응답 해석, 로컬 대체물의 규칙)은 태그 판정의 질문 구조(주제 대분류 · 중분류 · 지역 · 형식 · 중요도)에 맞춰져 있다.

## 결정

- **태그는 `tag` 모듈로 뗀다.** 태그 체계 · 태그 키 계보 · 태그 기준 · 저장 기준 · 저장 바닥 · 평가 대기 · 평가 회차 · 평가 결과 저장 · 빠진 태그 채움 · 평가 현황 · 주제별 보도량 · 검수 · 기준 미리보기는 `tag` 가 갖는다. `tag` 는 `evaluation` 에 기대고, `evaluation` 은 `tag` 를 모른다.
- **`evaluation` 은 판정마다 공개 API 하나를 내는 관문이다.** 판정 연산마다 모델 요청 조립(질문 이름 · 선택지 · state 형식) · 모델 호출 · 응답 해석 · 결과 대조 · 실패 갈래를 맡는다. Jev 를 부르는 포트는 `ArticleEvaluator` 하나다.
- **태그 판정은 사건 소속 판정과 같은 꼴로 낸다.**
  - 부르는 쪽은 기사 ID 를 넘기고, `evaluation` 이 기사 내용과 피드 맥락을 읽는다.
  - 판정 원본(원본 응답 · 평가기 · 모델 버전 · 입력 토큰)은 `evaluation` 이 기록하고 그 ID 를 돌려준다.
  - `tag` 의 평가 행은 그 ID 만 갖는다. 다시 읽기와 평가기 · 모델 버전 · 입력 토큰 조회도 그 ID 로 `evaluation` 에 묻는다.
- **질문 문장과 입력 텍스트는 그 지식을 가진 쪽이 낸다.**
  - 태그 판정의 입력은 `evaluation` 소유의 질문 묶음 타입이고, 모양은 태그 판정이 묻는 구조(주제 두 단계 · 지역 · 형식 · 중요도)다. 질문 문장은 운영자가 바꾸는 `tag` 의 체계 데이터에서 온다. `tag` 의 체계 내용 타입이 이 질문 타입을 그대로 품어, 같은 모양의 타입을 두 벌 두지 않는다.
  - 사건 소속 질문 문구는 `evaluation` 의 코드 상수다(ADR-0072).
- **평가 결과를 알리는 `ArticleEvaluated` 는 `tag` 가 낸다.** 발행 시점 · 내용은 이 결정의 범위 밖이다.
- **지운 기사 따르기는 두 모듈이 각자 한다.** `tag` 는 대기 · 평가 · 태그 행을, `evaluation` 은 판정 원본을 자기 리스너로 지운다.
- 경계의 정본은 [overview.md §5](../overview.md#5-모듈-지도) · [tag Spec](../specs/tag.md) · [evaluation Spec](../specs/evaluation.md)이다.

## 결과

- 관문에 기대는 모듈(`tag` · `event`)이 같은 꼴로 기댄다. 다음 판정(스토리라인 잇기의 판정 등)도 판정마다 연산 하나와 판정 원본 테이블 하나로 더한다.
- 판정 원본과 판정 결과가 두 모듈의 두 트랜잭션에 걸친다. 결과를 반영하지 못한 판정(점유를 잃음 · 체계가 바뀜)의 원본도 남는다 — 사건 소속 판정과 같은 규칙이다.
- 평가 현황 · 검수는 판정 원본의 입력 토큰 · 모델 정보를 `evaluation` 에서 받아 붙인다. 흘려 받기 묶음마다 조회가 하나 는다.
- 회차가 기사 내용을 한 번에 읽던 것이 판정마다 `evaluation` 이 읽는 것으로 바뀐다. 읽는 시각이 판정 직전이라, 그사이 붙은 피드 엔트리가 입력에 든다.
- 판정 결과가 질문과 어긋나는 것은 `evaluation` 의 예외가 된다. 회차는 이것을 평가기의 예상 못한 예외와 같은 갈래로 멈춘다.
- 태그 체계 모양(태그 종류)이 바뀌면 질문 묶음 타입과 평가기 구현을 `evaluation` 에서 함께 고친다.
- 기존 평가의 원본 응답은 마이그레이션이 판정 원본 테이블로 옮긴다. 옮길 때 각 평가는 평가 ID 와 같은 ID 의 판정 원본을 받는다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| `tag` 가 원본 응답 · 모델 정보를 평가 행에 그대로 두고 `evaluation` 은 상태 없는 판정 API 만 낸다 | 데이터를 옮기지 않아도 되지만, 사건 소속 판정과 기대는 꼴이 갈린다. "판정 원본은 관문이 남긴다"가 판정마다 달라진다 |
| `evaluation` 이 대기 · 회차 · 채움을 갖고 `tag` 의 활성 체계를 읽는다 | `tag` 가 `evaluation` 을 부를 수 없어 저장 결과의 조회 시점 판정 · 저장 기준 거르기 · 채움이 `evaluation` 에 남는다. 태그 지식이 두 모듈에 갈린다 |
| 관문 입력을 태그를 모르는 일반 질문 목록(Yes/No · Choice · Score)으로 둔다 | 질문 이름 규약 · 응답 해석(중요도 수준 되찾기)이 `tag` 로 간다. 로컬 대체물의 태그 구조 규칙을 일반 규칙으로 바꿔 결과가 달라진다 |
| 부르는 쪽이 기사 내용(제목 · 요약 · 피드 맥락)을 넘긴다 | 회차의 일괄 읽기는 그대로지만, 사건 소속 판정과 입력 꼴이 갈리고 `tag` 가 `source` 에 기댄다 |
| 원본 응답만 판정 원본으로 옮기고 평가기 · 모델 버전 · 입력 토큰은 평가 행에 남긴다 | 판정 원본 하나가 판정의 사실을 다 담지 못하고, 다시 읽기에 필요한 평가기 이름이 두 곳에 놓인다 |

## 후속 기록

- 2026-11-11: 결정 절 "`evaluation` 은 판정마다 공개 API 하나를 내는 관문이다"의 연산 범위(판정 원본이 없는 기사 임베딩) — [ADR-0114](0114-article-embedding-is-a-gateway-operation-of-evaluation-taking-callers-texts-checking-results-and-recording-nothing.md) · [ADR-0115](0115-embedding-api-in-both-roles-looks-up-the-embedder-on-call-and-a-worker-startup-check-requires-one.md) · [evaluation Spec §5](../specs/evaluation.md#기사-임베딩)
