# ADR-0100: 저장소 도구의 단위 테스트는 검사 서비스 `tools` 하나가 돌리고, 문서 검사는 링크와 앵커만 본다

- 상태: 승인. 부분 대체 — CI 체크 목록([ADR-0104](0104-arm64-run-checked-on-arm64-runners-with-a-symmetric-deploy-check-per-architecture.md))
- 날짜: 2026-11-08
- 근거: [이슈 #100](https://github.com/Theia0382/yozm-news/issues/100) — 사건 평가 도구를 더하며 그 도구의 검사 서비스를 따로 두었다가, 검사 이름을 저장소 디렉터리 단위로 맞춤
- 관련: [ADR-0035](0035-public-markdown-links-and-anchors-checked-by-separate-check-service.md)(검사 서비스 목록과 "컨테이너는 단위 테스트와 저장소 검사를 모두 돌린다" — 부분 대체), [ADR-0016](0016-containerized-checks.md)(컨테이너 검사 · 허용 목록 — 그대로 따른다), [ADR-0095](0095-event-clustering-evaluated-by-an-operator-tool-comparing-admin-api-results-with-full-day-labels.md)(사건 평가 도구)

## 맥락

- 검사 서비스와 CI 체크의 이름은 검사하는 저장소 디렉터리를 따른다 — `backend` · `frontend` · `docs` · `deploy`.
- `tools/` 에는 도구가 둘 있다 — 문서 링크 검사기 `tools/docs-check`, 사건 평가 도구 `tools/event-eval`. 둘 다 표준 라이브러리 Python 이고 단위 테스트를 갖는다.
- 문서 검사 서비스 `docs` 는 검사기의 단위 테스트와 저장소의 링크 검사를 함께 돌렸고([ADR-0035](0035-public-markdown-links-and-anchors-checked-by-separate-check-service.md)), 사건 평가 도구는 자기 이름의 검사 서비스와 CI 체크를 따로 두었다. 이대로면 도구가 늘 때마다 검사 서비스 · 이미지 · CI 체크 · 필수 체크가 하나씩 는다.

## 결정

- **검사 서비스 `tools` 가 `tools/` 아래 도구마다 단위 테스트를 돌린다.** 도구 디렉터리마다 테스트를 찾아 돌리고, 하나라도 실패하면 실패한다. 새 도구는 `tools/` 에 디렉터리를 더하면 이 검사에 든다. 이미지는 `tools/Dockerfile` 하나이고, 허용 목록에는 `tools/` 와 도구의 테스트가 읽는 저장소 파일을 넣는다.
- **문서 검사 서비스 `docs` 는 공개 마크다운의 상대 링크와 앵커만 본다.** 검사기의 단위 테스트는 `tools` 가 돌린다.
> 부분 대체(둘째 절 "CI 체크는 …") — [ADR-0104](0104-arm64-run-checked-on-arm64-runners-with-a-symmetric-deploy-check-per-architecture.md). 지금 상태: [개발 인프라 "CI"](../development/infrastructure.md#ci)
- 검사 서비스는 `backend` · `frontend` · `docs` · `tools` · `docker`(DinD)이고, CI 체크는 `backend` · `frontend` · `docs` · `tools` · `deploy` 다.

## 결과

- 도구를 더해도 검사 서비스 · CI 체크 · 필수 체크가 늘지 않는다. 필수 체크 목록을 `tools` 로 바꾸는 일은 저장소 설정이다.
- 검사 이미지 하나가 모든 도구의 테스트 입력(도구 디렉터리와 테스트가 읽는 저장소 파일의 합)을 갖는다. 어느 도구가 바뀌어도 이미지의 복사 계층이 다시 만들어진다.
- 도구의 테스트가 저장소 파일을 새로 읽으면 `tools/Dockerfile.dockerignore` 에 그 파일을 더한다.
- 표준 라이브러리 Python 이 아닌 도구가 생기면 이 이미지에 설치 단계가 필요해진다 — 그 도구를 더하는 때 정한다.
- 테스트가 없는 도구 디렉터리는 테스트를 찾지 못해 검사가 실패한다.

## 검토한 대안

| 대안 | 기각 이유 |
|---|---|
| 도구마다 검사 서비스와 CI 체크를 둔다 | 도구가 늘 때마다 서비스 · 이미지 · CI 체크 · 필수 체크가 늘고, 체크 이름이 디렉터리가 아니라 도구를 따라 다른 체크와 어긋난다 |
| 사건 평가 도구의 검사 이름만 `tools` 로 바꾼다 | 이름이 실제 범위(도구 하나)보다 넓고, 문서 검사기의 테스트는 `docs` 에 남아 도구 테스트가 두 검사에 흩어진다 |
