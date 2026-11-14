# 미결 사항

결정되면 해당 문서와 ADR에 반영하고 여기서 지운다.

## 착수 전에 필요

| # | 질문 | 권장안 | 필요 시점 |
|---|---|---|---|
| 34 | 스토리라인에 주제 · 지역 태그를 줄지 — 사용자 관심사(구독 주제)와 스토리라인을 잇는 축 | 주제는 스토리라인과 다른 축이다([ADR-0101](adr/0101-shared-storylines-connect-events-and-the-personal-side-is-storyline-subscriptions-and-interests.md)). 스토리라인에 태그를 저장하지 않고, 쓸 때 구성 기사의 태그로 모으는 방식부터 한다 — 스토리라인은 사건이 붙고 합쳐질 때마다, 기사 태그는 태그 체계 · 기준이 바뀔 때마다 달라져 저장하면 그때마다 다시 맞춰야 한다. 좋아요 / 싫어요와 주제별 보도량은 기사에 붙어 기사 태그로 세므로, 스토리라인의 주제가 필요한 곳은 스토리라인 구독을 주제별 관심도에 넣는 일이다. 재료가 기사 단위라 `storyline` 은 직접 모으지 못한다 — 모으는 곳과 여러 주제에 걸친 스토리라인의 집계 규칙(주제마다 어느 만큼의 기사가 있어야 그 주제로 보는지)을 정한다. 스토리라인을 주제로 거르는 화면이 생기면 저장을 다시 본다 | M7 관심도 |

## 운영하면서 결정

| # | 질문 | 권장안 |
|---|---|---|
| 8 | 다이제스트 주기: 하루 1회 고정인가, 사용자가 여러 번 받을 수 있는가 | v1은 하루 1회 + 수동 재생성 |
| 9 | 기본 후보 수와 모델별 토큰 예산 | 60건에서 시작해 M6 측정 결과로 조정. 모델별 예산은 허용 모델 목록의 기본 파일에 잠정값으로 있다([llm Spec](specs/llm.md#공급자와-허용-모델-목록) llm-R4) |
| 10 | 보존 기간 ([data-model.md](data-model.md)) | 초안 값으로 시작. RSS 요약문은 결정됨(아래) |
| 11 | 사용자 피드 추가 허용 | v1은 불가. 열 때는 사용자당 피드 수 상한, 피드 볼륨 상한, 운영자 승인 중 하나 이상이 필요하다 (Jev 비용이 운영자 부담이므로) |
| 12 | 본문 추출 도입 | M6 품질 측정 후. 요약문이 거의 없는 소스(제목만 주는 피드)부터 검토 |
| 20 | 사건의 최대 기간(연 뒤 이만큼 지나면 새 기사가 있어도 닫는다 — 기본값은 측정 전 목표, [event Spec §7](specs/event.md#7-설정)). 나머지 사건 묶기 값은 결정됨(아래) | 평가 세트의 다시 묶기는 며칠치라 이 길이를 담지 못한다. 운영 기록에서 연 뒤 오래 이어지는 사건이 보이면 정한다 |
| 21 | 스토리라인의 큰 판정 기준과 다이제스트의 큰 슬롯 수 | M4 평가 세트와 운영자 표본 검수로 시작해 M6 측정으로 조정 |
| 24 | 관심도 감쇠 · 가중치, 구독 제안 기준 | 중립에서 시작해 M7 측정으로 정한다 |
| 35 | 스토리라인 잇기의 기준 점수 · 핵심 말 가중치 · 시간 창과 몇 주에 걸친 전개([storyline Spec §5](specs/storyline.md#잇기-규칙) · [§7](specs/storyline.md#7-설정)) — 평가 세트가 며칠치라 긴 전개, 사건이 자라는 동안의 판정, 말의 흔함을 세는 범위, 사건이 쌓였을 때의 말 수 조회 비용을 재지 못했다([ADR-0121](adr/0121-storyline-linking-scores-centroid-cosine-plus-idf-weighted-key-term-overlap-without-time-decay-jev-or-topic-tags.md)) | 평가 세트에서 고른 값으로 시작해 M4 실측의 운영 기록과 운영자 표본 검수로 다시 본다. 애매한 구간의 Jev 판정을 더할지도 그때 본다 |
| 36 | 스토리라인 관계의 기준(확률 기준 · 한 사안 기준 · 사건 수 하한 · 후보 점수 기준 · 시간 창 — [storyline Spec §7](specs/storyline.md#7-설정))과 관계 판정의 문구 · 입력 — 고르고 잰 쌍이 같고 며칠치라, 다른 기간에서의 품질, 시간 창보다 멀리 떨어져 이어진 흐름, 스토리라인이 자라는 동안 다시 물은 판정의 흔들림, 스토리라인이 쌓였을 때 점수 후보를 찾는 조회의 시간을 재지 못했다. 틀린 관계는 주로 한 사안 기준을 넘긴 주제 묶음에서 나왔고, 확률이 기준에 딱 닿아 맺힌 것도 있었다([ADR-0132](adr/0132-storyline-relations-are-judged-by-jev-on-candidate-pairs-and-linked-only-above-a-probability-threshold.md) · [ADR-0133](adr/0133-storyline-evaluation-closes-system-containment-per-layer-view-and-measures-relations-by-direct-pair-judgement.md)) | 쌍 판정 세트에서 고른 값으로 시작해 운영 기록과 운영자 표본 검수로 다시 본다. 기간이 긴 평가 세트가 생기면 정답 관계로 재현율을 잰다. 이어짐이 포함으로 맺어지는 경계는 관계 기준 · 판정 문구와 함께 본다 — 운영자는 관계를 하나씩 고치지 않는다([ADR-0137](adr/0137-storyline-relations-are-not-edited-one-by-one-by-operators-and-are-tuned-through-relation-criteria.md)). 한 사안 기준은 잇기가 여는 스토리라인의 품질을 다시 볼 때 함께 본다 |
| 37 | 같은 원인에서 나온 나란한 흐름을 묶는 상위 스토리라인을 시스템이 열지 — 지금은 상위가 이미 스토리라인으로 있을 때만 형제로 드러난다([ADR-0131](adr/0131-storyline-relations-are-explicit-links-with-containment-as-a-single-parent-tree-gathered-at-query-time.md)) | 운영 기록에서 상위 없이 남는 나란한 흐름이 얼마나 되는지 보고 정한다. 잇기가 여는 스토리라인의 품질(한 사안이 둘로 열린 것 · 주제 묶음)을 다시 볼 때 함께 본다 |
| 31 | 메일 발송 서비스 선택(메일을 도입할 때) | SMTP 로 붙인다([ADR-0012](adr/0012-swappable-external-dependencies.md)). 후보 Resend · Amazon SES · Postmark. 발송 도메인에 SPF · DKIM · DMARC 를 설정한다. 자체 메일 서버는 전달률을 위해 역방향 DNS 도 필요하다 |
| 32 | 키가 없는 사용자에게 Jev 평가 결과 기반 헤드라인 목록을 보여 줄지 | 보류 — 생성형 LLM 을 쓰지 않아 운영자 비용이 늘지 않는다. 지금은 키 등록 페이지만 보여 준다([ADR-0003](adr/0003-byok-personalized-digest.md)) |

## 확인이 필요한 외부 사실

| # | 내용 |
|---|---|
| 16 | 네이버 검색 API 약관 개정 공식 원문. 보도 기준으로 결정했으며 원문은 직접 확인하지 못했다 |
| 25 | pgvector 이미지(OS 계열을 맞춘 PG 18 태그)의 재빌드 주기 — PostgreSQL 보안 패치가 반영되는 속도 |

## 결정됨 (2026-10-25)

| # | 내용 | 반영 |
|---|---|---|
| 2 | v1 LLM 공급자: OpenAI(ChatGPT), Anthropic(Claude) | [ADR-0008](adr/0008-v1-llm-providers.md) |
| 3 | v1 로그인: 자체 로그인 | [ADR-0009](adr/0009-authentication-google-and-local.md) · [ADR-0123](adr/0123-v1-login-is-local-only-and-oidc-and-account-linking-come-after-v1.md) |
| 4 | 데이터 접근: Spring Data JDBC + JdbcClient (처음엔 JPA로 정했다가 변경) | [ADR-0014](adr/0014-spring-data-jdbc.md) |
| 18 | 아키텍처 패턴, 기본 패키지 `net.yozmnews`, 컨트롤러는 각 모듈 안 | [ADR-0013](adr/0013-architecture-patterns.md) |
| 5 | 프론트엔드 세부: React Router, TanStack Query, Tailwind + shadcn/ui | [ADR-0005](adr/0005-frontend-react-spa.md) |
| 17 | 메일 발송: v1에서 하지 않음 | [ADR-0011](adr/0011-no-email-in-v1.md) |
| 6 | 서비스 이름: 요즘뉴스(Yozm News), 프로젝트 도메인 `yozmnews.net` (Cloudflare Registrar). 비상업 개인 프로젝트 성격이라 상표 리스크는 감수 | [ADR-0047](adr/0047-self-hosted-software-run-by-instance-operators.md) |

## 결정됨 (2026-10-27)

| # | 내용 | 반영 |
|---|---|---|
| 10 (요약문) | RSS 요약문은 기본 무기한 보존하고, 자동 삭제는 운영 설정으로 켠다 | [ADR-0029](adr/0029-indefinite-summary-retention-with-optional-deletion.md) |

## 결정됨 (2026-10-31)

| # | 내용 | 반영 |
|---|---|---|
| 13 | 저가 모델 기반 기본 티어: 두지 않는다 | [ADR-0047](adr/0047-self-hosted-software-run-by-instance-operators.md) |

## 결정됨 (2026-11-02)

| # | 내용 | 반영 |
|---|---|---|
| 15 | Jev 공식 자료 확인 — 가격은 입력 100만 토큰당 $0.042(출력 무료). 경로는 TypeSafe System One API(`POST /v1/systemone`)이고, OpenRouter 가 같은 스키마의 호환 경로를 둔다. Cloudflare · Vercel AI Gateway 경유는 공식 문서에 없다. 문맥 상한은 요청 전체 64k · 질문 하나 32k 토큰이고, 질문 수 상한은 없다. Choice 선택지는 1 ~ 255개, Score 수준은 1 ~ 10개다 | [evaluation Spec](specs/evaluation.md#구현과-로컬-대체물) · [ADR-0063](adr/0063-jev-called-through-one-system-one-schema-context-overflow-probed-with-empty-state-latest-model-alias-by-default.md) |

## 결정됨 (2026-11-07)

| # | 내용 | 반영 |
|---|---|---|
| 27 · 20 (임베딩 모델) | 기본 로컬 임베딩 모델은 multilingual-e5-small int8 이고 빌드 때 받아 이미지에 담는다. OpenAI 호환 외부 API 를 설정으로 고를 수 있다. 후보의 크기 · 메모리 · 품질은 조사 자료에 있다 | [ADR-0090](adr/0090-article-embedding-defaults-to-an-in-app-local-model-with-an-openai-compatible-api-by-setting.md) · [ADR-0091](adr/0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md) · [임베딩 모델 조사](research/2026-11-embedding-models.md) |
| 22 | 사건 배정 입력(기사 벡터)의 보관: 그 기사의 사건이 닫힐 때까지, 열린 사건에 배정되지 않은 표현은 만든 뒤 시간 창 + 여유. 사건을 닫는 기준은 마지막으로 기사가 붙은 뒤 닫는 기간 · 연 뒤 최대 기간이다(값은 20) | [ADR-0088](adr/0088-article-features-kept-until-their-event-closes-and-unassigned-ones-for-the-window-plus-a-margin.md) · [ADR-0086](adr/0086-events-close-after-an-idle-period-since-the-last-join-or-a-maximum-period-since-opening.md) · [event Spec §5](specs/event.md#사건-정리) |

## 결정됨 (2026-11-08)

| # | 내용 | 반영 |
|---|---|---|
| 1 | 첫 체계의 태그 설명 · 중요도 기준 문구를 평가 세트로 정했다 — 주제 중분류 둘(의료·헬스케어 서비스 · 가족·관계)을 더하고, 설명은 제외 목록 없이 태그가 무엇인지만 짧게 쓴다. 중요도는 사안이 실제로 바꾸거나 일으킨 일의 크기와 영향 범위로 매긴다 | 기본 체계 파일 · [ADR-0099](adr/0099-importance-judged-by-what-the-matter-changes-and-its-reach-with-levels-written-as-reach-without-article-type-examples.md) |
| 7 | 주제 · 지역의 태그 기준 · 저장 기준은 표본 검수로 기본 체계 파일의 처음 값 그대로 둔다. 중요도 기준 문구는 1 과 같다 | 기본 체계 파일 |

## 결정됨 (2026-11-10)

| # | 내용 | 반영 |
|---|---|---|
| 26 | 로컬 임베딩(ONNX Runtime · DJL 토크나이저)은 linux-aarch64 네이티브를 담아 root 가 아닌 컨테이너에서 `/tmp` 에 풀려 돈다. arm64 실기에서 기동하고 로컬 모델로 추론하는지는 CI 의 arm64 러너가 PR 마다 확인한다 — 러너 CPU 에 없는 명령어 조합의 오래된 코어는 확인 범위 밖이다 | [ADR-0091](adr/0091-local-embedding-model-is-multilingual-e5-small-int8-fetched-at-build-into-the-image-with-onnx-telemetry-off.md) · [ADR-0104](adr/0104-arm64-run-checked-on-arm64-runners-with-a-symmetric-deploy-check-per-architecture.md) · [개발 인프라 — CI](development/infrastructure.md#ci) |

## 결정됨 (2026-11-11)

| # | 내용 | 반영 |
|---|---|---|
| 20 (일부) | 사건 기준(시간 창 · 위 · 아래 · 확률 기준) · 저장 기준의 기본값과 점수 산식의 반감기 · 핵심 말 가중치는 기본 임베딩 모델에 맞춰 평가 세트와 운영자 표본 검수로 정했다 — 반감기 · 가중치는 코드 상수로 둔다. 닫는 기간은 시간 창에 늦게 수집되는 기사를 덮는 여유를 더한 값으로 운영 기록으로 정했다. 기본 임베딩 모델은 외부 모델 둘과 견줘 그대로 두고, 그 둘의 권장 기준은 운영 안내에 둔다 | [ADR-0109](adr/0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md) · [event Spec §7](specs/event.md#7-설정) · [운영 설정 — 사건 묶기](operations/configuration.md#사건-묶기) |

## 결정됨 (2026-11-12)

| # | 내용 | 반영 |
|---|---|---|
| 28 | Jev 사건 소속 판정 가운데 배정 판정(기사 하나와 후보 사건의 기사들)은 평가 세트에서 같은 일과 다른 일을 가른다. 병합 판정이 같은 일인 사건 쌍을 대부분 합치지 않은 것은 질문의 꼴 때문이 아니라 병합에 올라오는 쌍의 구성 때문이었다 — 다수가 배정 때 이미 확률 기준 미만의 답을 받고 갈린 쌍이다. 병합은 그런 쌍을 묻지 않고, 확률 기준의 기본값은 병합 판정의 정밀이 높은 값으로 다시 골랐다. 병합 판정의 품질이 더 필요해지면 사건 대 사건을 묻는 질문을 다음 대안으로 본다 | [ADR-0119](adr/0119-event-merge-does-not-ask-band-pairs-whose-prior-assignment-probability-is-below-the-probability-threshold.md) · [ADR-0120](adr/0120-event-membership-probability-threshold-default-chosen-by-merge-precision-among-values-indistinguishable-in-assignment.md) · [ADR-0109](adr/0109-event-clustering-defaults-chosen-on-the-evaluation-set-with-operator-review-and-closing-from-operating-records.md) · [event Spec §5](specs/event.md#사건-정리) event-R13 · [event Spec §7](specs/event.md#7-설정) |
