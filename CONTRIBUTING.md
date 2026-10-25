# 기여 가이드

요즘뉴스 팀의 공용 규약이다. 브랜치 · 커밋 · PR · 이슈 · 개발 환경의 정본은 이 문서다. 설계 문서는 [docs/README.md](docs/README.md)부터 읽는다.

## 1. 개발 환경

### 필요한 것

- **JDK 25**
- **Docker** — 테스트가 Testcontainers 로 PostgreSQL 컨테이너를 띄운다
- Gradle 은 따로 설치하지 않는다 — 저장소의 wrapper(`backend/gradlew`)를 쓴다
- **Node.js 24** (`frontend/.nvmrc`)
- **pnpm** — 설치된 버전과 관계없이 `frontend/package.json` 의 `packageManager` 에 고정한 버전으로 실행된다

### 빌드와 테스트

저장소 루트에서 실행한다.

**백엔드**

```sh
backend/gradlew -p backend test                                  # 전체 테스트
backend/gradlew -p backend test --tests 'net.yozmnews.digest.*'  # 일부만
backend/gradlew -p backend build                                 # 컴파일 + 테스트 + 검사
```

결과는 `backend/build/test-results/test/` (JUnit XML) 와 `backend/build/reports/tests/test/index.html` 에 남는다.

**프론트엔드**

```sh
pnpm -C frontend install       # 의존성 설치 (처음 한 번, lockfile 이 바뀌었을 때)
pnpm -C frontend dev           # 개발 서버 — /api · /oauth2 · /login 은 localhost:8080 백엔드로 넘긴다
pnpm -C frontend build         # 타입 검사 + 빌드
pnpm -C frontend lint          # ESLint
pnpm -C frontend format        # Prettier 로 정렬 (검사만: format:check)
pnpm -C frontend test          # Vitest
```

UI 컴포넌트는 shadcn 으로 추가한다 — `pnpm -C frontend dlx shadcn@latest add <컴포넌트>` (`src/shared/ui` 에 생긴다).

테스트로 무엇을 증명해야 하는지는 [테스트 전략](docs/qa/test-strategy.md)을 따른다.

## 2. 이슈

작업은 GitHub Issues 로 관리하고, 스프린트와 백로그는 Projects 보드에서 본다.

- **제목**은 한국어. 모듈 작업이면 앞에 모듈 이름 — `[digest] 후보 선별 쿼리 구현`
- **본문**에 목적, 수용 기준(AC), 관련 문서(`docs/` · ADR 링크)를 적는다
- **라벨은 하나** — 작업 유형([§4 유형](#4-커밋-메시지)에서 `revert` 를 뺀 값). 작업 브랜치 접두사와 같다
- 큰 묶음은 상위 이슈 + 하위 이슈(sub-issue), 로드맵 단계는 Milestone
- 작업을 시작하기 전에 이슈가 있어야 한다. 버그는 원인과 범위를 파악한 뒤에 정확히 만든다

## 3. 브랜치

```
main ── 안정 릴리스 (dev → main PR 로만)
 └── dev ── 통합 브랜치 (작업 브랜치의 PR merge 대상)
      ├── feat/12-frontend-init
      └── fix/15-feed-date-parse
```

- 작업 브랜치는 `dev` 에서 분기하고, PR 로 `dev` 에 merge 한다
- `main` · `dev` 에 직접 커밋 · push 하지 않는다
- 작업 브랜치의 이력은 선형으로 유지한다. merge 뒤에도 브랜치는 지우지 않는다
- **이름**: `{접두사}/{이슈 번호}-{설명}` — 접두사는 이슈 라벨(작업 유형)과 같고, 설명은 영문 소문자 kebab-case

## 4. 커밋 메시지

```
{유형}({영역}): {요지}

- 변경 항목 1
- 변경 항목 2

Issue: #12
```

- **유형**: [Conventional Commits](https://www.conventionalcommits.org/) 의 type 을 따른다

  | 유형 | 대상 |
  |---|---|
  | `feat` | 새 기능 |
  | `fix` | 버그 수정 |
  | `refactor` | 동작 변경 없는 구조 개선 |
  | `perf` | 성능 개선 |
  | `test` | 테스트 추가 · 개선 |
  | `docs` | 문서만 |
  | `build` | 빌드 시스템 · 의존성 (Gradle · npm · Dockerfile) |
  | `ci` | CI 설정 |
  | `chore` | 그 밖의 잡무 (설정 · 저장소 관리) |
  | `revert` | 이전 커밋 되돌리기 — 커밋에만 쓰고 이슈 라벨 · 브랜치 접두사로는 쓰지 않는다 |

- **영역**: 아래 표의 낱말 하나 — 주된 영역. 셋 이상을 고르게 걸치면 `({영역})` 을 생략한다
- **요지**: 한 가지 변경의 요지, 한국어 50자 이내, 명사형 종결(`~ 추가` · `~ 정정` · `~ 통합`)
- **본문**: 제목만으로 무엇이 바뀌었는지 다 드러나지 않으면 변경 항목 bullet 을 쓴다. 여러 변경을 담은 커밋은 항상 쓴다
- **trailer**: 마지막 문단에 `Issue: #{이슈 번호}`
- 메시지에는 저장소에서 바뀐 것만 적는다 — 작업 절차의 용어나 개인 작업 기록의 번호를 넣지 않는다
- ADR 등재 커밋: `docs(adr): ADR-NNNN {결정 요지}`

| 영역 | 대상 |
|---|---|
| `source` · `article` · `evaluation` · `digest` · `llm` · `credential` · `user` · `feedback` · `admin` · `platform` | 백엔드 모듈 ([overview.md §5](docs/overview.md#5-모듈-지도)) |
| `web` | 프론트엔드 |
| `adr` · `docs` · `qa` | ADR · 설계 문서 · 테스트 전략 |
| `deploy` | Docker · Compose · 배포 설정 |

## 5. PR

- **대상**은 `dev`. 하나의 이슈에 PR 하나
- **제목**은 커밋 메시지 제목과 같은 형식: `{유형}({영역}): {요지}`
- **본문**은 [PR 템플릿](.github/pull_request_template.md)을 따른다 — 변경 요약, `Closes #{이슈 번호}`, 확인 사항. merge 커밋 메시지가 PR 제목과 본문으로 만들어지므로 저장소의 변경을 정리해 쓴다
- **merge** 는 리드가 한다. 방식은 **merge commit** 만 쓴다(squash · rebase merge 를 쓰지 않는다 — 작업 브랜치의 커밋을 그대로 남긴다)
- **리뷰**: 리드가 GitHub 에서 검토하고, 필요하면 로컬로 받아 확인한다(`gh pr checkout {번호}`)
- **충돌**: 작업 브랜치에 `dev` 를 merge 해서 해소하고 다시 push 한다. force push 하지 않는다
- **릴리스**: 안정 시점에 리드가 `dev` → `main` PR 을 만들어 merge 한다

## 6. 문서

- 변경이 어떤 문서 갱신을 요구하는지는 [docs/README.md §3 갱신 트리거](docs/README.md#3-갱신-트리거)가 정한다. 해당하는 문서 갱신은 그 PR 에 포함한다
- 작성 규칙(현재 상태만 쓰기 · 절 번호 유지 · ADR 수정 범위 · 상대 링크)은 [docs/README.md §4](docs/README.md#4-작성-규칙)
- ADR 은 [docs/adr/README.md](docs/adr/README.md)의 양식으로 쓰고, 번호는 모든 브랜치에서 가장 큰 번호 + 1 로 정한 뒤 바로 커밋한다

## 7. 코드

- 들여쓰기 · 줄바꿈 · 문자 인코딩은 [.editorconfig](.editorconfig)를 따른다
- 모듈 구조와 패턴은 [ADR-0013](docs/adr/0013-architecture-patterns.md), 데이터 접근은 [ADR-0014](docs/adr/0014-spring-data-jdbc.md)를 따른다
- 모듈의 공개 API(모듈 최상위 패키지의 타입)는 Javadoc 을 쓴다. 주석과 Javadoc 은 현재 동작만 설명하고, 이슈 번호에 기대지 않고 그 자리에서 이해되게 쓴다
- 변경 안에서 발견한 작은 정리(낡은 주석 · dead code · 중복)는 같은 PR 에서 끝낸다
