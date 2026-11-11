# ADR-0026: 사건 묶기는 로컬 임베딩과 고유명사 산식으로 하고, 애매한 구간만 Jev 로 판정한다

- 상태: 승인. 부분 대체 — 접속 설정의 AI Gateway 경유([ADR-0063](0063-jev-called-through-one-system-one-schema-context-overflow-probed-with-empty-state-latest-model-alias-by-default.md)) · 사건보다 긴 전개를 잇는 단위([ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)) · 큰 사건 판정과 그 정밀도의 단위([ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md))
- 날짜: 2026-10-27
- 근거: [이슈 #36](https://github.com/Theia0382/yozm-news/issues/36) — 기점 `e4dbe0d` 의 설계 문서 · ADR 과 Spring Modulith 2.1.1 의 모듈 순환 판정을 대조해 확인
- 관련: [ADR-0025](0025-long-term-personal-news-history-in-v1.md)(공유 사건), [ADR-0002](0002-shared-evaluation-with-jev.md)(Jev 공유 평가 — 평가 항목 목록을 이 ADR 이 부분 대체한다), [ADR-0010](0010-tag-taxonomy.md)(질문 묶음), [ADR-0012](0012-swappable-external-dependencies.md)(교체 가능한 외부 의존성), [ADR-0027](0027-pgvector-for-vector-storage.md)(벡터 저장)

## 맥락

- [ADR-0025](0025-long-term-personal-news-history-in-v1.md)는 같은 사건 기사를 사용자와 무관하게 묶는 공유 사건을 두고, 운영자 비용으로 생성형 LLM 없이 묶기로 했다.
- 큰 사건은 여러 매체가 서로 다른 기사로 다룰 때 드러난다. 제목 유사도는 전재 기사만 잡으므로 사건의 크기를 적게 센다.
- 임베딩은 표현이 달라도 같은 사건을 잘 찾지만, 같은 주제의 서로 다른 사건(예: 다른 날의 금리 기사)도 가깝게 본다. 한국어 기사 제목은 인명 · 기관명 · 지명이 촘촘해서 고유명사의 겹침이 이 둘을 가른다.
- Jev 는 Yes/No 판정을 확률과 함께 내고, 한 요청에 질문 여러 개를 묶을 수 있다([ADR-0010](0010-tag-taxonomy.md)). 판정할 후보를 스스로 찾지는 못한다.
- 다이제스트는 사용자 일정에 따라 수시로 만들어지므로, 기사가 들어오는 대로 묶여 있어야 한다.
> 부분 대체(둘째 문장) — [ADR-0063](0063-jev-called-through-one-system-one-schema-context-overflow-probed-with-empty-state-latest-model-alias-by-default.md). 지금 상태: [evaluation Spec §7](../specs/evaluation.md#7-설정)
- Jev 를 부르는 곳은 `ArticleEvaluator` 하나다([ADR-0002](0002-shared-evaluation-with-jev.md), [ADR-0012](0012-swappable-external-dependencies.md) 의존성 표). 접속 설정(직접 · AI Gateway) · 로컬 대체물 · 판정 원본 기록이 그 관문에 있다.
- 묶기의 임계값을 정하려면 정답이 있는 표본이 필요하다. 실제 기사로 만든 표본은 언론사 콘텐츠다.

## 결정

- **묶기는 네 단계다.**
  1. 표현 — 기사 제목과 요약문 앞부분으로 임베딩을 만들고, 같은 텍스트에서 고유명사 · 핵심 명사를 뽑는다
  2. 후보 — 일정한 시간 창 안의 활성 사건 중 가까운 사건 몇 개를 고른다. 점수는 사건 중심과의 코사인 유사도, 고유명사 겹침, 시간 감쇠로 계산한다
  3. 판정 — 점수가 확실하면 산식으로 그 사건에 붙이거나 새 사건을 연다. 그 사이 애매한 구간만 Jev 에 "이 기사는 사건 X를 다루는가"를 묻고, 후보 사건 여럿을 한 요청에 묶는다
  4. 정리 — 주기적으로 잘게 쪼개진 사건끼리 합치고, 새 기사가 일정 기간 없는 사건을 닫는다
- **배정 기준은 사건 중심이다.** 사건 안의 기사 하나와만 닮아도 붙이는 방식은 사건이 번지기 쉽다.
> 부분 대체(둘째 문장) — [ADR-0101](0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md). 지금 상태: [`overview` §8](../overview.md#8-용어)
- 사건은 며칠 단위로 닫힌다. 그보다 긴 전개는 사용자별 이슈가 잇는다([ADR-0025](0025-long-term-personal-news-history-in-v1.md)).
> 부분 대체(판정의 단위) — [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md). 지금 상태: [event Spec 대표 기사 · 큰 사건](../specs/event.md#대표-기사--큰-사건) event-R15
- **큰 사건은 조회 시점에 판정한다.** 사건의 서로 다른 소스 수와 기사의 Jev 중요도로 판정하고, 임계값은 운영자가 정한다.
- **Jev 관문은 하나로 둔다.** 사건 소속 판정은 `ArticleEvaluator` 의 연산이다. 그 인자 · 반환 타입은 `evaluation` 이 소유한다 — `event` 의 타입을 받으면 `evaluation → event` 가 생겨 `event → evaluation` 과 순환이 된다. `event` 는 `evaluation` 의 공개 API 를 부른다. [ADR-0012](0012-swappable-external-dependencies.md) 의존성 표의 Jev 행(`ArticleEvaluator`)은 그대로 성립한다.
- **임베딩은 `event` 의 `ArticleEmbedder` 포트 뒤에 둔다.** 기본 구현은 로컬 모델(ONNX Runtime)이고, 설정으로 외부 임베딩 API 로 바꿀 수 있다. 개발 · 테스트는 로컬 대체물을 쓴다. [ADR-0012](0012-swappable-external-dependencies.md) 의존성 표에 없는 이 행을 이 ADR 이 기록한다.
- 고유명사는 형태소 분석기 Lucene Nori 로 뽑는다. Java 라이브러리라 별도 서버가 없다.
- 운영자 LLM 은 쓰지 않는다.
> 부분 대체(둘째 문장의 큰 사건 판정 정밀도) — [ADR-0103](0103-big-judgement-on-storylines-and-the-storyline-milestone-right-after-event-clustering-takes-over-big-events.md). 지금 상태: [로드맵](../roadmap.md)
- **임계값 · 시간 창 · 모델은 평가 세트로 정한다.** 수집한 실제 기사 표본에 사건 라벨을 붙이고 묶음 정확도(B-cubed F1)와 큰 사건 판정 정밀도를 잰다. 평가 세트의 실제 기사 텍스트는 공개 저장소에 넣지 않는다.
- 판정 원본(Jev 응답), 임베딩 모델 버전을 기록한다.

## 결과

- [ADR-0002](0002-shared-evaluation-with-jev.md)의 평가 항목 목록(카테고리 · 중요도 · 적합성)에 사건 소속 판정이 더해진다. "사용자 정보 없이 판정한다"는 그대로다.
- Jev 비용이 늘어난다. 애매한 구간만 묻는다. 기사 하나에 후보 사건 5개를 묻는 경우 입력 약 800토큰, 하루 4,000건이면 약 320만 토큰이고, 보도된 단가(입력 100만 토큰당 $0.042)로 하루 약 $0.13 이하로 추정한다. 측정 전 값이다.
- worker 가 임베딩 모델의 메모리 · CPU 를 쓴다. 앱 이미지에 처음으로 네이티브 라이브러리가 들어와, arm64 에서 실제로 실행되는지 확인해야 한다.
- 임베딩 모델 파일을 어떻게 배포할지(이미지에 포함 · 처음 기동 때 내려받기) 정해야 한다. 실행 단계에서 명령을 돌리지 않는 이미지 규칙([ADR-0022](0022-image-targets-per-side-dockerfile-without-run-in-runtime-stage.md))과 함께 본다.
- 묶기 품질이 다이제스트(큰 사건 슬롯 · 사건 단위 중복 제거)에 직접 영향을 준다. 운영자가 표본을 검수해 임계값을 조정한다.
- 기사가 들어오는 순서에 따라 묶음이 달라질 수 있다. 정리 단계의 병합이 이를 보완한다.
- 요약문이 없는 기사는 제목만으로 표현되어 묶기 품질이 낮아진다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 제목 유사도(L3)만 쓴다 | 전재 기사만 잡아 사건 크기를 적게 센다 |
| 다이제스트 LLM 에 맡긴다([ADR-0007](0007-defer-event-clustering.md)) | 묶음이 사용자 사이에 공유되지 않고 날짜를 넘어 이어지지 않는다 |
| 하루치 기사를 LLM 에 통째로 주고 묶게 한다 | 운영자 생성형 LLM 비용이 생기고, 묶음마다 사건 ID 가 달라 날짜를 넘어 이어지지 않으며, 재현되지 않는다 |
| 하루 한 번 배치 클러스터링만 한다 | 수시로 만들어지는 다이제스트와 맞지 않는다. 정리 단계의 보조로는 쓸 수 있다 |
| Jev 만 쓴다 | 후보를 찾을 수단이 없어 기사 쌍 전체를 물어야 한다 |
| 애매한 구간을 운영자 LLM 으로 판정한다 | 운영자 키와 생성형 LLM 비용이 생기고 셀프 호스팅 요건이 늘어난다. 피할 수 없고 비용이 적을 때만 다시 검토한다 |
| `event` 에 별도 판정 포트와 Jev 어댑터를 둔다 | Jev 를 부르는 곳이 둘이 되어 접속 설정 · 로컬 대체물 · 판정 기록이 두 곳에 생긴다 |
| 외부 임베딩 API 를 기본으로 쓴다 | 운영자 API 비용과 외부 의존이 생긴다. 포트 뒤에 두어 설정으로 바꿀 수 있게만 한다 |

## 후속 기록

- 2026-11-05: 결정 절 "Jev 관문은 하나로 둔다"의 인자 · 반환 타입 — [ADR-0071](0071-event-membership-judged-from-article-ids-with-questions-built-in-evaluation.md) · [evaluation Spec §5](../specs/evaluation.md#5-규칙과-불변식) evaluation-R37 · R38
- 2026-11-05: 결정 절 "고유명사는 형태소 분석기 Lucene Nori 로 뽑는다"의 분해 모드 · 고르는 품사 — [ADR-0076](0076-key-terms-from-nori-without-decompounding-proper-common-and-foreign-nouns.md) · [event Spec §5](../specs/event.md#기사-표현) event-R2
- 2026-11-06: 결과 절 "운영자가 표본을 검수해 임계값을 조정한다" — [ADR-0081](0081-event-assignment-criteria-in-settings-and-rejudgement-within-stored-scores.md) · [event Spec §5](../specs/event.md#기준과-재판정) event-R17 · R18
- 2026-11-07: 결정 절 "큰 사건은 조회 시점에 판정한다"의 판정 식 — [ADR-0082](0082-big-events-judged-at-query-time-by-distinct-sources-and-mean-importance-both-reaching-criteria.md) · [event Spec §5](../specs/event.md#대표-기사--큰-사건) event-R15
- 2026-11-07: 결정 절 "임계값은 운영자가 정한다"의 큰 사건 기준을 둘 곳 — [ADR-0083](0083-big-event-criteria-as-operator-settings-with-append-only-history-and-preview.md) · [event Spec §5](../specs/event.md#대표-기사--큰-사건) event-R21
- 2026-11-07: 결정 절 "4. 정리"의 병합 — [ADR-0084](0084-event-merge-applies-assignment-scoring-between-events-and-keeps-merged-rows-pointing-to-survivors.md) · [event Spec §5](../specs/event.md#사건-정리) event-R13
- 2026-11-07: 결정 절 "4. 정리"의 닫기와 "사건은 며칠 단위로 닫힌다" — [ADR-0086](0086-events-close-after-an-idle-period-since-the-last-join-or-a-maximum-period-since-opening.md) · [event Spec §5](../specs/event.md#사건-정리) event-R13
- 2026-11-07: 결정 절 "기본 구현은 로컬 모델(ONNX Runtime)이고, 설정으로 외부 임베딩 API 로 바꿀 수 있다" 의 방식 · 모델 · 배포 — [ADR-0090](0090-article-embedding-defaults-to-an-in-app-local-model-with-an-openai-compatible-api-by-setting.md) · [ADR-0091](0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md) · [event Spec §4](../specs/event.md#구현과-로컬-대체물)
- 2026-11-08: 결정 절 "임계값 · 시간 창 · 모델은 평가 세트로 정한다"의 평가 방법 — [ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md) · [사건 묶기 평가하기](../operations/event-evaluation.md)
- 2026-11-08: 결정 절 "평가 세트의 실제 기사 텍스트는 공개 저장소에 넣지 않는다"에 더한 라벨 · 결과 기록의 자리 — [ADR-0096](0096-instance-bound-evaluation-data-kept-out-of-the-public-repository-and-defaults-cite-only-the-evaluation-set.md) · [security.md](../security.md#저작권과-소스-이용조건)
- 2026-11-09: 결정 절 "묶기는 네 단계다"의 판정이 묻는 같은 사건의 범위 — [ADR-0102](0102-an-event-is-the-reports-of-one-happening-narrowed-by-the-event-membership-question-wording.md) · [evaluation Spec 사건 소속 판정](../specs/evaluation.md#사건-소속-판정) evaluation-R37
- 2026-11-11: 결정 절 "임계값 · 시간 창 · 모델은 평가 세트로 정한다"와 결과 절 "운영자가 표본을 검수해 임계값을 조정한다"의 첫 값 — [ADR-0109](0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md) · [event Spec §7](../specs/event.md#7-설정)
